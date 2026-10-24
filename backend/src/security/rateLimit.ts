import type { Redis } from 'ioredis';

/** Fixed-window counter. Returns true while the caller is within `limit` per `windowSec`. */
export async function allow(redis: Redis, key: string, limit: number, windowSec: number): Promise<boolean> {
  const k = `rl:${key}`;
  const count = await redis.incr(k);
  if (count === 1) await redis.expire(k, windowSec);
  return count <= limit;
}
