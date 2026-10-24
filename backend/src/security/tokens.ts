import { createHash, randomBytes } from 'node:crypto';
import { SignJWT, jwtVerify } from 'jose';
import type { Config } from '../config.js';

export type TokenKind = 'user' | 'admin';

export interface AccessClaims {
  sub: string;
  kind: TokenKind;
  did?: string;
  role?: string;
}

const AUDIENCE: Record<TokenKind, string> = { user: 'visioo-user', admin: 'visioo-admin' };

export async function signAccessToken(config: Config, claims: AccessClaims): Promise<string> {
  return new SignJWT({ did: claims.did, role: claims.role })
    .setProtectedHeader({ alg: 'HS256' })
    .setSubject(claims.sub)
    .setAudience(AUDIENCE[claims.kind])
    .setIssuer('visioo')
    .setIssuedAt()
    .setExpirationTime(`${config.ACCESS_TOKEN_TTL_SEC}s`)
    .sign(new TextEncoder().encode(config.JWT_SECRET));
}

/** Returns null for any invalid, expired or wrong-audience token. */
export async function verifyAccessToken(config: Config, token: string, kind: TokenKind): Promise<AccessClaims | null> {
  try {
    const { payload } = await jwtVerify(token, new TextEncoder().encode(config.JWT_SECRET), {
      audience: AUDIENCE[kind],
      issuer: 'visioo',
      algorithms: ['HS256'],
    });
    if (!payload.sub) return null;
    return { sub: payload.sub, kind, did: payload.did as string | undefined, role: payload.role as string | undefined };
  } catch {
    return null;
  }
}

export const newRefreshToken = () => `rt_${randomBytes(32).toString('base64url')}`;
export const hashToken = (token: string) => createHash('sha256').update(token).digest('hex');
