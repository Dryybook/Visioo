import { createHmac, timingSafeEqual } from 'node:crypto';

function signature(secret: string, pathname: string, exp: number): string {
  return createHmac('sha256', secret).update(`${pathname}\n${exp}`).digest('base64url');
}

/**
 * Appends `exp` and `sig` query parameters. The CDN/origin verifies them with the same
 * secret (see verifyPlaybackSignature) so a leaked URL stops working after `ttlSec`.
 */
export function signPlaybackUrl(secret: string, url: string, ttlSec: number, nowMs = Date.now()) {
  const u = new URL(url);
  const exp = Math.floor(nowMs / 1000) + ttlSec;
  u.searchParams.set('exp', String(exp));
  u.searchParams.set('sig', signature(secret, u.pathname, exp));
  return { url: u.toString(), expiresAt: new Date(exp * 1000) };
}

export function verifyPlaybackSignature(secret: string, pathname: string, exp: number, sig: string, nowMs = Date.now()): boolean {
  if (!Number.isFinite(exp) || exp * 1000 < nowMs) return false;
  const expected = Buffer.from(signature(secret, pathname, exp));
  const actual = Buffer.from(sig);
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}
