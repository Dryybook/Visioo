import { describe, expect, it } from 'vitest';
import { hashPassword, verifyPassword } from '../src/security/password.js';
import { signPlaybackUrl, verifyPlaybackSignature } from '../src/security/signedUrl.js';
import { rankSources, type RankableSource } from '../src/security/sourceRanking.js';
import { displayPrefix, generateVipKey, hashVipKey, normalizeVipKey } from '../src/security/vipKey.js';

describe('vip keys', () => {
  it('generates well-formed, distinct keys', () => {
    const keys = new Set(Array.from({ length: 200 }, generateVipKey));
    expect(keys.size).toBe(200);
    for (const k of keys) expect(normalizeVipKey(k)).toBe(k);
  });

  it('normalizes casing and separators', () => {
    expect(normalizeVipKey('vsoo 8k7m qp4x 2p9d')).toBe('VSOO-8K7M-QP4X-2P9D');
    expect(normalizeVipKey('VSOO8K7MQP4X2P9D')).toBe('VSOO-8K7M-QP4X-2P9D');
  });

  it('rejects malformed keys', () => {
    expect(normalizeVipKey('VSOO-0000-1111-IIII')).toBeNull();
    expect(normalizeVipKey('ABCD-8K7M-QP4X-2P9D')).toBeNull();
    expect(normalizeVipKey('VSOO-8K7M')).toBeNull();
  });

  it('hashes deterministically per pepper and never returns the key', () => {
    const k = 'VSOO-8K7M-QP4X-2P9D';
    expect(hashVipKey('p'.repeat(32), k)).toBe(hashVipKey('p'.repeat(32), k));
    expect(hashVipKey('p'.repeat(32), k)).not.toBe(hashVipKey('q'.repeat(32), k));
    expect(hashVipKey('p'.repeat(32), k)).not.toContain('8K7M');
    expect(displayPrefix(k)).toBe('VSOO-8K7M');
  });
});

describe('passwords', () => {
  it('verifies the right password only', async () => {
    const h = await hashPassword('s3cret-password');
    expect(await verifyPassword('s3cret-password', h)).toBe(true);
    expect(await verifyPassword('wrong', h)).toBe(false);
    expect(await verifyPassword('x', 'garbage')).toBe(false);
  });
});

describe('signed playback urls', () => {
  const secret = 's'.repeat(32);
  it('verifies until expiry and rejects tampering', () => {
    const now = 1_700_000_000_000;
    const { url } = signPlaybackUrl(secret, 'https://cdn.example.com/a/master.m3u8?x=1', 600, now);
    const u = new URL(url);
    const exp = Number(u.searchParams.get('exp'));
    const sig = u.searchParams.get('sig')!;
    expect(u.searchParams.get('x')).toBe('1');
    expect(verifyPlaybackSignature(secret, u.pathname, exp, sig, now + 1000)).toBe(true);
    expect(verifyPlaybackSignature(secret, u.pathname, exp, sig, now + 601_000)).toBe(false);
    expect(verifyPlaybackSignature(secret, '/other.m3u8', exp, sig, now + 1000)).toBe(false);
    expect(verifyPlaybackSignature('t'.repeat(32), u.pathname, exp, sig, now + 1000)).toBe(false);
  });
});

describe('source ranking', () => {
  const s = (o: Partial<RankableSource> & { id: string }): RankableSource => ({
    type: 'HLS', quality: 1080, language: 'fr', priority: 0, authorized: true, health: 'HEALTHY', latencyMs: 100, ...o,
  });

  it('drops unauthorized and DOWN sources', () => {
    const r = rankSources([s({ id: 'a', authorized: false }), s({ id: 'b', health: 'DOWN' }), s({ id: 'c' })]);
    expect(r.map((x) => x.id)).toEqual(['c']);
  });

  it('prefers healthy, then language, then quality, then adaptive', () => {
    expect(rankSources([s({ id: 'deg', health: 'DEGRADED' }), s({ id: 'ok' })]).map((x) => x.id)[0]).toBe('ok');
    expect(rankSources([s({ id: 'en', language: 'en' }), s({ id: 'fr' })], { language: 'fr' })[0]!.id).toBe('fr');
    expect(rankSources([s({ id: '720', quality: 720 }), s({ id: '1080' })])[0]!.id).toBe('1080');
    expect(rankSources([s({ id: 'mp4', type: 'MP4' }), s({ id: 'hls' })])[0]!.id).toBe('hls');
  });

  it('does not pick a quality above the target when one at or below exists', () => {
    expect(rankSources([s({ id: '2160', quality: 2160 }), s({ id: '720', quality: 720 })], { preferredQuality: 720 })[0]!.id).toBe('720');
  });
});
