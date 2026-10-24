import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { authenticateAdmin, type Deps } from '../context.js';
import { notFound, tooMany, unauthorized } from '../errors.js';
import { verifyPassword } from '../security/password.js';
import { allow } from '../security/rateLimit.js';
import { signAccessToken } from '../security/tokens.js';
import { displayPrefix, generateVipKey, hashVipKey } from '../security/vipKey.js';

const generateBody = z.object({
  planId: z.string().min(1).max(50),
  quantity: z.number().int().min(1).max(1000),
  deviceLimit: z.number().int().min(1).max(10).optional(),
  maxActivations: z.number().int().min(1).max(100).default(1),
  expiresAt: z.string().datetime().optional(),
  notes: z.string().max(500).optional(),
  format: z.enum(['json', 'csv']).default('json'),
});

const DUMMY_HASH = 'scrypt$16384$8$1$AAAAAAAAAAAAAAAAAAAAAA==$AAAA';

export async function adminRoutes(app: FastifyInstance, deps: Deps) {
  const { db, config, redis } = deps;
  const keyAdmins = authenticateAdmin(deps, 'SUPER_ADMIN', 'ADMIN');
  const support = authenticateAdmin(deps, 'SUPER_ADMIN', 'ADMIN', 'SUPPORT');

  const audit = (adminId: string, action: string, targetType: string, targetId: string | null, details: object = {}) =>
    db.query('insert into audit_logs (admin_id, action, target_type, target_id, details) values ($1, $2, $3, $4, $5)', [adminId, action, targetType, targetId, details]);

  app.post('/admin/auth/login', async (req) => {
    const b = z.object({ email: z.string().email(), password: z.string().min(1).max(200) }).parse(req.body);
    if (!(await allow(redis, `admin-login:${req.ip}`, 5, 60))) throw tooMany();
    const { rows } = await db.query('select id, password_hash, role from admin_users where lower(email) = lower($1)', [b.email]);
    const row = rows[0];
    const ok = await verifyPassword(b.password, row?.password_hash ?? DUMMY_HASH);
    if (!row || !ok) throw unauthorized('INVALID_CREDENTIALS', 'Invalid email or password');
    return { accessToken: await signAccessToken(config, { sub: row.id, kind: 'admin', role: row.role }), role: row.role };
  });

  app.get('/admin/vip/plans', { preHandler: support }, async () => {
    const { rows } = await db.query('select id, name, duration_days, device_limit from vip_plans order by duration_days');
    return { plans: rows.map((r) => ({ id: r.id, name: r.name, durationDays: r.duration_days, deviceLimit: r.device_limit })) };
  });

  app.post('/admin/vip/keys', { preHandler: keyAdmins }, async (req, reply) => {
    const b = generateBody.parse(req.body);
    const adminId = req.admin!.id;
    const plan = await db.query('select id from vip_plans where id = $1', [b.planId]);
    if (plan.rows.length === 0) throw notFound('PLAN_NOT_FOUND');

    const keys: string[] = [];
    await db.tx(async (tx) => {
      while (keys.length < b.quantity) {
        const key = generateVipKey();
        const inserted = await tx.query(
          `insert into vip_keys (key_hash, display_prefix, plan_id, max_activations, device_limit, expires_at, notes)
           values ($1, $2, $3, $4, $5, $6, $7) on conflict (key_hash) do nothing`,
          [hashVipKey(config.VIP_KEY_PEPPER, key), displayPrefix(key), b.planId, b.maxActivations, b.deviceLimit ?? null, b.expiresAt ?? null, b.notes ?? null],
        );
        if (inserted.rowCount === 1) keys.push(key);
      }
    });
    await audit(adminId, 'vip_keys.generate', 'vip_plan', b.planId, { quantity: b.quantity, deviceLimit: b.deviceLimit ?? null });

    // Raw keys exist only in this response; only their hashes are stored.
    if (b.format === 'csv') {
      const created = new Date().toISOString();
      const lines = ['key,plan,device_limit,status,created_at', ...keys.map((k) => `${k},${b.planId},${b.deviceLimit ?? ''},active,${created}`)];
      return reply.status(201).header('content-type', 'text/csv; charset=utf-8').send(lines.join('\n') + '\n');
    }
    return reply.status(201).send({ planId: b.planId, keys });
  });

  app.get('/admin/vip/keys', { preHandler: support }, async (req) => {
    const q = z.object({ status: z.enum(['active', 'used', 'revoked']).optional(), limit: z.coerce.number().int().min(1).max(200).default(50), offset: z.coerce.number().int().min(0).default(0) }).parse(req.query);
    const { rows } = await db.query(
      `select id, display_prefix, plan_id, status, max_activations, activation_count, device_limit, created_at, expires_at, activated_at, activated_by_user_id, notes
       from vip_keys where ($1::text is null or status = $1) order by created_at desc limit $2 offset $3`,
      [q.status ?? null, q.limit, q.offset],
    );
    return {
      keys: rows.map((r) => ({
        id: r.id, prefix: r.display_prefix, planId: r.plan_id, status: r.status, maxActivations: r.max_activations,
        activationCount: r.activation_count, deviceLimit: r.device_limit, createdAt: r.created_at, expiresAt: r.expires_at,
        activatedAt: r.activated_at, activatedByUserId: r.activated_by_user_id, notes: r.notes,
      })),
    };
  });

  app.post('/admin/vip/keys/:id/revoke', { preHandler: keyAdmins }, async (req, reply) => {
    const { id } = z.object({ id: z.string().uuid() }).parse(req.params);
    const res = await db.tx(async (tx) => {
      const r = await tx.query("update vip_keys set status = 'revoked' where id = $1 returning id", [id]);
      if (r.rowCount === 0) return false;
      // Revoking a key also ends the entitlements it granted.
      await tx.query('update vip_entitlements set revoked_at = now() where key_id = $1 and revoked_at is null', [id]);
      return true;
    });
    if (!res) throw notFound('KEY_NOT_FOUND');
    await audit(req.admin!.id, 'vip_keys.revoke', 'vip_key', id);
    return reply.status(204).send();
  });

  app.get('/admin/users', { preHandler: support }, async (req) => {
    const q = z.object({ q: z.string().max(100).optional(), limit: z.coerce.number().int().min(1).max(100).default(50), offset: z.coerce.number().int().min(0).default(0) }).parse(req.query);
    const pattern = q.q ? `%${q.q.replace(/[\\%_]/g, (c) => `\\${c}`)}%` : null;
    const { rows } = await db.query(
      `select u.id, u.email, u.created_at,
              (select count(*)::int from devices d where d.user_id = u.id and d.revoked_at is null) as devices,
              exists (select 1 from vip_entitlements e where e.user_id = u.id and e.revoked_at is null and e.ends_at > now() and e.starts_at <= now()) as vip
       from users u where u.deleted_at is null and ($1::text is null or u.email ilike $1) order by u.created_at desc limit $2 offset $3`,
      [pattern, q.limit, q.offset],
    );
    return { users: rows.map((r) => ({ id: r.id, email: r.email, createdAt: r.created_at, devices: r.devices, vip: r.vip })) };
  });

  app.get('/admin/users/:id/devices', { preHandler: support }, async (req) => {
    const { id } = z.object({ id: z.string().uuid() }).parse(req.params);
    const { rows } = await db.query('select id, platform, model, app_version, last_seen_at, created_at, revoked_at from devices where user_id = $1 order by created_at', [id]);
    return { devices: rows.map((r) => ({ id: r.id, platform: r.platform, model: r.model, appVersion: r.app_version, lastSeenAt: r.last_seen_at, createdAt: r.created_at, revokedAt: r.revoked_at })) };
  });

  app.delete('/admin/devices/:id', { preHandler: support }, async (req, reply) => {
    const { id } = z.object({ id: z.string().uuid() }).parse(req.params);
    const res = await db.query('update devices set revoked_at = now() where id = $1 and revoked_at is null', [id]);
    if (res.rowCount === 0) throw notFound('DEVICE_NOT_FOUND');
    await db.query('update refresh_tokens set revoked_at = now() where device_id = $1 and revoked_at is null', [id]);
    await audit(req.admin!.id, 'device.revoke', 'device', id);
    return reply.status(204).send();
  });
}

