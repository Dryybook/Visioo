import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { authenticate, user, type Deps } from '../context.js';
import { notFound } from '../errors.js';

export async function deviceRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;
  const pre = { preHandler: authenticate(deps) };

  app.get('/devices', pre, async (req) => {
    const { id, deviceId } = user(req);
    const { rows } = await db.query(
      'select id, platform, model, app_version, last_seen_at, created_at from devices where user_id = $1 and revoked_at is null order by created_at',
      [id],
    );
    return {
      devices: rows.map((r) => ({
        id: r.id, platform: r.platform, model: r.model, appVersion: r.app_version,
        lastSeenAt: r.last_seen_at, createdAt: r.created_at, current: r.id === deviceId,
      })),
    };
  });

  app.delete('/devices/:id', pre, async (req, reply) => {
    const { id: deviceId } = z.object({ id: z.string().uuid() }).parse(req.params);
    const { id } = user(req);
    const res = await db.query('update devices set revoked_at = now() where id = $1 and user_id = $2 and revoked_at is null', [deviceId, id]);
    if (res.rowCount === 0) throw notFound('DEVICE_NOT_FOUND');
    await db.query('update refresh_tokens set revoked_at = now() where device_id = $1 and revoked_at is null', [deviceId]);
    return reply.status(204).send();
  });
}
