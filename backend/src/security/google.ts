import { createRemoteJWKSet, jwtVerify } from 'jose';

export interface GoogleIdentity {
  sub: string;
  email: string;
  emailVerified: boolean;
}

export type GoogleVerifier = (idToken: string) => Promise<GoogleIdentity | null>;

const JWKS = createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'));

/** Verifies a Google ID token's signature, issuer, audience (our web client id) and expiry. */
export function googleVerifier(clientId: string): GoogleVerifier {
  return async (idToken) => {
    try {
      const { payload } = await jwtVerify(idToken, JWKS, {
        issuer: ['https://accounts.google.com', 'accounts.google.com'],
        audience: clientId,
      });
      if (!payload.sub || typeof payload.email !== 'string') return null;
      return { sub: payload.sub, email: payload.email, emailVerified: payload.email_verified === true };
    } catch {
      return null;
    }
  };
}
