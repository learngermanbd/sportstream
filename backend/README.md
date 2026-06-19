# SportStream Admin Backend

Phase 8 / Step 8.2 - 8.3 base skeleton for the REST API that powers the
SportStream admin panel web + Android plus the user's `/api/config` and
`/api/*` reads.

## Stack

- **Runtime**: Node.js 18+
- **Framework**: Express 4
- **Database**: PostgreSQL via Prisma 5 (Supabase in prod)
- **Auth**: JWT (`jsonwebtoken`) + bcrypt hashes (`bcryptjs`)
- **Security**: helmet + cors + morgan; RBAC, refresh tokens and rate limiting land in Steps 8.3+ / 7.x

## Quick start

```bash
cd backend
cp .env.example .env             # then fill in DATABASE_URL + JWT_SECRET
npm install                      # resolves express, prisma, bcrypt, etc.
node --check src/server.js       # fast syntax check (no exec)
npx prisma generate              # generates @prisma/client (needs DATABASE_URL)
npm run dev                      # boots on :3000
curl http://localhost:3000/api/health
```

## Endpoints (Step 8.2 minimum skeleton)

| Method | Path                          | Auth | Notes                                          |
|--------|-------------------------------|------|------------------------------------------------|
| GET    | `/api/health`                 | -    | Liveness probe. Returns uptime + version.      |
| POST   | `/api/admin/auth/login`       | -    | **Dev mode**: accepts any email/password, returns mocked JWT. Real bcrypt + DB lookup lands Step 8.3. |
| GET    | `/api/events`                 | -    | Stub: `{events: []}` until Step 8.6 ships full CRUD. |

## Architecture (planned per sportzfy_build_plan Phase 8)

- **Step 8.2** - Prisma schema (10 models): `Admin`, `Event`, `Channel`, `Highlight`, `Category`, `Banner`, `StreamLink`, `AppConfig`, `Notification`, `AnalyticsEvent`.
- **Step 8.3** - Full routes + RBAC (SUPER_ADMIN/EDITOR/VIEWER) + Zod validation + helmet/cors/rateLimit + multer file uploads to Supabase Storage.
- **Step 8.4 - 8.9** - Web admin frontend (login, dashboard, events manager, channels, highlights, notifications composer, app config, analytics).
- **Step 8.10** - Mobile (user) `ApiService` integration so the user app consumes the same REST API.
- **Step 8.13 - 8.17** - Separate Android admin app (see `admin/`).
- **Step 8.18** - FCM notifications targeting + scheduling via `node-cron`.

## Why both `admin/` and `backend/`?

- **`admin/`** - Android APK installed on admins' phones for on-the-go content edits.
- **`backend/`** - This Node.js service. Runs on Render free tier in dev, Railway $5/mo in prod.

Both share the `/api/*` contract. The user Android app talks to `backend/` directly; the admin web frontend + admin app talk to `backend/` after authenticating.
