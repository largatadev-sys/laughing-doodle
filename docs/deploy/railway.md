# Deploying to Railway — Story 9, Phase 2

The prod target is **one** Railway service (the bundled image from [`Dockerfile`](../../Dockerfile),
serving the Expo web app at `/` and the API at `/api`) plus a **Railway PostgreSQL** service,
single origin over HTTPS (ADR-008). This is the *same image* the local parity gate runs
(`docker compose --profile fullstack up --build`), so if the gate is green, this should deploy clean.

> Do this **with the developer** — it needs the Railway account and sets real secrets. Nothing
> below puts a secret in the repo (AC-2).

## 0. Prerequisites

- A Railway account and this repo pushed to GitHub (Railway deploys from a connected repo),
  **or** the Railway CLI (`railway`) installed and logged in.
- The branch you want to deploy is pushed (this story's work merged to `dev`/`main`).

## 1. Create the project + database

1. New Project → **Deploy PostgreSQL** (or add a Postgres service to an empty project).
   Railway provisions managed Postgres and exposes `PGHOST`, `PGPORT`, `PGUSER`,
   `PGPASSWORD`, `PGDATABASE`, `DATABASE_URL` (URI form) on that service.

## 2. Add the app service

2. **New Service → GitHub repo** (this repo/branch), or `railway up` from the repo root.
3. Railway auto-detects the root `Dockerfile` and builds it. (If it doesn't, set the service's
   **Builder → Dockerfile** explicitly.) No build command / start command needed — the image's
   `ENTRYPOINT` runs the jar.

## 3. Wire the app service's variables

Set these on the **app** service (Variables tab). Use Railway **reference variables** so the
DB creds resolve from the Postgres service at deploy time:

| Variable | Value | Why |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` | Spring needs **JDBC** form; Railway's own `DATABASE_URL` is URI form (`postgresql://…`) and won't work in `spring.datasource.url`. |
| `DATABASE_USER` | `${{Postgres.PGUSER}}` | |
| `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` | |
| `JWT_SECRET` | *a real 256-bit secret* (e.g. `openssl rand -base64 48`) | **Never** the repo's dev placeholder. Set here only. |
| `JWT_TTL_DAYS` | `7` | Optional (defaults to 7). |

- **Do NOT set `PORT`** — Railway injects it and the app already binds `${PORT:8080}`.
- `CORS_ALLOWED_ORIGINS` — leave unset. The web is same-origin in prod, so CORS never triggers.
- Prefer the **private** DB host (`${{Postgres.PGHOST}}` resolves to the internal
  `*.railway.internal` host within the project) — no public egress. Confirm in the deploy logs.

## 4. Networking + health

4. App service → **Settings → Networking → Generate Domain**. Railway gives an
   `https://…up.railway.app` URL with TLS terminated at its edge (satisfies AC-1's HTTPS).
5. *(Recommended)* **Settings → Healthcheck Path → `/api/health`** so a deploy only goes live
   once the app answers. (`/api/health` does a live `SELECT 1`, proving DB connectivity.)

## 5. Deploy and verify the migration ran

6. Trigger the deploy. **Watch the logs** for Flyway applying `V1__init` and `V2__seed_users`.
   This repo has a standing lesson: a green test suite once hid a silently-not-running Flyway.
   Confirm in the logs that both migrations applied, or connect and check:
   ```
   # Railway → Postgres service → Connect (psql), then:
   \dt              -- expect: users, time_entries, flyway_schema_history
   select username, role from users;   -- expect the 4 seeded users
   ```

## 6. Rotate the seeded passwords (AC-2, required before sharing the URL)

The seeded users ship a **public** BCrypt hash of `changeme123`
([V2](../../src/main/resources/db/migration/V2__seed_users.sql)) — a real credential in prod.
Rotate all four before anyone gets the URL (ADR-002's admin-reset mechanism):

```sql
-- Connect to the Railway Postgres (psql), then per user:
UPDATE users SET password_hash = '<new-bcrypt-hash>' WHERE username = 'admin';
-- ...repeat for member1, member2, member3
```

Generate a BCrypt hash **locally** (never paste a real password into a web tool):

```bash
# Option A — Apache htpasswd (bcrypt, cost 10). Spring accepts $2a/$2b/$2y.
htpasswd -bnBC 10 "" 'the-real-password' | tr -d ':\n'

# Option B — reuse the app's own encoder (a throwaway Gradle/JBang/jshell snippet):
#   new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("the-real-password")
```

## 7. Smoke test (AC-1)

- Open the `https://…up.railway.app` domain → the **web app** loads (served by Spring).
- Log in with a rotated credential → `GET /api/entries` succeeds (same origin, no CORS).
- Create / edit / delete an entry and open the Team Feed → all work end-to-end over HTTPS.
- Hard-refresh `/team` and an edit route → the app loads (SPA fallback), not a 404.

## Rollback / redeploy

- Railway keeps deploy history — roll back to a previous deploy from the service's Deployments tab.
- A frontend or backend change is one redeploy of the same image (they're coupled by design, ADR-008).

## Out of scope (deferred per Story 9)

CI/CD (auto-deploy on push can be toggled in Railway later), monitoring/observability,
backups/DR automation, a separate staging environment.
