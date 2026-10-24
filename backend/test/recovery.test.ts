import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { generateRecoveryPhrase, isWellFormedPhrase, normalizePhrase } from '../src/security/recovery.js';
import { createTestApp, device, register, type TestApp } from './helpers.js';

let t: TestApp;
beforeEach(async () => { t ??= await createTestApp(); await t.reset(); });
afterAll(async () => { await t?.close(); });

describe('recovery phrase helpers', () => {
  it('generates 12 words that survive normalization', () => {
    const words = generateRecoveryPhrase();
    expect(words).toHaveLength(12);
    expect(isWellFormedPhrase(words.join(' '))).toBe(true);
  });

  it('ignores case, accents, punctuation and extra spaces', () => {
    expect(normalizePhrase('  Rivière,  ÉTOILE\nocéan ')).toBe('riviere etoile ocean');
    expect(isWellFormedPhrase('un deux trois')).toBe(false);
  });
});

describe('account recovery', () => {
  const setup = async (auth: Record<string, string>) =>
    (await t.app.inject({ method: 'POST', url: '/api/v1/auth/recovery/setup', headers: auth })).json().words as string[];

  const recover = (email: string, phrase: string, newPassword = 'brand new password', installationId = 'install-0005') =>
    t.app.inject({ method: 'POST', url: '/api/v1/auth/recover', payload: { email, phrase, newPassword, device: device(installationId) } });

  it('setup requires authentication and stores only a hash', async () => {
    expect((await t.app.inject({ method: 'POST', url: '/api/v1/auth/recovery/setup' })).statusCode).toBe(401);
    const { auth } = await register(t);
    const words = await setup(auth);
    expect(words).toHaveLength(12);
    const stored = (await t.db.query('select recovery_hash from users')).rows[0].recovery_hash as string;
    expect(stored.startsWith('scrypt$')).toBe(true);
    for (const w of words) expect(stored).not.toContain(w);
    expect((await t.app.inject({ method: 'GET', url: '/api/v1/me', headers: auth })).json().hasRecovery).toBe(true);
  });

  it('resets the password, revokes old sessions and burns the phrase', async () => {
    const { auth, body } = await register(t);
    const words = await setup(auth);

    const res = await recover('USER@example.com', words.join(' ').toUpperCase());
    expect(res.statusCode).toBe(200);
    expect(res.json().recoveryReset).toBe(true);

    expect((await t.app.inject({ method: 'POST', url: '/api/v1/auth/refresh', payload: { refreshToken: body.refreshToken } })).statusCode).toBe(401);
    const login = (password: string) => t.app.inject({ method: 'POST', url: '/api/v1/auth/login', payload: { email: 'user@example.com', password, device: device('install-0005') } });
    expect((await login('brand new password')).statusCode).toBe(200);
    expect((await login('correct horse battery')).statusCode).toBe(401);

    const again = await recover('user@example.com', words.join(' '), 'another password!!');
    expect(again.statusCode).toBe(401);
  });

  it('gives one error for wrong phrase, unknown email and missing setup', async () => {
    const { auth } = await register(t);
    const words = await setup(auth);
    await register(t, 'nosetup@example.com', 'install-0007');
    const wrongWords = generateRecoveryPhrase().join(' ');
    const codes = await Promise.all([
      recover('user@example.com', wrongWords),
      recover('nobody@example.com', words.join(' ')),
      recover('nosetup@example.com', words.join(' ')),
      recover('user@example.com', 'too short phrase'),
    ]);
    for (const r of codes) {
      expect(r.statusCode).toBe(401);
      expect(r.json().error.code).toBe('INVALID_RECOVERY');
    }
  });

  it('rate limits guessing', async () => {
    const { auth } = await register(t);
    await setup(auth);
    const codes: number[] = [];
    for (let i = 0; i < 7; i++) codes.push((await recover('user@example.com', generateRecoveryPhrase().join(' '))).statusCode);
    expect(codes.slice(5)).toEqual([429, 429]);
  });

  it('a new setup replaces the previous phrase', async () => {
    const { auth } = await register(t);
    const first = await setup(auth);
    await setup(auth);
    expect((await recover('user@example.com', first.join(' '))).statusCode).toBe(401);
  });
});

describe('google sign-in', () => {
  const identity = { sub: 'google-sub-1', email: 'g@example.com', emailVerified: true };
  const call = (tt: TestApp, idToken = 'x'.repeat(30)) =>
    tt.app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { idToken, device: device('install-0008', 'android_tv') } });

  it('is disabled when not configured', async () => {
    const res = await call(t);
    expect(res.statusCode).toBe(501);
  });

  it('creates an account with a profile, then signs in to the same one', async () => {
    const g = await createTestApp({ googleVerifier: async (tok) => (tok.startsWith('good') ? identity : null) });
    await g.reset();
    const first = await call(g, 'good'.padEnd(30, 'x'));
    expect(first.statusCode).toBe(201);
    const auth = { authorization: `Bearer ${first.json().accessToken}` };
    expect((await g.app.inject({ method: 'GET', url: '/api/v1/profiles', headers: auth })).json().profiles).toHaveLength(1);
    const second = await call(g, 'good'.padEnd(30, 'x'));
    expect(second.statusCode).toBe(200);
    expect(second.json().userId).toBe(first.json().userId);
    expect((await call(g, 'bad'.padEnd(30, 'x'))).statusCode).toBe(401);
    await g.close();
  });

  it('links to an existing email only when Google verified it', async () => {
    const g = await createTestApp({ googleVerifier: async (tok) => (tok.startsWith('unverified') ? { ...identity, emailVerified: false } : identity) });
    await g.reset();
    const existing = await register(g, 'g@example.com');
    expect((await call(g, 'unverified'.padEnd(30, 'x'))).statusCode).toBe(401);
    const linked = await call(g, 'good'.padEnd(30, 'x'));
    expect(linked.statusCode).toBe(200);
    expect(linked.json().userId).toBe(existing.body.userId);
    await g.close();
  });

  it('password login is refused for Google-only accounts', async () => {
    const g = await createTestApp({ googleVerifier: async () => identity });
    await g.reset();
    await call(g);
    const res = await g.app.inject({ method: 'POST', url: '/api/v1/auth/login', payload: { email: 'g@example.com', password: 'anything at all', device: device('install-0008') } });
    expect(res.statusCode).toBe(401);
    await g.close();
  });
});

describe('register with profile name', () => {
  it('uses the given name for the primary profile', async () => {
    const res = await t.app.inject({ method: 'POST', url: '/api/v1/auth/register', payload: { email: 'n@example.com', password: 'correct horse battery', profileName: 'Djidjoho', device: device('install-0001') } });
    const auth = { authorization: `Bearer ${res.json().accessToken}` };
    expect((await t.app.inject({ method: 'GET', url: '/api/v1/profiles', headers: auth })).json().profiles[0].name).toBe('Djidjoho');
  });
});
