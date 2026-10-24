import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { authenticate, user, type Deps } from '../context.js';
import { AppError, badRequest, conflict, tooMany, unauthorized } from '../errors.js';
import { allow } from '../security/rateLimit.js';
import { hashPassword, verifyPassword } from '../security/password.js';
import { hashToken, newRefreshToken, signAccessToken } from '../security/tokens.js';
import type { Queryable } from '../db.js';
import { activeEntitlement } from '../context.js';
import { generateRecoveryPhrase, hashRecoveryPhrase, isWellFormedPhrase, verifyRecoveryPhrase } from '../security/recovery.js';

const deviceSchema = z.object({
  installationId: z.string().min(8).max(128),
  platform: z.enum(['android', 'android_tv']),
  model: z.string().max(128).optional(),
  appVersion: z.string().max(32).optional(),
});

const credentials = z.object({
  email: z.string().email().max(254),
  password: z.string().min(8).max(200),
  device: deviceSchema,
});

const registerBody = credentials.extend({ profileName: z.string().trim().min(1).max(30).optional() });

const DUMMY_HASH = 'scrypt$16384$8$1$AAAAAAAAAAAAAAAAAAAAAA==$AAAA';

const refreshBody = z.object({ refreshToken: z.string().min(10).max(200) });

export async function authRoutes(app: FastifyInstance, deps: Deps) {
  const { db, config, redis } = deps;

  async function issueSession(tx: Queryable | typeof db, userId: string, deviceId: string) {
    const refreshToken = newRefreshToken();
    await tx.query(
      `insert into refresh_tokens (user_id, device_id, token_hash, expires_at)
       values ($1, $2, $3, now() + make_interval(days => $4))`,
      [userId, deviceId, hashToken(refreshToken), config.REFRESH_TOKEN_TTL_DAYS],
    );
    const accessToken = await signAccessToken(config, { sub: userId, kind: 'user', did: deviceId });
    return { accessToken, refreshToken, expiresIn: config.ACCESS_TOKEN_TTL_SEC, deviceId };
  }

  /** Registers (or re-uses) the device and enforces the account device limit. */
  async function registerDevice(tx: Queryable, userId: string, d: z.infer<typeof deviceSchema>): Promise<string> {
    const existing = await tx.query('select id, revoked_at from devices where user_id = $1 and installation_id = $2', [userId, d.installationId]);
    const row = existing.rows[0];
    if (row && !row.revoked_at) {
      await tx.query('update devices set last_seen_at = now(), app_version = $2, model = $3 where id = $1', [row.id, d.appVersion ?? null, d.model ?? null]);
      return row.id;
    }
    const ent = await activeEntitlement(deps, userId);
    const limit = ent?.device_limit ?? config.FREE_DEVICE_LIMIT;
    const active = await tx.query('select id, platform, model, last_seen_at from devices where user_id = $1 and revoked_at is null', [userId]);
    if (active.rows.length >= limit) {
      throw conflict('DEVICE_LIMIT_REACHED', 'Device limit reached. Remove a device to continue.', {
        limit,
        devices: active.rows.map((r) => ({ id: r.id, platform: r.platform, model: r.model, lastSeenAt: r.last_seen_at })),
      });
    }
    if (row) {
      await tx.query('update devices set revoked_at = null, last_seen_at = now(), platform = $2, model = $3, app_version = $4 where id = $1', [row.id, d.platform, d.model ?? null, d.appVersion ?? null]);
      return row.id;
    }
    const inserted = await tx.query(
      'insert into devices (user_id, installation_id, platform, model, app_version) values ($1, $2, $3, $4, $5) returning id',
      [userId, d.installationId, d.platform, d.model ?? null, d.appVersion ?? null],
    );
    return inserted.rows[0].id;
  }

  app.post('/auth/register', async (req, reply) => {
    if (!(await allow(redis, `register:${req.ip}`, 10, 60))) throw tooMany();
    const body = registerBody.parse(req.body);
    const passwordHash = await hashPassword(body.password);
    const result = await db.tx(async (tx) => {
      const created = await tx.query(
        'insert into users (email, password_hash) values ($1, $2) on conflict do nothing returning id',
        [body.email, passwordHash],
      );
      const userId = created.rows[0]?.id as string | undefined;
      if (!userId) throw conflict('EMAIL_TAKEN', 'An account with this email already exists');
      await tx.query('insert into profiles (user_id, name, maturity_level) values ($1, $2, 18)', [userId, body.profileName ?? 'Principal']);
      const deviceId = await registerDevice(tx, userId, body.device);
      return { userId, session: await issueSession(tx, userId, deviceId) };
    });
    return reply.status(201).send({ userId: result.userId, ...result.session });
  });

  app.post('/auth/login', async (req) => {
    const body = credentials.parse(req.body);
    const email = body.email.toLowerCase();
    if (!(await allow(redis, `login:ip:${req.ip}`, 5, 60)) || !(await allow(redis, `login:email:${email}`, 10, 60))) throw tooMany();
    const { rows } = await db.query('select id, password_hash from users where lower(email) = $1 and deleted_at is null', [email]);
    const row = rows[0];
    // Always run a hash comparison so unknown emails cost the same as wrong passwords.
    const ok = await verifyPassword(body.password, row?.password_hash ?? DUMMY_HASH);
    if (!row || !ok) throw unauthorized('INVALID_CREDENTIALS', 'Invalid email or password');
    return db.tx(async (tx) => {
      const deviceId = await registerDevice(tx, row.id, body.device);
      return { userId: row.id as string, ...(await issueSession(tx, row.id, deviceId)) };
    });
  });

  app.post('/auth/refresh', async (req) => {
    const { refreshToken } = refreshBody.parse(req.body);
    const outcome = await db.tx(async (tx) => {
      const { rows } = await tx.query(
        'select id, user_id, device_id, revoked_at, expires_at from refresh_tokens where token_hash = $1 for update',
        [hashToken(refreshToken)],
      );
      const row = rows[0];
      if (!row) return { error: unauthorized('INVALID_REFRESH_TOKEN', 'Invalid refresh token') };
      if (row.revoked_at) {
        // A rotated token was presented again: assume theft and kill the device's sessions.
        // Returned (not thrown) so this revocation is committed.
        await tx.query('update refresh_tokens set revoked_at = now() where device_id = $1 and revoked_at is null', [row.device_id]);
        return { error: unauthorized('INVALID_REFRESH_TOKEN', 'Invalid refresh token') };
      }
      if (new Date(row.expires_at) < new Date()) return { error: unauthorized('INVALID_REFRESH_TOKEN', 'Refresh token expired') };
      const device = await tx.query('select 1 from devices where id = $1 and revoked_at is null', [row.device_id]);
      if (device.rows.length === 0) return { error: unauthorized('DEVICE_REVOKED', 'This device is no longer authorized') };
      await tx.query('update refresh_tokens set revoked_at = now() where id = $1', [row.id]);
      return { session: await issueSession(tx, row.user_id, row.device_id) };
    });
    if ('error' in outcome) throw outcome.error;
    return outcome.session;
  });

  app.post('/auth/google', async (req, reply) => {
    if (!deps.googleVerifier) throw new AppError(501, 'GOOGLE_NOT_CONFIGURED', 'Google sign-in is not available');
    if (!(await allow(redis, `google:${req.ip}`, 20, 60))) throw tooMany();
    const body = z.object({ idToken: z.string().min(20).max(4096), device: deviceSchema }).parse(req.body);
    const identity = await deps.googleVerifier(body.idToken);
    if (!identity || !identity.emailVerified) throw unauthorized('INVALID_GOOGLE_TOKEN', 'Google sign-in failed');

    const result = await db.tx(async (tx) => {
      let userId: string | undefined;
      let created = false;
      const bySub = await tx.query('select id from users where google_sub = $1 and deleted_at is null', [identity.sub]);
      userId = bySub.rows[0]?.id;
      if (!userId) {
        // Link to an existing account only because Google verified this email.
        const byEmail = await tx.query('select id from users where lower(email) = lower($1) and deleted_at is null', [identity.email]);
        userId = byEmail.rows[0]?.id;
        if (userId) {
          await tx.query('update users set google_sub = $2 where id = $1 and google_sub is null', [userId, identity.sub]);
        } else {
          const inserted = await tx.query('insert into users (email, google_sub) values ($1, $2) returning id', [identity.email, identity.sub]);
          userId = inserted.rows[0].id as string;
          await tx.query("insert into profiles (user_id, name, maturity_level) values ($1, 'Principal', 18)", [userId]);
          created = true;
        }
      }
      const deviceId = await registerDevice(tx, userId as string, body.device);
      return { userId: userId as string, created, session: await issueSession(tx, userId as string, deviceId) };
    });
    return reply.status(result.created ? 201 : 200).send({ userId: result.userId, ...result.session });
  });

  /** Generates a new phrase, replacing any previous one. The phrase is returned once and only a hash is kept. */
  app.post('/auth/recovery/setup', { preHandler: authenticate(deps) }, async (req) => {
    const { id } = user(req);
    const phrase = generateRecoveryPhrase();
    await db.query('update users set recovery_hash = $2, recovery_set_at = now() where id = $1', [id, await hashRecoveryPhrase(phrase.join(' '))]);
    return { words: phrase };
  });

  app.post('/auth/recover', async (req) => {
    const body = z.object({
      email: z.string().email().max(254),
      phrase: z.string().min(1).max(400),
      newPassword: z.string().min(8).max(200),
      device: deviceSchema,
    }).parse(req.body);
    const email = body.email.toLowerCase();
    if (!(await allow(redis, `recover:ip:${req.ip}`, 5, 60)) || !(await allow(redis, `recover:email:${email}`, 10, 3600))) throw tooMany();

    const { rows } = await db.query('select id, recovery_hash from users where lower(email) = $1 and deleted_at is null', [email]);
    const row = rows[0];
    const wellFormed = isWellFormedPhrase(body.phrase);
    const ok = await verifyRecoveryPhrase(wellFormed ? body.phrase : 'x', row?.recovery_hash ?? DUMMY_HASH);
    if (!row || !row.recovery_hash || !wellFormed || !ok) throw unauthorized('INVALID_RECOVERY', 'Email or recovery phrase is incorrect');

    const passwordHash = await hashPassword(body.newPassword);
    return db.tx(async (tx) => {
      // The phrase is single use: the user must create a new one after recovering.
      await tx.query('update users set password_hash = $2, recovery_hash = null, recovery_set_at = null where id = $1', [row.id, passwordHash]);
      await tx.query('update refresh_tokens set revoked_at = now() where user_id = $1 and revoked_at is null', [row.id]);
      const deviceId = await registerDevice(tx, row.id, body.device);
      return { userId: row.id as string, recoveryReset: true, ...(await issueSession(tx, row.id, deviceId)) };
    });
  });

  app.post('/auth/logout', async (req, reply) => {
    const { refreshToken } = refreshBody.parse(req.body);
    await db.query('update refresh_tokens set revoked_at = now() where token_hash = $1 and revoked_at is null', [hashToken(refreshToken)]);
    return reply.status(204).send();
  });

  app.get('/me', { preHandler: authenticate(deps) }, async (req) => {
    const { id, deviceId } = user(req);
    const { rows } = await db.query('select id, email, created_at, recovery_hash is not null as has_recovery from users where id = $1', [id]);
    const ent = await activeEntitlement(deps, id);
    return {
      id,
      email: rows[0].email,
      createdAt: rows[0].created_at,
      deviceId,
      hasRecovery: rows[0].has_recovery,
      vip: ent ? { active: true, planId: ent.plan_id, endsAt: ent.ends_at, deviceLimit: ent.device_limit } : { active: false },
    };
  });

  app.patch('/me', { preHandler: authenticate(deps) }, async (req) => {
    const body = z.object({ password: z.string().min(8).max(200), currentPassword: z.string().max(200) }).parse(req.body);
    const { id } = user(req);
    const { rows } = await db.query('select password_hash from users where id = $1', [id]);
    if (!rows[0].password_hash || !(await verifyPassword(body.currentPassword, rows[0].password_hash))) throw badRequest('INVALID_CREDENTIALS', 'Current password is incorrect');
    await db.query('update users set password_hash = $2 where id = $1', [id, await hashPassword(body.password)]);
    await db.query('update refresh_tokens set revoked_at = now() where user_id = $1 and device_id <> $2 and revoked_at is null', [id, req.user!.deviceId]);
    return { ok: true };
  });
}
