import type { FastifyReply, FastifyRequest } from 'fastify';
import type { Redis } from 'ioredis';
import type { Config } from './config.js';
import type { Db } from './db.js';
import { AppError, forbidden, unauthorized } from './errors.js';
import type { GoogleVerifier } from './security/google.js';
import { verifyAccessToken } from './security/tokens.js';

export interface Deps {
  config: Config;
  db: Db;
  redis: Redis;
  /** Absent when Google sign-in is not configured. */
  googleVerifier?: GoogleVerifier;
}

export interface AuthUser {
  id: string;
  deviceId: string;
}

export interface AuthAdmin {
  id: string;
  role: string;
}

declare module 'fastify' {
  interface FastifyRequest {
    user?: AuthUser;
    admin?: AuthAdmin;
  }
}

function bearer(req: FastifyRequest): string | null {
  const h = req.headers.authorization;
  return h?.startsWith('Bearer ') ? h.slice(7) : null;
}

/** Validates the access token AND that its device has not been revoked. */
export function authenticate(deps: Deps) {
  return async (req: FastifyRequest, _reply: FastifyReply) => {
    const token = bearer(req);
    const claims = token ? await verifyAccessToken(deps.config, token, 'user') : null;
    if (!claims?.did) throw unauthorized();
    const { rows } = await deps.db.query(
      'select 1 from devices d join users u on u.id = d.user_id where d.id = $1 and d.user_id = $2 and d.revoked_at is null and u.deleted_at is null',
      [claims.did, claims.sub],
    );
    if (rows.length === 0) throw unauthorized('DEVICE_REVOKED', 'This device is no longer authorized');
    deps.db.query('update devices set last_seen_at = now() where id = $1', [claims.did]).catch(() => undefined);
    req.user = { id: claims.sub, deviceId: claims.did };
  };
}

export function authenticateAdmin(deps: Deps, ...roles: string[]) {
  return async (req: FastifyRequest, _reply: FastifyReply) => {
    const token = bearer(req);
    const claims = token ? await verifyAccessToken(deps.config, token, 'admin') : null;
    if (!claims?.role) throw unauthorized();
    if (roles.length > 0 && !roles.includes(claims.role)) throw forbidden('FORBIDDEN', 'Insufficient role');
    req.admin = { id: claims.sub, role: claims.role };
  };
}

export function user(req: FastifyRequest): AuthUser {
  if (!req.user) throw new AppError(500, 'INTERNAL', 'Route is missing authentication');
  return req.user;
}

export interface ProfileContext {
  id: string;
  maturityLevel: number;
}

/** Profile selected by `X-Profile-Id`, or the account's oldest profile. Ownership is enforced here. */
export async function profileContext(deps: Deps, req: FastifyRequest): Promise<ProfileContext> {
  const { id } = user(req);
  const header = req.headers['x-profile-id'];
  const wanted = typeof header === 'string' ? header : null;
  const { rows } = wanted
    ? await deps.db.query('select id, maturity_level from profiles where id = $1 and user_id = $2', [wanted, id]).catch(() => ({ rows: [] }))
    : await deps.db.query('select id, maturity_level from profiles where user_id = $1 order by created_at limit 1', [id]);
  const row = rows[0];
  if (!row) throw forbidden('PROFILE_NOT_FOUND', 'Unknown profile');
  return { id: row.id, maturityLevel: row.maturity_level };
}

/**
 * The entitlement running now. `ends_at` is the end of the whole stack, because keys activated
 * while VIP is running start when the previous one ends.
 */
export async function activeEntitlement(deps: Deps, userId: string) {
  const { rows } = await deps.db.query(
    `select e.id, e.plan_id, e.starts_at, e.device_limit,
            (select max(ends_at) from vip_entitlements where user_id = $1 and revoked_at is null) as ends_at
     from vip_entitlements e
     where e.user_id = $1 and e.revoked_at is null and e.starts_at <= now() and e.ends_at > now()
     order by e.ends_at desc limit 1`,
    [userId],
  );
  return rows[0] as { id: string; plan_id: string; starts_at: Date; ends_at: Date; device_limit: number } | undefined;
}
