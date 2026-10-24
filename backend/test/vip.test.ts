import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { adminAuth, createTestApp, generateKeys, register, type TestApp } from './helpers.js';

let t: TestApp;
let admin: Record<string, string>;
beforeEach(async () => { t ??= await createTestApp(); await t.reset(); admin = await adminAuth(t); });
afterAll(async () => { await t?.close(); });

const activate = (auth: Record<string, string>, key: string) =>
  t.app.inject({ method: 'POST', url: '/api/v1/vip/activate', headers: auth, payload: { key } });

describe('vip activation', () => {
  it('activates a key and grants an entitlement', async () => {
    const { auth } = await register(t);
    const [key] = await generateKeys(t, admin);
    const res = await activate(auth, key!.toLowerCase());
    expect(res.statusCode).toBe(200);
    expect(res.json()).toMatchObject({ active: true, planId: 'vip_30d', deviceLimit: 2 });
    expect((await t.app.inject({ method: 'GET', url: '/api/v1/vip', headers: auth })).json().active).toBe(true);
  });

  it('stores only a hash of the key', async () => {
    const [key] = await generateKeys(t, admin);
    const rows = (await t.db.query('select * from vip_keys')).rows;
    expect(JSON.stringify(rows)).not.toContain(key!.slice(5));
    expect(rows[0].key_hash).toMatch(/^[0-9a-f]{64}$/);
  });

  it('rejects unknown and malformed keys with the same error', async () => {
    const { auth } = await register(t);
    const unknown = await activate(auth, 'VSOO-AAAA-BBBB-CCCC');
    const malformed = await activate(auth, 'not a key at all');
    expect(unknown.json().error.code).toBe('INVALID_KEY');
    expect(malformed.json().error.code).toBe('INVALID_KEY');
  });

  it('refuses to reuse a key on another account but is idempotent for the same one', async () => {
    const a = await register(t, 'a@example.com');
    const b = await register(t, 'b@example.com', 'install-0009');
    const [key] = await generateKeys(t, admin);
    expect((await activate(a.auth, key!)).statusCode).toBe(200);
    expect((await activate(a.auth, key!)).statusCode).toBe(200);
    expect((await t.db.query('select count(*)::int as n from vip_entitlements')).rows[0].n).toBe(1);
    const reuse = await activate(b.auth, key!);
    expect(reuse.statusCode).toBe(409);
    expect(reuse.json().error.code).toBe('KEY_ALREADY_USED');
  });

  it('handles concurrent activation of one key: exactly one winner', async () => {
    const users = await Promise.all([1, 2, 3, 4].map((i) => register(t, `u${i}@example.com`, `install-100${i}`)));
    const [key] = await generateKeys(t, admin);
    const results = await Promise.all(users.map((u) => activate(u.auth, key!)));
    expect(results.filter((r) => r.statusCode === 200)).toHaveLength(1);
    expect((await t.db.query('select count(*)::int as n from vip_entitlements')).rows[0].n).toBe(1);
  });

  it('rejects expired and revoked keys', async () => {
    const { auth } = await register(t);
    const [expired] = await generateKeys(t, admin);
    await t.db.query("update vip_keys set expires_at = now() - interval '1 day'");
    expect((await activate(auth, expired!)).json().error.code).toBe('KEY_EXPIRED');

    const [revoked] = await generateKeys(t, admin);
    const id = (await t.db.query("select id from vip_keys where status = 'active' and expires_at is null")).rows[0].id;
    await t.app.inject({ method: 'POST', url: `/api/v1/admin/vip/keys/${id}/revoke`, headers: admin });
    expect((await activate(auth, revoked!)).json().error.code).toBe('KEY_REVOKED');
  });

  it('stacks a second key after the running entitlement', async () => {
    const { auth } = await register(t);
    const keys = await generateKeys(t, admin, { quantity: 2 });
    await activate(auth, keys[0]!);
    await activate(auth, keys[1]!);
    const vip = (await t.app.inject({ method: 'GET', url: '/api/v1/vip', headers: auth })).json();
    const days = (new Date(vip.endsAt).getTime() - Date.now()) / 86_400_000;
    expect(days).toBeGreaterThan(59);
    expect(days).toBeLessThan(61);
  });

  it('rate limits activation attempts', async () => {
    const { auth } = await register(t);
    const codes: number[] = [];
    for (let i = 0; i < 7; i++) codes.push((await activate(auth, 'VSOO-AAAA-BBBB-CCCC')).statusCode);
    expect(codes.slice(5)).toEqual([429, 429]);
  });

  it('applies the key device limit to new devices', async () => {
    const { auth } = await register(t);
    const [key] = await generateKeys(t, admin, { deviceLimit: 3 });
    await activate(auth, key!);
    const login = (id: string) => t.app.inject({ method: 'POST', url: '/api/v1/auth/login', payload: { email: 'user@example.com', password: 'correct horse battery', device: { installationId: id, platform: 'android_tv' } } });
    expect((await login('install-0002')).statusCode).toBe(200);
    expect((await login('install-0003')).statusCode).toBe(200);
    expect((await login('install-0004')).statusCode).toBe(409);
  });
});

