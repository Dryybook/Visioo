import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { adminAuth, createTestApp, register, type TestApp } from './helpers.js';

let t: TestApp;
beforeEach(async () => { t ??= await createTestApp(); await t.reset(); });
afterAll(async () => { await t?.close(); });

describe('admin web page', () => {
  it('serves the page and its assets with a strict content security policy', async () => {
    for (const [url, type] of [['/admin', 'text/html'], ['/admin/app.js', 'text/javascript'], ['/admin/app.css', 'text/css']]) {
      const res = await t.app.inject({ method: 'GET', url: url! });
      expect(res.statusCode).toBe(200);
      expect(res.headers['content-type']).toContain(type);
      expect(res.headers['content-security-policy']).toContain("default-src 'self'");
      expect(res.headers['content-security-policy']).toContain("frame-ancestors 'none'");
      expect(res.headers['cache-control']).toBe('no-store');
    }
  });

  it('never injects server data as HTML', async () => {
    const js = (await t.app.inject({ method: 'GET', url: '/admin/app.js' })).body;
    expect(js).not.toMatch(/innerHTML|outerHTML|insertAdjacentHTML|document\.write|eval\(/);
  });

  it('lists plans for staff only', async () => {
    expect((await t.app.inject({ method: 'GET', url: '/api/v1/admin/vip/plans' })).statusCode).toBe(401);
    const { auth } = await register(t);
    expect((await t.app.inject({ method: 'GET', url: '/api/v1/admin/vip/plans', headers: auth })).statusCode).toBe(401);
    const res = await t.app.inject({ method: 'GET', url: '/api/v1/admin/vip/plans', headers: await adminAuth(t, 'SUPPORT') });
    expect(res.json().plans).toEqual([
      { id: 'vip_1y', name: 'VIP 1 an', durationDays: 365, deviceLimit: 2 },
      { id: 'vip_2y', name: 'VIP 2 ans', durationDays: 730, deviceLimit: 2 },
    ]);
  });
});
