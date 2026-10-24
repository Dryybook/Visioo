import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { authenticate, user, type Deps } from '../context.js';
import { badRequest, conflict, notFound } from '../errors.js';

const MAX_PROFILES = 5;
const CHILD_MAX_MATURITY = 12;

const base = z.object({
  name: z.string().trim().min(1).max(30),
  avatar: z.string().max(200).nullable().optional(),
  isChild: z.boolean().default(false),
  language: z.enum(['fr', 'en']).default('fr'),
  subtitleLanguage: z.string().max(8).nullable().optional(),
  maturityLevel: z.number().int().min(0).max(18).default(18),
  autoplay: z.boolean().default(true),
});

const clampMaturity = (isChild: boolean, level: number) => (isChild ? Math.min(level, CHILD_MAX_MATURITY) : level);

const toDto = (r: any) => ({
  id: r.id,
  name: r.name,
  avatar: r.avatar,
  isChild: r.is_child,
  language: r.language,
  subtitleLanguage: r.subtitle_language,
  maturityLevel: r.maturity_level,
  autoplay: r.autoplay,
});

export async function profileRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;
  const pre = { preHandler: authenticate(deps) };

  app.get('/profiles', pre, async (req) => {
    const { rows } = await db.query('select * from profiles where user_id = $1 order by created_at', [user(req).id]);
    return { profiles: rows.map(toDto) };
  });

  app.post('/profiles', pre, async (req, reply) => {
    const b = base.parse(req.body);
    const { id } = user(req);
    const count = await db.query('select count(*)::int as n from profiles where user_id = $1', [id]);
    if (count.rows[0].n >= MAX_PROFILES) throw conflict('PROFILE_LIMIT', `At most ${MAX_PROFILES} profiles per account`);
    const { rows } = await db.query(
      `insert into profiles (user_id, name, avatar, is_child, language, subtitle_language, maturity_level, autoplay)
       values ($1, $2, $3, $4, $5, $6, $7, $8) returning *`,
      [id, b.name, b.avatar ?? null, b.isChild, b.language, b.subtitleLanguage ?? null, clampMaturity(b.isChild, b.maturityLevel), b.autoplay],
    );
    return reply.status(201).send(toDto(rows[0]));
  });

  app.patch('/profiles/:id', pre, async (req) => {
    const { id: profileId } = z.object({ id: z.string().uuid() }).parse(req.params);
    const b = base.partial().parse(req.body);
    const { id } = user(req);
    const current = await db.query('select * from profiles where id = $1 and user_id = $2', [profileId, id]);
    const cur = current.rows[0];
    if (!cur) throw notFound('PROFILE_NOT_FOUND');
    const isChild = b.isChild ?? cur.is_child;
    const maturity = clampMaturity(isChild, b.maturityLevel ?? cur.maturity_level);
    const { rows } = await db.query(
      `update profiles set name = $3, avatar = $4, is_child = $5, language = $6, subtitle_language = $7, maturity_level = $8, autoplay = $9
       where id = $1 and user_id = $2 returning *`,
      [
        profileId, id, b.name ?? cur.name, b.avatar === undefined ? cur.avatar : b.avatar, isChild,
        b.language ?? cur.language, b.subtitleLanguage === undefined ? cur.subtitle_language : b.subtitleLanguage,
        maturity, b.autoplay ?? cur.autoplay,
      ],
    );
    return toDto(rows[0]);
  });

  app.delete('/profiles/:id', pre, async (req, reply) => {
    const { id: profileId } = z.object({ id: z.string().uuid() }).parse(req.params);
    const { id } = user(req);
    const count = await db.query('select count(*)::int as n from profiles where user_id = $1', [id]);
    if (count.rows[0].n <= 1) throw badRequest('LAST_PROFILE', 'An account needs at least one profile');
    const res = await db.query('delete from profiles where id = $1 and user_id = $2', [profileId, id]);
    if (res.rowCount === 0) throw notFound('PROFILE_NOT_FOUND');
    return reply.status(204).send();
  });
}
