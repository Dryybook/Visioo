import { createHmac, randomInt } from 'node:crypto';

// No 0/O/1/I/L to avoid transcription mistakes.
const ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const PREFIX = 'VSOO';

export function generateVipKey(): string {
  const group = () => Array.from({ length: 4 }, () => ALPHABET[randomInt(ALPHABET.length)]).join('');
  return `${PREFIX}-${group()}-${group()}-${group()}`;
}

/** Accepts any casing and separators. Returns the canonical form or null if malformed. */
export function normalizeVipKey(input: string): string | null {
  const raw = input.toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (raw.length !== 16 || !raw.startsWith(PREFIX)) return null;
  const body = raw.slice(4);
  if (![...body].every((c) => ALPHABET.includes(c))) return null;
  return `${PREFIX}-${body.slice(0, 4)}-${body.slice(4, 8)}-${body.slice(8, 12)}`;
}

export function hashVipKey(pepper: string, normalizedKey: string): string {
  return createHmac('sha256', pepper).update(normalizedKey).digest('hex');
}

export const displayPrefix = (normalizedKey: string) => normalizedKey.slice(0, 9);
