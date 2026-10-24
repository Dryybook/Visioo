import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { activeEntitlement, authenticate, profileContext, user, type Deps } from '../context.js';
import { forbidden, notFound, tooMany } from '../errors.js';
import { allow } from '../security/rateLimit.js';
import { signPlaybackUrl } from '../security/signedUrl.js';
import { rankSources, type HealthStatus } from '../security/sourceRanking.js';

const body = z.object({
  contentType: z.enum(['movie', 'episode']),
  contentId: z.string().uuid(),
  language: z.string().max(8).optional(),
  preferredQuality: z.number().int().min(144).max(4320).optional(),
});

export async function playbackRoutes(app: FastifyInstance, deps: Deps) {
  const { db, redis, config } = deps;

  app.post('/playback/resolve', { preHandler: authenticate(deps) }, async (req) => {
    const { id: userId } = user(req);
    if (!(await allow(redis, `resolve:${userId}`, 60, 60))) throw tooMany();
    const b = body.parse(req.body);
    const profile = await profileContext(deps, req);

    const content = b.contentType === 'movie'
      ? (await db.query('select is_vip, age_rating from movies where id = $1 and published', [b.contentId])).rows[0]
      : (await db.query(
          `select sr.is_vip, sr.age_rating from episodes e join seasons s on s.id = e.season_id join series sr on sr.id = s.series_id
           where e.id = $1 and sr.published`,
          [b.contentId],
        )).rows[0];
    if (!content) throw notFound('CONTENT_NOT_FOUND');
    if (content.age_rating > profile.maturityLevel) throw forbidden('PROFILE_RESTRICTED', 'Not available for this profile');

    if (content.is_vip && !(await activeEntitlement(deps, userId))) {
      const past = await db.query('select 1 from vip_entitlements where user_id = $1 limit 1', [userId]);
      throw forbidden(past.rows.length > 0 ? 'VIP_EXPIRED' : 'VIP_REQUIRED', 'VIP access required');
    }

    const { rows } = await db.query(
      `select s.*, coalesce(h.status, 'UNKNOWN') as health, h.latency_ms from sources s
       left join source_health h on h.source_id = s.id
       where s.content_type = $1 and s.content_id = $2 and s.authorized`,
      [b.contentType, b.contentId],
    );
    const ranked = rankSources(
      rows.map((r) => ({ ...r, health: r.health as HealthStatus, latencyMs: r.latency_ms as number | null })),
      { language: b.language, preferredQuality: b.preferredQuality },
    );
    if (ranked.length === 0) throw notFound('NO_SOURCE', 'No playable source is available');

    return {
      sources: ranked.map((s) => {
        const signed = signPlaybackUrl(config.PLAYBACK_SIGNING_SECRET, s.url, config.PLAYBACK_URL_TTL_SEC);
        return {
          id: s.id,
          provider: s.provider,
          type: s.type,
          quality: s.quality,
          language: s.language,
          url: signed.url,
          expiresAt: signed.expiresAt,
          audioTracks: s.audio_tracks,
          subtitleTracks: s.subtitle_tracks,
        };
      }),
    };
  });
}
