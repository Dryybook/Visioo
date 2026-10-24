import { Redis } from 'ioredis';
import { buildApp } from '../src/app.js';
import type { Config } from '../src/config.js';
import { Db } from '../src/db.js';
import { hashPassword } from '../src/security/password.js';

export const testConfig: Config = {
  NODE_ENV: 'test',
  PORT: 0,
  DATABASE_URL: process.env.TEST_DATABASE_URL ?? 'postgres://postgres@127.0.0.1:5433/visioo_test',
  REDIS_URL: process.env.TEST_REDIS_URL ?? 'redis://127.0.0.1:6380/1',
  JWT_SECRET: 'test-jwt-secret-test-jwt-secret-test',
  VIP_KEY_PEPPER: 'test-pepper-test-pepper-test-pepper-1',
  PLAYBACK_SIGNING_SECRET: 'test-signing-secret-test-signing-secret',
  ACCESS_TOKEN_TTL_SEC: 900,
  REFRESH_TOKEN_TTL_DAYS: 30,
  PLAYBACK_URL_TTL_SEC: 900,
  FREE_DEVICE_LIMIT: 2,
};

const TABLES = [
  'audit_logs', 'admin_users', 'watchlist', 'watch_history', 'home_section_items', 'home_sections', 'source_health', 'sources',
  'episodes', 'seasons', 'movie_genres', 'series_genres', 'genres', 'movies', 'series',
  'vip_key_activations', 'vip_entitlements', 'vip_keys', 'refresh_tokens', 'devices', 'profiles', 'users',
];

export async function createTestApp() {
  const db = new Db(testConfig.DATABASE_URL);
  const redis = new Redis(testConfig.REDIS_URL);
  const app = buildApp({ config: testConfig, db, redis });
  await app.ready();
  const reset = async () => {
    await db.query(`truncate ${TABLES.join(', ')} restart identity cascade`);
    await redis.flushdb();
  };
  await reset();
  return {
    app, db, redis, reset,
    close: async () => { await app.close(); await db.close(); redis.disconnect(); },
  };
}

export type TestApp = Awaited<ReturnType<typeof createTestApp>>;

export const device = (installationId = 'install-0001', platform = 'android') => ({ installationId, platform });

export async function register(t: TestApp, email = 'user@example.com', installationId = 'install-0001') {
  const res = await t.app.inject({
    method: 'POST', url: '/api/v1/auth/register',
    payload: { email, password: 'correct horse battery', device: device(installationId) },
  });
  return { res, body: res.json(), auth: { authorization: `Bearer ${res.json().accessToken}` } };
}

export async function adminAuth(t: TestApp, role = 'SUPER_ADMIN') {
  const email = `${role.toLowerCase()}@example.com`;
  await t.db.query('insert into admin_users (email, password_hash, role) values ($1, $2, $3) on conflict do nothing', [email, await hashPassword('admin-password-123'), role]);
  const res = await t.app.inject({ method: 'POST', url: '/api/v1/admin/auth/login', payload: { email, password: 'admin-password-123' } });
  return { authorization: `Bearer ${res.json().accessToken}` };
}

export async function generateKeys(t: TestApp, adminHeaders: Record<string, string>, extra: object = {}) {
  const res = await t.app.inject({
    method: 'POST', url: '/api/v1/admin/vip/keys', headers: adminHeaders,
    payload: { planId: 'vip_30d', quantity: 1, ...extra },
  });
  return res.json().keys as string[];
}

export async function seedMovie(t: TestApp, opts: { title?: string; isVip?: boolean; ageRating?: number; sourceUrl?: string; authorized?: boolean } = {}) {
  const m = await t.db.query(
    'insert into movies (title, is_vip, age_rating, published) values ($1, $2, $3, true) returning id',
    [opts.title ?? 'Test Movie', opts.isVip ?? false, opts.ageRating ?? 0],
  );
  const id = m.rows[0].id as string;
  await t.db.query(
    "insert into sources (content_type, content_id, provider, url, type, quality, authorized) values ('movie', $1, 'test', $2, 'HLS', 1080, $3)",
    [id, opts.sourceUrl ?? 'https://cdn.example.com/movie/master.m3u8', opts.authorized ?? true],
  );
  return id;
}
