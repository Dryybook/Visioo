import Fastify, { type FastifyInstance } from 'fastify';
import { ZodError } from 'zod';
import type { Deps } from './context.js';
import { AppError } from './errors.js';
import { adminRoutes } from './routes/admin.js';
import { authRoutes } from './routes/auth.js';
import { catalogRoutes } from './routes/catalog.js';
import { deviceRoutes } from './routes/devices.js';
import { historyRoutes } from './routes/history.js';
import { playbackRoutes } from './routes/playback.js';
import { profileRoutes } from './routes/profiles.js';
import { vipRoutes } from './routes/vip.js';

export function buildApp(deps: Deps): FastifyInstance {
  const app = Fastify({
    logger: deps.config.NODE_ENV === 'test' ? false : { redact: ['req.headers.authorization'] },
    trustProxy: true,
    bodyLimit: 64 * 1024,
  });

  app.setErrorHandler((err, req, reply) => {
    if (err instanceof ZodError) {
      return reply.status(400).send({ error: { code: 'VALIDATION_ERROR', message: 'Invalid request', details: err.issues.map((i) => ({ path: i.path.join('.'), message: i.message })) } });
    }
    if (err instanceof AppError) {
      return reply.status(err.status).send({ error: { code: err.code, message: err.message, details: err.details } });
    }
    const status = (err as { statusCode?: number }).statusCode;
    if (status && status >= 400 && status < 500) {
      return reply.status(status).send({ error: { code: 'BAD_REQUEST', message: 'Bad request' } });
    }
    req.log.error({ err }, 'unhandled error');
    return reply.status(500).send({ error: { code: 'INTERNAL', message: 'Internal error' } });
  });

  app.get('/health', async () => ({ status: 'ok' }));

  app.register(async (api) => {
    await authRoutes(api, deps);
    await profileRoutes(api, deps);
    await vipRoutes(api, deps);
    await deviceRoutes(api, deps);
    await catalogRoutes(api, deps);
    await playbackRoutes(api, deps);
    await historyRoutes(api, deps);
    await adminRoutes(api, deps);
  }, { prefix: '/api/v1' });

  return app;
}
