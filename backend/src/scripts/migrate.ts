import { loadConfig } from '../config.js';
import { Db } from '../db.js';
import { migrate } from '../migrate.js';

const db = new Db(loadConfig().DATABASE_URL);
const applied = await migrate(db);
console.log(applied.length ? `Applied: ${applied.join(', ')}` : 'Database is up to date');
await db.close();