describe('admin', () => {
  it('rejects user tokens, anonymous calls and insufficient roles', async () => {
    const { auth } = await register(t);
    const call = (headers: Record<string, string>) => t.app.inject({ method: 'POST', url: '/api/v1/admin/vip/keys', headers, payload: { planId: 'vip_30d', quantity: 1 } });
    expect((await call({})).statusCode).toBe(401);
    expect((await call(auth)).statusCode).toBe(401);
    expect((await call(await adminAuth(t, 'SUPPORT'))).statusCode).toBe(403);
    expect((await call(await adminAuth(t, 'CONTENT_MANAGER'))).statusCode).toBe(403);
  });

  it('exports generated keys as CSV and audits the action', async () => {
    const res = await t.app.inject({ method: 'POST', url: '/api/v1/admin/vip/keys', headers: admin, payload: { planId: 'vip_30d', quantity: 3, deviceLimit: 2, format: 'csv' } });
    expect(res.statusCode).toBe(201);
    expect(res.headers['content-type']).toContain('text/csv');
    const lines = res.body.trim().split('\n');
    expect(lines[0]).toBe('key,plan,device_limit,status,created_at');
    expect(lines).toHaveLength(4);
    expect((await t.db.query("select count(*)::int as n from audit_logs where action = 'vip_keys.generate'")).rows[0].n).toBe(1);
  });

  it('lists keys without exposing raw keys, and lists users and devices', async () => {
    const { body } = await register(t);
    const [key] = await generateKeys(t, admin);
    const list = await t.app.inject({ method: 'GET', url: '/api/v1/admin/vip/keys', headers: admin });
    expect(list.body).not.toContain(key!.slice(5));
    const users = await t.app.inject({ method: 'GET', url: '/api/v1/admin/users', headers: admin });
    expect(users.json().users[0]).toMatchObject({ email: 'user@example.com', devices: 1, vip: false });
    const devices = await t.app.inject({ method: 'GET', url: `/api/v1/admin/users/${body.userId}/devices`, headers: admin });
    expect(devices.json().devices).toHaveLength(1);
  });

  it('revoking a key ends the entitlement it granted', async () => {
    const { auth } = await register(t);
    const [key] = await generateKeys(t, admin);
    await activate(auth, key!);
    const id = (await t.db.query('select id from vip_keys')).rows[0].id;
    expect((await t.app.inject({ method: 'POST', url: `/api/v1/admin/vip/keys/${id}/revoke`, headers: admin })).statusCode).toBe(204);
    expect((await t.app.inject({ method: 'GET', url: '/api/v1/vip', headers: auth })).json().active).toBe(false);
  });
});
