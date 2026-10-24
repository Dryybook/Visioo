import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { verifyPlaybackSignature } from '../src/security/signedUrl.js';
import { testConfig } from './helpers.js';
import { adminAuth, createTestApp, generateKeys, register, seedMovie, type TestApp } from './helpers.js';

let t: TestApp;
beforeEach(async () => { t ??= await createTestApp(); await t.reset(); });
afterAll(async () => { await t?.close(); });

const resolve = (auth: Record<string, string>, contentId: string, extra: Record<string, string> = {}) =>
  t.app.inject({ method: 'POST', url: '/api/v1/playback/resolve', headers: { ...auth, ...extra }, payload: { contentType: 'movie', contentId } });

describe('playback', () => {
  it('requires authentication', async () => {
    const id = await seedMovie(t);
    const res = await t.app.inject({ method: 'POST', url: '/api/v1/playback/resolve', payload: { contentType: 'movie', contentId: id } });
    expect(res.statusCode).toBe(401);
  });

  it('serves free content with a short-lived signed URL', async () => {
    const { auth } = await register(t);
    const id = await seedMovie(t);
    const res = await resolve(auth, id);
    expect(res.statusCode).toBe(200);
    const source = res.json().sources[0];
    const u = new URL(source.url);
    expect(verifyPlaybackSignature(testConfig.PLAYBACK_SIGNING_SECRET, u.pathname, Number(u.searchParams.get('exp')), u.searchParams.get('sig')!)).toBe(true);
    const ttl = Number(u.searchParams.get('exp')) - Date.now() / 1000;
    expect(ttl).toBeGreaterThan(300);
    expect(ttl).toBeLessThanOrEqual(1800);
  });

  it('blocks VIP content without an entitlement and allows it after activation', async () => {
    const { auth } = await register(t);
    const id = await seedMovie(t, { isVip: true });
    const denied = await resolve(auth, id);
    expect(denied.statusCode).toBe(403);
    expect(denied.json().error.code).toBe('VIP_REQUIRED');

    const admin = await adminAuth(t);
    const [key] = await generateKeys(t, admin);
    await t.app.inject({ method: 'POST', url: '/api/v1/vip/activate', headers: auth, payload: { key } });
    expect((await resolve(auth, id)).statusCode).toBe(200);
  });

  it('reports an expired VIP differently from never having one', async () => {
    const { auth } = await register(t);
    const id = await seedMovie(t, { isVip: true });
    const admin = await adminAuth(t);
    const [key] = await generateKeys(t, admin);
    await t.app.inject({ method: 'POST', url: '/api/v1/vip/activate', headers: auth, payload: { key } });
    await t.db.query("update vip_entitlements set ends_at = now() - interval '1 hour', starts_at = now() - interval '31 days'");
    expect((await resolve(auth, id)).json().error.code).toBe('VIP_EXPIRED');
  });

  it('never returns unauthorized sources', async () => {
    const { auth } = await register(t);
    const id = await seedMovie(t, { authorized: false });
    const res = await resolve(auth, id);
    expect(res.statusCode).toBe(404);
    expect(res.json().error.code).toBe('NO_SOURCE');
  });

  it('enforces maturity on the server for child profiles', async () => {
    const { auth } = await register(t);
    const id = await seedMovie(t, { ageRating: 16 });
    const child = (await t.app.inject({ method: 'POST', url: '/api/v1/profiles', headers: auth, payload: { name: 'Kid', isChild: true } })).json();
    const res = await resolve(auth, id, { 'x-profile-id': child.id });
    expect(res.statusCode).toBe(403);
    expect(res.json().error.code).toBe('PROFILE_RESTRICTED');
    expect((await resolve(auth, id)).statusCode).toBe(200);
  });

  it('rejects a profile id that belongs to someone else', async () => {
    const a = await register(t, 'a@example.com');
    const b = await register(t, 'b@example.com', 'install-0009');
    const aProfile = (await t.app.inject({ method: 'GET', url: '/api/v1/profiles', headers: a.auth })).json().profiles[0];
    const id = await seedMovie(t);
    expect((await resolve(b.auth, id, { 'x-profile-id': aProfile.id })).statusCode).toBe(403);
  });
});

