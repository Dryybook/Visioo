import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { authenticate, profileContext, type Deps } from '../context.js';

const progressBody = z.object({
  contentType: z.enum(['movie', 'episode']),
  contentId: z.string().uuid(),
  positionMs: z.number().int().min(0).max(86_400_000),
  durationMs: z.number().int().min(0).max(86_400_000),
});

const positionQuery = z.object({
  contentType: z.enum(['movie', 'episode']),
  contentId: z.string().uuid(),
});

export async function historyRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;
  const pre = { preHandler: authenticate(deps) };

  /** Stores the raw position. Resume and "completed" rules live in the clients. */
  app.post('/history/progress', pre, async (req, reply) => {
    const b = progressBody.parse(req.body);
    const profile = await profileContext(deps, req);
    await db.query(
      `insert into watch_history (user_id, profile_id, content_type, content_id, position_ms, duration_ms)
       values ($1, $2, $3, $4, $5, $6)
       on conflict (profile_id, content_type, content_id)
       do update set position_ms = excluded.position_ms, duration_ms = excluded.duration_ms, updated_at = now()`,
      [req.user!.id, profile.id, b.contentType, b.contentId, b.positionMs, b.durationMs],
    );
    return reply.status(204).send();
  });

  app.get('/history/position', pre, async (req) => {
    const q = positionQuery.parse(req.query);
    const profile = await profileContext(deps, req);
    const { rows } = await db.query(
      'select position_ms, duration_ms from watch_history where profile_id = $1 and content_type = $2 and content_id = $3',
      [profile.id, q.contentType, q.contentId],
    );
    const row = rows[0];
    return { positionMs: row ? Number(row.position_ms) : 0, durationMs: row ? Number(row.duration_ms) : 0 };
  });
}
