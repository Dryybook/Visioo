// DEVELOPMENT ONLY. Seeds a tiny catalog backed by openly licensed Blender Foundation
// films so the apps have something authorized to play. Never runs in production.
import { loadConfig } from '../config.js';
import { Db } from '../db.js';

const config = loadConfig();
if (config.NODE_ENV === 'production') {
  console.error('seed:dev refuses to run in production');
  process.exit(1);
}

const db = new Db(config.DATABASE_URL);
const BASE = 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample';

const films = [
  { title: 'Big Buck Bunny', year: 2008, runtime: 10, file: 'BigBuckBunny.mp4', vip: false, overview: 'Open movie by the Blender Foundation (CC BY 3.0).' },
  { title: 'Sintel', year: 2010, runtime: 15, file: 'Sintel.mp4', vip: true, overview: 'Open movie by the Blender Foundation (CC BY 3.0).' },
];

await db.tx(async (tx) => {
  await tx.query("insert into genres (slug, name) values ('animation', 'Animation') on conflict do nothing");
  await tx.query("insert into home_sections (id, title, layout, position) values ('popular_movies', 'Films populaires', 'poster', 1) on conflict do nothing");
  let position = 0;
  for (const f of films) {
    const exists = await tx.query('select id from movies where title = $1', [f.title]);
    if (exists.rows.length > 0) continue;
    const m = await tx.query(
      'insert into movies (title, overview, year, runtime_min, is_vip, published) values ($1, $2, $3, $4, $5, true) returning id',
      [f.title, f.overview, f.year, f.runtime, f.vip],
    );
    const id = m.rows[0].id;
    await tx.query("insert into movie_genres select $1, id from genres where slug = 'animation'", [id]);
    await tx.query("insert into home_section_items (section_id, content_type, content_id, position) values ('popular_movies', 'movie', $1, $2)", [id, position++]);
    await tx.query(
      "insert into sources (content_type, content_id, provider, url, type, quality, language, authorized) values ('movie', $1, 'dev-open-content', $2, 'MP4', 720, 'en', true)",
      [id, `${BASE}/${f.file}`],
    );
  }
});
console.log('Dev catalog seeded');
await db.close();
