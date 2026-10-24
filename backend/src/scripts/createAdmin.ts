import { loadConfig } from '../config.js';
import { Db } from '../db.js';
import { hashPassword } from '../security/password.js';

// Usage: ADMIN_EMAIL=... ADMIN_PASSWORD=... [ADMIN_ROLE=SUPER_ADMIN] npm run admin:create
const email = process.env.ADMIN_EMAIL;
const password = process.env.ADMIN_PASSWORD;
const role = process.env.ADMIN_ROLE ?? 'SUPER_ADMIN';
if (!email || !password || password.length < 12) {
  console.error('ADMIN_EMAIL and ADMIN_PASSWORD (12+ characters) are required');
  process.exit(1);
}
if (!['SUPER_ADMIN', 'ADMIN', 'CONTENT_MANAGER', 'SUPPORT'].includes(role)) {
  console.error('Invalid ADMIN_ROLE');
  process.exit(1);
}
const db = new Db(loadConfig().DATABASE_URL);
await db.query('insert into admin_users (email, password_hash, role) values ($1, $2, $3)', [email, await hashPassword(password), role]);
console.log(`Admin ${email} created with role ${role}`);
await db.close();
