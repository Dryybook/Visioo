import { Db } from '../src/db.js';
import { migrate } from '../src/migrate.js';
import { testConfig } from './helpers.js';

export default async function setup() {
  const db = new Db(testConfig.DATABASE_URL);
  await db.query('drop schema public cascade; create schema public;');
  await migrate(db);
  await db.close();
}
