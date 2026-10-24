# VISIOO backend

Fastify + TypeScript API (`/api/v1`) on PostgreSQL and Redis.

## Run locally

```bash
docker compose up -d postgres redis
cp .env.example .env          # then set the three secrets
npm install
npm run migrate
npm run seed:dev              # optional, dev only: two open-licensed films
ADMIN_EMAIL=you@example.com ADMIN_PASSWORD='a-long-password' npm run admin:create
npm run dev
```

## Tests

Integration tests need PostgreSQL and Redis. Defaults: `postgres://postgres@127.0.0.1:5433/visioo_test` and `redis://127.0.0.1:6380/1`. Override with `TEST_DATABASE_URL` and `TEST_REDIS_URL`. The test database is wiped on each run.

```bash
npm test
```

## Endpoints

| Area | Routes |
| --- | --- |
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`; `GET/PATCH /me` |
| Profiles | `GET/POST /profiles`, `PATCH/DELETE /profiles/:id` |
| VIP | `GET /vip`, `POST /vip/activate` |
| Devices | `GET /devices`, `DELETE /devices/:id` |
| Catalog | `GET /home`, `/movies`, `/movies/:id`, `/series`, `/series/:id`, `/series/:id/seasons`, `/episodes/:id`, `/search` |
| Playback | `POST /playback/resolve` |
| Admin | `POST /admin/auth/login`, `POST/GET /admin/vip/keys`, `POST /admin/vip/keys/:id/revoke`, `GET /admin/users`, `GET /admin/users/:id/devices`, `DELETE /admin/devices/:id` |

Send `X-Profile-Id` to browse as a specific profile. Maturity limits are enforced by the server for that profile.

## Security notes

- VIP keys are stored only as HMAC-SHA256 hashes (with `VIP_KEY_PEPPER`). Raw keys appear once, in the admin generation response.
- Playback URLs are signed with `exp` and `sig` query parameters (`PLAYBACK_URL_TTL_SEC`, 5 to 30 minutes). The CDN or origin must verify them with `PLAYBACK_SIGNING_SECRET` (see `verifyPlaybackSignature`). For HLS, segment URLs inside a manifest need path-prefix tokens at the CDN, which this API does not generate.
- Only sources marked `authorized` are ever returned.
- Refresh tokens rotate. Presenting a rotated token revokes that device's sessions.
- Access tokens are rejected as soon as their device is revoked.

## Not implemented yet

12-word recovery (`/auth/recover`), Google sign-in, admin MFA, content management and homepage CMS endpoints, source health checker, `/playback/session` and progress, history and watchlist routes, account deletion and export.