describe('catalog', () => {
  it('builds /home from sections, hides unpublished and age-restricted items', async () => {
    const { auth } = await register(t);
    const ok = await seedMovie(t, { title: 'Visible' });
    const adult = await seedMovie(t, { title: 'Adult', ageRating: 18 });
    const hidden = (await t.db.query("insert into movies (title, published) values ('Hidden', false) returning id")).rows[0].id;
    await t.db.query("insert into home_sections (id, title, layout, position) values ('popular_movies', 'Films populaires', 'poster', 1), ('empty', 'Empty', 'poster', 2)");
    for (const [i, id] of [ok, adult, hidden].entries()) {
      await t.db.query("insert into home_section_items (section_id, content_type, content_id, position) values ('popular_movies', 'movie', $1, $2)", [id, i]);
    }
    const child = (await t.app.inject({ method: 'POST', url: '/api/v1/profiles', headers: auth, payload: { name: 'Kid', isChild: true } })).json();

    const parent = (await t.app.inject({ method: 'GET', url: '/api/v1/home', headers: auth })).json();
    expect(parent.sections).toHaveLength(1);
    expect(parent.sections[0].items.map((i: { title: string }) => i.title)).toEqual(['Visible', 'Adult']);

    const kid = (await t.app.inject({ method: 'GET', url: '/api/v1/home', headers: { ...auth, 'x-profile-id': child.id } })).json();
    expect(kid.sections[0].items.map((i: { title: string }) => i.title)).toEqual(['Visible']);
  });

  it('serves the hero banner separately from the rows and lists genres', async () => {
    const { auth } = await register(t);
    const id = await seedMovie(t, { title: 'Featured' });
    await t.db.query("update movies set overview = 'A story.' where id = $1", [id]);
    await t.db.query("insert into home_sections (id, title, layout, position) values ('hero', 'Hero', 'landscape', 0), ('rows', 'Rows', 'poster', 1)");
    await t.db.query("insert into home_section_items (section_id, content_type, content_id, position) values ('hero', 'movie', $1, 0), ('rows', 'movie', $1, 0)", [id]);
    await t.db.query("insert into genres (slug, name) values ('action', 'Action'), ('animation', 'Animation')");
    const home = (await t.app.inject({ method: 'GET', url: '/api/v1/home', headers: auth })).json();
    expect(home.hero).toHaveLength(1);
    expect(home.hero[0]).toMatchObject({ title: 'Featured', overview: 'A story.' });
    expect(home.sections.map((x: { id: string }) => x.id)).toEqual(['rows']);
    const genres = (await t.app.inject({ method: 'GET', url: '/api/v1/genres', headers: auth })).json().genres;
    expect(genres.map((g: { slug: string }) => g.slug)).toEqual(['action', 'animation']);
  });

  it('never exposes origin source URLs in movie details', async () => {
    const { auth } = await register(t);
    const id = await seedMovie(t, { sourceUrl: 'https://origin.internal/secret/master.m3u8' });
    const res = await t.app.inject({ method: 'GET', url: `/api/v1/movies/${id}`, headers: auth });
    expect(res.statusCode).toBe(200);
    expect(res.body).not.toContain('origin.internal');
  });

  it('serves series seasons with episodes and searches safely', async () => {
    const { auth } = await register(t);
    const s = (await t.db.query("insert into series (title, published) values ('Show 100%', true) returning id")).rows[0].id;
    const season = (await t.db.query('insert into seasons (series_id, season_number) values ($1, 1) returning id', [s])).rows[0].id;
    await t.db.query("insert into episodes (season_id, episode_number, title) values ($1, 1, 'Pilot'), ($1, 2, 'Second')", [season]);
    const seasons = (await t.app.inject({ method: 'GET', url: `/api/v1/series/${s}/seasons`, headers: auth })).json();
    expect(seasons.seasons[0].episodes.map((e: { title: string }) => e.title)).toEqual(['Pilot', 'Second']);
    const found = (await t.app.inject({ method: 'GET', url: '/api/v1/search?q=100%25', headers: auth })).json();
    expect(found.items).toHaveLength(1);
    const none = (await t.app.inject({ method: 'GET', url: '/api/v1/search?q=%25%25', headers: auth })).json();
    expect(none.items).toHaveLength(0);
  });
});
