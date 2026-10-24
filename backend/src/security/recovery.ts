import { randomInt } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { hashPassword, verifyPassword } from './password.js';

const WORDS = readFileSync(fileURLToPath(new URL('../../data/wordlist-fr.txt', import.meta.url)), 'utf8')
  .split('\n')
  .map((w) => w.trim())
  .filter(Boolean);

export const PHRASE_LENGTH = 12;

if (WORDS.length !== 2048) throw new Error('Recovery word list must contain 2048 words');

/** 12 words drawn with a CSPRNG from 2048 words: 132 bits. */
export function generateRecoveryPhrase(): string[] {
  return Array.from({ length: PHRASE_LENGTH }, () => WORDS[randomInt(WORDS.length)] as string);
}

/** Case, accent and spacing insensitive so a TV keyboard without accents still works. */
export function normalizePhrase(input: string): string {
  return input
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .split(/[^a-z]+/)
    .filter(Boolean)
    .join(' ');
}

export const isWellFormedPhrase = (input: string) => normalizePhrase(input).split(' ').length === PHRASE_LENGTH;

export const hashRecoveryPhrase = (phrase: string) => hashPassword(normalizePhrase(phrase));
export const verifyRecoveryPhrase = (phrase: string, stored: string) => verifyPassword(normalizePhrase(phrase), stored);
