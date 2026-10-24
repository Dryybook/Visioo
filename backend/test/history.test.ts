import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createTestApp, register, seedMovie, type TestApp } from './helpers.js';

let t: TestApp;
beforeEach(async () => { t ??= await createTestApp(); await t.reset(); });
afterAll(async () => { await t?.close(); });

const post = (auth: Record<string, string>, payload: object, extra: Record<string, string> = {}) =>
  t.app.inject({ method: 'POST', url: '/api/v1/history/progress', headers: { ...auth, ...extra }, payload });
const position = (auth: Record<string, string>, id: string, extra: Record<string, string> = {}) =>
  t.app.inject({ method: 'GET', url: `/api/v1/history/position?contentType=movie&contentId=${id}`, headers: { ...auth, ...extra } });

describe('history', () => {
  it('stores and updates the position, defaulting to zero', async () => {
    const { auth } = await register(t);
    const id = await seedMovie(t);
    expect((await position(auth, id)).json()).toEqual({ positionMs: 0, durationMs: 0 });
    expect((await post(auth, { contentType: 'movie', contentId: id, positionMs: 60_000, durationMs: 600_000 })).statusCode).toBe(204);
    await post(auth, { contentType: 'movie', contentId: id, positionMs: 120_000, durationMs: 600_000 });
    expect((await position(auth, id)).json()).toEqual({ positionMs: 120_000, durationMs: 600_000 });
    expect((await t.db.query('select count(*)::int as n from watch_history')).rows[0].n).toBe(1);
  });

  it('keeps positions separate per profile', async () => {
    const { auth } = await register(t);
    const id = await seedMovie(t);
    const kid = (await t.app.inject({ method: 'POST', url: '/api/v1/profiles', headers: auth, payload: { name: 'Kid', isChild: true } })).json();
    await post(auth, { contentType: 'movie', contentId: id, positionMs: 5000, durationMs: 9000 });
    expect((await position(auth, id, { 'x-profile-id': kid.id })).json().positionMs).toBe(0);
    expect((await position(auth, id)).json().positionMs).toBe(5000);
  });

  it('requires authentication and validates input', async () => {
    const id = await seedMovie(t);
    expect((await t.app.inject({ method: 'POST', url: '/api/v1/history/progress', payload: {} })).statusCode).toBe(401);
    const { auth } = await register(t);
    expect((await post(auth, { contentType: 'movie', contentId: id, positionMs: -1, durationMs: 10 })).statusCode).toBe(400);
    expect((await post(auth, { contentType: 'other', contentId: id, positionMs: 1, durationMs: 10 })).statusCode).toBe(400);
  });

  it('does not let a user read another profile that is not theirs', async () => {
    const a = await register(t, 'a@example.com');
    const b = await register(t, 'b@example.com', 'install-0009');
    const aProfile = (await t.app.inject({ method: 'GET', url: '/api/v1/profiles', headers: a.auth })).json().profiles[0];
    const id = await seedMovie(t);
    expect((await position(b.auth, id, { 'x-profile-id': aProfile.id })).statusCode).toBe(403);
  });
});
