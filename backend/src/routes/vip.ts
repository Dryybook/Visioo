import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { activeEntitlement, authenticate, user, type Deps } from '../context.js';
import { AppError, badRequest, tooMany } from '../errors.js';
import { allow } from '../security/rateLimit.js';
import { hashVipKey, normalizeVipKey } from '../security/vipKey.js';

const activateBody = z.object({ key: z.string().min(8).max(64) });

export async function vipRoutes(app: FastifyInstance, deps: Deps) {
  const { db, redis, config } = deps;
  const pre = { preHandler: authenticate(deps) };

  const entitlementDto = (e: Awaited<ReturnType<typeof activeEntitlement>>) =>
    e ? { active: true, planId: e.plan_id, startsAt: e.starts_at, endsAt: e.ends_at, deviceLimit: e.device_limit } : { active: false };

  app.get('/vip', pre, async (req) => entitlementDto(await activeEntitlement(deps, user(req).id)));

  app.post('/vip/activate', pre, async (req) => {
    const { id: userId, deviceId } = user(req);
    if (!(await allow(redis, `vip:${userId}:${deviceId}`, 5, 60))) throw tooMany();
    const normalized = normalizeVipKey(activateBody.parse(req.body).key);
    if (!normalized) throw badRequest('INVALID_KEY', 'Invalid VIP key');
    const keyHash = hashVipKey(config.VIP_KEY_PEPPER, normalized);

    await db.tx(async (tx) => {
      const { rows } = await tx.query('select * from vip_keys where key_hash = $1 for update', [keyHash]);
      const key = rows[0];
      if (!key) throw badRequest('INVALID_KEY', 'Invalid VIP key');
      if (key.status === 'revoked') throw new AppError(403, 'KEY_REVOKED', 'This key has been revoked');
      if (key.expires_at && new Date(key.expires_at) < new Date()) throw new AppError(403, 'KEY_EXPIRED', 'This key has expired');

      const already = await tx.query('select 1 from vip_key_activations where key_id = $1 and user_id = $2', [key.id, userId]);
      if (already.rows.length > 0) return; // idempotent for the same account
      if (key.activation_count >= key.max_activations) throw new AppError(409, 'KEY_ALREADY_USED', 'This key has already been used');

      const plan = (await tx.query('select duration_days, device_limit from vip_plans where id = $1', [key.plan_id])).rows[0];
      // Stack on top of a running entitlement instead of overlapping it.
      const latest = (await tx.query('select max(ends_at) as ends_at from vip_entitlements where user_id = $1 and revoked_at is null and ends_at > now()', [userId])).rows[0];
      const startsAt: Date = latest?.ends_at ? new Date(latest.ends_at) : new Date();
      const endsAt = new Date(startsAt.getTime() + plan.duration_days * 86_400_000);

      await tx.query(
        'insert into vip_entitlements (user_id, plan_id, key_id, starts_at, ends_at, device_limit) values ($1, $2, $3, $4, $5, $6)',
        [userId, key.plan_id, key.id, startsAt, endsAt, key.device_limit ?? plan.device_limit],
      );
      await tx.query('insert into vip_key_activations (key_id, user_id, device_id) values ($1, $2, $3)', [key.id, userId, deviceId]);
      const count = key.activation_count + 1;
      await tx.query(
        `update vip_keys set activation_count = $2, status = $3, activated_at = coalesce(activated_at, now()), activated_by_user_id = coalesce(activated_by_user_id, $4) where id = $1`,
        [key.id, count, count >= key.max_activations ? 'used' : 'active', userId],
      );
    });

    return entitlementDto(await activeEntitlement(deps, userId));
  });
}
