import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { FastifyInstance } from 'fastify';

const dir = fileURLToPath(new URL('../../admin/', import.meta.url));
const file = (name: string) => readFileSync(dir + name, 'utf8');

/**
 * The admin web page. Served by the API itself so there is nothing else to deploy.
 * Strict CSP: only same-origin scripts and styles, no framing.
 */
export async function adminUiRoutes(app: FastifyInstance) {
  const assets: Record<string, [string, string]> = {
    '/admin': ['index.html', 'text/html; charset=utf-8'],
    '/admin/': ['index.html', 'text/html; charset=utf-8'],
    '/admin/app.js': ['app.js', 'text/javascript; charset=utf-8'],
    '/admin/app.css': ['app.css', 'text/css; charset=utf-8'],
  };
  for (const [path, [name, type]] of Object.entries(assets)) {
    const body = file(name);
    app.get(path, async (_req, reply) =>
      reply
        .header('content-type', type)
        .header('content-security-policy', "default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        .header('x-content-type-options', 'nosniff')
        .header('referrer-policy', 'no-referrer')
        .header('cache-control', 'no-store')
        .send(body),
    );
  }
}
