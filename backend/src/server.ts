import { Redis } from 'ioredis';
import { buildApp } from './app.js';
import { loadConfig } from './config.js';
import { Db } from './db.js';
import { migrate } from './migrate.js';
import { googleVerifier } from './security/google.js';

const config = loadConfig();
const db = new Db(config.DATABASE_URL);
const redis = new Redis(config.REDIS_URL);

await migrate(db);
const app = buildApp({ config, db, redis, googleVerifier: config.GOOGLE_CLIENT_ID ? googleVerifier(config.GOOGLE_CLIENT_ID) : undefined });

const shutdown = async () => {
  await app.close();
  await db.close();
  redis.disconnect();
  process.exit(0);
};
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);

await app.listen({ port: config.PORT, host: '0.0.0.0' });
