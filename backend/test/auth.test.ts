import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createTestApp, device, register, type TestApp } from './helpers.js';

let t: TestApp;
beforeEach(async () => { t ??= await createTestApp(); await t.reset(); });
afterAll(async () => { await t?.close(); });

const login = (email: string, password: string, installationId = 'install-0001') =>
  t.app.inject({ method: 'POST', url: '/api/v1/auth/login', payload: { email, password, device: device(installationId) } });

describe('auth', () => {
  it('registers, creates a primary profile and serves /me', async () => {
    const { res, auth } = await register(t);
    expect(res.statusCode).toBe(201);
    const me = await t.app.inject({ method: 'GET', url: '/api/v1/me', headers: auth });
    expect(me.json()).toMatchObject({ email: 'user@example.com', vip: { active: false } });
    const profiles = await t.app.inject({ method: 'GET', url: '/api/v1/profiles', headers: auth });
    expect(profiles.json().profiles).toHaveLength(1);
  });

  it('rejects duplicate emails regardless of case', async () => {
    await register(t, 'User@Example.com');
    const dup = await register(t, 'user@example.com', 'install-0002');
    expect(dup.res.statusCode).toBe(409);
    expect(dup.body.error.code).toBe('EMAIL_TAKEN');
  });

  it('logs in and rejects wrong passwords with the same error as unknown users', async () => {
    await register(t);
    expect((await login('user@example.com', 'correct horse battery')).statusCode).toBe(200);
    const wrong = await login('user@example.com', 'wrong password!!');
    const unknown = await login('nobody@example.com', 'wrong password!!');
    expect(wrong.statusCode).toBe(401);
    expect(unknown.statusCode).toBe(401);
    expect(wrong.json().error.code).toBe(unknown.json().error.code);
  });

  it('requires a token and rejects garbage', async () => {
    expect((await t.app.inject({ method: 'GET', url: '/api/v1/me' })).statusCode).toBe(401);
    expect((await t.app.inject({ method: 'GET', url: '/api/v1/me', headers: { authorization: 'Bearer nope' } })).statusCode).toBe(401);
  });

  it('rotates refresh tokens and kills the device sessions on replay', async () => {
    const { body } = await register(t);
    const first = await t.app.inject({ method: 'POST', url: '/api/v1/auth/refresh', payload: { refreshToken: body.refreshToken } });
    expect(first.statusCode).toBe(200);
    const replay = await t.app.inject({ method: 'POST', url: '/api/v1/auth/refresh', payload: { refreshToken: body.refreshToken } });
    expect(replay.statusCode).toBe(401);
    const afterReplay = await t.app.inject({ method: 'POST', url: '/api/v1/auth/refresh', payload: { refreshToken: first.json().refreshToken } });
    expect(afterReplay.statusCode).toBe(401);
  });

  it('logout revokes the refresh token', async () => {
    const { body } = await register(t);
    expect((await t.app.inject({ method: 'POST', url: '/api/v1/auth/logout', payload: { refreshToken: body.refreshToken } })).statusCode).toBe(204);
    expect((await t.app.inject({ method: 'POST', url: '/api/v1/auth/refresh', payload: { refreshToken: body.refreshToken } })).statusCode).toBe(401);
  });

  it('rate limits login attempts per IP', async () => {
    await register(t);
    const codes: number[] = [];
    for (let i = 0; i < 7; i++) codes.push((await login('user@example.com', 'wrong password!!')).statusCode);
    expect(codes.slice(0, 5).every((c) => c === 401)).toBe(true);
    expect(codes.slice(5)).toEqual([429, 429]);
  });

  it('validates input', async () => {
    const res = await t.app.inject({ method: 'POST', url: '/api/v1/auth/register', payload: { email: 'bad', password: 'x', device: {} } });
    expect(res.statusCode).toBe(400);
    expect(res.json().error.code).toBe('VALIDATION_ERROR');
  });
});

describe('devices', () => {
  it('enforces the free device limit and lets the user free a slot', async () => {
    const { auth } = await register(t);
    await login('user@example.com', 'correct horse battery', 'install-0002');
    const third = await login('user@example.com', 'correct horse battery', 'install-0003');
    expect(third.statusCode).toBe(409);
    expect(third.json().error.code).toBe('DEVICE_LIMIT_REACHED');

    const list = await t.app.inject({ method: 'GET', url: '/api/v1/devices', headers: auth });
    const other = list.json().devices.find((d: { current: boolean }) => !d.current);
    expect((await t.app.inject({ method: 'DELETE', url: `/api/v1/devices/${other.id}`, headers: auth })).statusCode).toBe(204);
    expect((await login('user@example.com', 'correct horse battery', 'install-0003')).statusCode).toBe(200);
  });

  it('immediately invalidates the access token of a revoked device', async () => {
    const { auth, body } = await register(t);
    const second = await login('user@example.com', 'correct horse battery', 'install-0002');
    const secondAuth = { authorization: `Bearer ${second.json().accessToken}` };
    expect((await t.app.inject({ method: 'GET', url: '/api/v1/me', headers: secondAuth })).statusCode).toBe(200);
    await t.app.inject({ method: 'DELETE', url: `/api/v1/devices/${second.json().deviceId}`, headers: auth });
    const after = await t.app.inject({ method: 'GET', url: '/api/v1/me', headers: secondAuth });
    expect(after.statusCode).toBe(401);
    expect(after.json().error.code).toBe('DEVICE_REVOKED');
    expect(body.deviceId).not.toBe(second.json().deviceId);
  });
});

describe('profiles', () => {
  it('clamps maturity for child profiles and keeps at least one profile', async () => {
    const { auth } = await register(t);
    const created = await t.app.inject({ method: 'POST', url: '/api/v1/profiles', headers: auth, payload: { name: 'Enfants', isChild: true, maturityLevel: 18 } });
    expect(created.statusCode).toBe(201);
    expect(created.json().maturityLevel).toBe(12);
    const list = (await t.app.inject({ method: 'GET', url: '/api/v1/profiles', headers: auth })).json().profiles;
    for (const p of list) await t.app.inject({ method: 'DELETE', url: `/api/v1/profiles/${p.id}`, headers: auth });
    expect((await t.app.inject({ method: 'GET', url: '/api/v1/profiles', headers: auth })).json().profiles).toHaveLength(1);
  });

  it('does not let a user touch another user\'s profile', async () => {
    const a = await register(t, 'a@example.com');
    const b = await register(t, 'b@example.com', 'install-0009');
    const aProfile = (await t.app.inject({ method: 'GET', url: '/api/v1/profiles', headers: a.auth })).json().profiles[0];
    expect((await t.app.inject({ method: 'PATCH', url: `/api/v1/profiles/${aProfile.id}`, headers: b.auth, payload: { name: 'hack' } })).statusCode).toBe(404);
  });
});
