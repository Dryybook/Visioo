import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { activeEntitlement, authenticate, profileContext, user, type Deps } from '../context.js';
import { notFound } from '../errors.js';

const uuid = z.object({ id: z.string().uuid() });
const paging = z.object({
  limit: z.coerce.number().int().min(1).max(50).default(20),
  offset: z.coerce.number().int().min(0).default(0),
  genre: z.string().max(50).optional(),
});

const summary = (type: 'movie' | 'series', r: any) => ({
  id: r.id,
  title: r.title,
  type,
  posterUrl: r.poster_url,
  backdropUrl: r.backdrop_url,
  year: r.year,
  isVip: r.is_vip,
  overview: r.overview ?? null,
});

export async function catalogRoutes(app: FastifyInstance, deps: Deps) {
  const { db } = deps;
  const pre = { preHandler: authenticate(deps) };

  app.get('/home', pre, async (req) => {
    const profile = await profileContext(deps, req);
    const { rows } = await db.query(
      `select s.id as section_id, s.title as section_title, s.layout, i.content_type, i.content_id,
              coalesce(m.title, sr.title) as title, coalesce(m.poster_url, sr.poster_url) as poster_url,
              coalesce(m.backdrop_url, sr.backdrop_url) as backdrop_url, coalesce(m.year, sr.year) as year,
              coalesce(m.is_vip, sr.is_vip) as is_vip, coalesce(m.age_rating, sr.age_rating) as age_rating, coalesce(m.overview, sr.overview) as overview
       from home_sections s
       join home_section_items i on i.section_id = s.id
       left join movies m on i.content_type = 'movie' and m.id = i.content_id and m.published
       left join series sr on i.content_type = 'series' and sr.id = i.content_id and sr.published
       where s.active
       order by s.position, i.position`,
    );
    const sections = new Map<string, { id: string; title: string; layout: string; items: unknown[] }>();
    for (const r of rows) {
      if (r.title == null || r.age_rating > profile.maturityLevel) continue;
      let section = sections.get(r.section_id);
      if (!section) {
        section = { id: r.section_id, title: r.section_title, layout: r.layout, items: [] };
        sections.set(r.section_id, section);
      }
      section.items.push(summary(r.content_type, { ...r, id: r.content_id }));
    }
    // The section with id `hero` feeds the home banner and is not listed as a row.
    const hero = sections.get('hero');
    sections.delete('hero');
    return { hero: hero?.items ?? [], sections: [...sections.values()] };
  });

  app.get('/genres', pre, async () => {
    const { rows } = await db.query('select slug, name from genres order by name');
    return { genres: rows };
  });

  for (const [path, table, type] of [['/movies', 'movies', 'movie'], ['/series', 'series', 'series']] as const) {
    app.get(path, pre, async (req) => {
      const q = paging.parse(req.query);
      const profile = await profileContext(deps, req);
      const genreTable = type === 'movie' ? 'movie_genres' : 'series_genres';
      const genreCol = type === 'movie' ? 'movie_id' : 'series_id';
      const { rows } = await db.query(
        `select t.* from ${table} t
         where t.published and t.age_rating <= $1
           and ($2::text is null or exists (select 1 from ${genreTable} g join genres gn on gn.id = g.genre_id where g.${genreCol} = t.id and gn.slug = $2))
         order by t.created_at desc, t.id limit $3 offset $4`,
        [profile.maturityLevel, q.genre ?? null, q.limit, q.offset],
      );
      return { items: rows.map((r) => summary(type, r)), limit: q.limit, offset: q.offset };
    });
  }

  app.get('/movies/:id', pre, async (req) => {
    const { id } = uuid.parse(req.params);
    const profile = await profileContext(deps, req);
    const { rows } = await db.query('select * from movies where id = $1 and published and age_rating <= $2', [id, profile.maturityLevel]);
    const m = rows[0];
    if (!m) throw notFound('MOVIE_NOT_FOUND');
    const genres = await db.query('select gn.slug, gn.name from movie_genres g join genres gn on gn.id = g.genre_id where g.movie_id = $1 order by gn.name', [id]);
    const ent = await activeEntitlement(deps, user(req).id);
    return {
      ...summary('movie', m),
      originalTitle: m.original_title,
      overview: m.overview,
      logoUrl: m.logo_url,
      releaseDate: m.release_date,
      runtimeMin: m.runtime_min,
      rating: m.rating == null ? null : Number(m.rating),
      ageRating: m.age_rating,
      genres: genres.rows,
      canPlay: !m.is_vip || !!ent,
    };
  });

  app.get('/series/:id', pre, async (req) => {
    const { id } = uuid.parse(req.params);
    const profile = await profileContext(deps, req);
    const { rows } = await db.query('select * from series where id = $1 and published and age_rating <= $2', [id, profile.maturityLevel]);
    const s = rows[0];
    if (!s) throw notFound('SERIES_NOT_FOUND');
    const genres = await db.query('select gn.slug, gn.name from series_genres g join genres gn on gn.id = g.genre_id where g.series_id = $1 order by gn.name', [id]);
    const seasons = await db.query(
      'select s.id, s.season_number, s.title, s.poster_url, (select count(*)::int from episodes e where e.season_id = s.id) as episode_count from seasons s where s.series_id = $1 order by s.season_number',
      [id],
    );
    const ent = await activeEntitlement(deps, user(req).id);
    return {
      ...summary('series', s),
      originalTitle: s.original_title,
      overview: s.overview,
      rating: s.rating == null ? null : Number(s.rating),
      ageRating: s.age_rating,
      genres: genres.rows,
      seasons: seasons.rows.map((x) => ({ id: x.id, seasonNumber: x.season_number, title: x.title, posterUrl: x.poster_url, episodeCount: x.episode_count })),
      canPlay: !s.is_vip || !!ent,
    };
  });

  app.get('/series/:id/seasons', pre, async (req) => {
    const { id } = uuid.parse(req.params);
    const profile = await profileContext(deps, req);
    const series = await db.query('select 1 from series where id = $1 and published and age_rating <= $2', [id, profile.maturityLevel]);
    if (series.rows.length === 0) throw notFound('SERIES_NOT_FOUND');
    const { rows } = await db.query(
      `select s.id as season_id, s.season_number, s.title as season_title, e.id, e.episode_number, e.title, e.overview, e.thumbnail_url, e.runtime_min, e.air_date
       from seasons s left join episodes e on e.season_id = s.id where s.series_id = $1 order by s.season_number, e.episode_number`,
      [id],
    );
    const seasons = new Map<string, any>();
    for (const r of rows) {
      const season = seasons.get(r.season_id) ?? { id: r.season_id, seasonNumber: r.season_number, title: r.season_title, episodes: [] };
      seasons.set(r.season_id, season);
      if (r.id) {
        season.episodes.push({ id: r.id, episodeNumber: r.episode_number, title: r.title, overview: r.overview, thumbnailUrl: r.thumbnail_url, runtimeMin: r.runtime_min, airDate: r.air_date });
      }
    }
    return { seasons: [...seasons.values()] };
  });

  app.get('/episodes/:id', pre, async (req) => {
    const { id } = uuid.parse(req.params);
    const profile = await profileContext(deps, req);
    const { rows } = await db.query(
      `select e.*, s.season_number, s.series_id, sr.title as series_title from episodes e
       join seasons s on s.id = e.season_id join series sr on sr.id = s.series_id
       where e.id = $1 and sr.published and sr.age_rating <= $2`,
      [id, profile.maturityLevel],
    );
    const e = rows[0];
    if (!e) throw notFound('EPISODE_NOT_FOUND');
    return {
      id: e.id, seriesId: e.series_id, seriesTitle: e.series_title, seasonNumber: e.season_number,
      episodeNumber: e.episode_number, title: e.title, overview: e.overview, thumbnailUrl: e.thumbnail_url,
      runtimeMin: e.runtime_min, airDate: e.air_date,
    };
  });

  app.get('/search', pre, async (req) => {
    const q = z.object({ q: z.string().trim().min(2).max(80), limit: z.coerce.number().int().min(1).max(30).default(20) }).parse(req.query);
    const profile = await profileContext(deps, req);
    const pattern = `%${q.q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
    const [movies, series] = await Promise.all([
      db.query('select * from movies where published and age_rating <= $1 and (title ilike $2 or original_title ilike $2) order by title limit $3', [profile.maturityLevel, pattern, q.limit]),
      db.query('select * from series where published and age_rating <= $1 and (title ilike $2 or original_title ilike $2) order by title limit $3', [profile.maturityLevel, pattern, q.limit]),
    ]);
    const items = [...movies.rows.map((r) => summary('movie', r)), ...series.rows.map((r) => summary('series', r))]
      .sort((a, b) => a.title.localeCompare(b.title))
      .slice(0, q.limit);
    return { items };
  });
}
