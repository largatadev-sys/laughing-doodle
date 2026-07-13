# Story 9 — Deploy to prod (skeleton goes live)

**Status.** Planned, Phase 1 (local full-stack gate) implemented this session; Phase 2
(Railway deploy) prepared, executed interactively with the developer. Immutable once
implementation lands — see [BUILD_STATUS.md](../../BUILD_STATUS.md) for live status.

**Source.** [07-epic-map.md](../design/07-epic-map.md) Story 9, elaborated through a
`/grill-with-docs` session on 2026-07-14. This file records *why* each choice was made; the
epic map records *what* is required. The session reversed/refined two committed ADRs — the
architectural decisions were put to the developer directly and are recorded as **ADR-008**
([04-architecture.md](../design/04-architecture.md)); implementation-detail decisions were
made by the agent and recorded here.

---

## Scope

Make the walking skeleton deployable and deploy it. Two phases:

- **Phase 1 — local full-stack parity gate.** A multi-stage `Dockerfile` bundles the Expo
  **web** export into the Spring Boot image; `docker compose --profile fullstack up --build`
  runs that image (web at `/` + API at `/api`) next to Postgres. This *is* the developer's
  "deployable containerized full stack in local Docker," and it is the exact artifact prod runs.
- **Phase 2 — prod on Railway.** The same image + Railway managed Postgres, single origin over
  HTTPS, secrets in Railway's env UI.

No CI/CD, no monitoring (epic scope boundary). Daily dev stays native (`bootRun` + Expo host).

## The through-line: parity via the artifact, not via a Dockerised dev loop

The developer's motivation is dev/prod parity. Parity is delivered by the **artifact** — the
compose full stack is byte-for-byte the image Railway runs — so the daily edit-run loop stays
native (fast), and Docker is reserved as an on-demand **parity gate** run before deploying.
This front-loads ADR-006's named invalidator ("deploy-time works-locally-breaks-in-prod")
into a gate we clear on purpose. See ADR-008.

## Decisions put to the developer directly (→ ADR-008)

1. **Single-origin bundled deploy — Spring serves the web.** Chosen over the split topology
   (web on Vercel/Netlify, API on Railway) that Story 9 originally described. Rationale: for a
   4-user internal authed tool it is the least infrastructure and it *removes* two foot-guns —
   **CORS** and the **build-time-baked API URL** — rather than managing them. The web calls a
   **relative** `/api`, so the same image works locally and in prod. Tradeoff accepted:
   frontend+backend deploys are coupled (fine at this scale). Alternative (Vercel split)
   remains the documented invalidator path if independent frontend deploys/preview deploys
   ever matter.
2. **Two-mode local containerization** (native daily loop + full-stack parity gate), refining
   ADR-006 rather than superseding it — the fast native loop it protects is retained.
3. **`SecurityConfig` change approved.** `/api/**` stays authenticated; static assets become
   public; **INV-2 (author-only writes) is untouched** — the ownership check stays in the
   entries service, the JWT filter is unchanged. The only broadening is making the public
   client bundle reachable before login (it holds no secrets and enforces nothing).
4. **Seeded passwords rotated as the first prod step.** [V2 seed](../../src/main/resources/db/migration/V2__seed_users.sql)
   ships a *public* BCrypt hash of `changeme123`; in prod that is a real, publicly-known
   credential. Per ADR-002's admin-reset mechanism, the first prod action is a manual DB
   update of all 4 passwords, before the URL is shared. Accepted over the heavier
   alternative (env-aware seeding) at this scale.

## Decisions made this session (implementation detail, agent-decided)

1. **`web.output: "single"` (SPA) instead of `"static"`.** Found in the Expo v57 docs:
   `static` emits per-route HTML and expects the host to rewrite extensionless paths
   (`/login`→`login.html`) — awkward from Spring. `single` emits one `index.html` + JS under
   `_expo/`, so a single `/*→index.html` fallback serves every route uniformly, including the
   dynamic `[id]/edit` route. SEO/SSR is irrelevant for an internal authed tool. Only affects
   the web *export*; native and the web dev server are unaffected.
2. **SPA fallback = a `WebMvcConfigurer` with a custom `PathResourceResolver`.** Serves an
   existing static file if present; otherwise returns `index.html` — **except** for paths
   starting `api/` (let them 404/401, not return HTML) and paths that look like a missing
   asset with a file extension (404, so a broken bundle reference is visible, not masked by
   HTML). Controller mappings already take precedence over the static resource handler, so
   `/api/**` reaches its controller normally.
3. **Multi-stage `Dockerfile`, three stages.** `node:22-alpine` (Node ≥ 22.13 per Expo 57)
   runs `npx expo export --platform web --output-dir dist` with `EXPO_PUBLIC_API_URL=""`
   (→ relative `/api`); `eclipse-temurin:25-jdk` copies the web export into
   `src/main/resources/static/` then runs `./gradlew bootJar -x test`; `eclipse-temurin:25-jre`
   runs the jar as a **non-root** user, binding `${PORT:8080}`. Tests are skipped in the image
   build (Testcontainers needs a Docker daemon; tests run in the dev loop, not the image).
4. **`server.port=${PORT:8080}`.** Railway (like most PaaS) injects `PORT` and routes to it;
   without this the app binds 8080 and gets no traffic. Defaults to 8080 locally.
5. **DB creds: compose points the app at the `postgres` service host; Railway composes a JDBC
   URL from its `PG*` vars.** Railway's `DATABASE_URL` is URI-form (`postgresql://…`), which
   Spring's `spring.datasource.url` (JDBC-form) rejects — so prod sets
   `DATABASE_URL=jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}` + user/password from
   `PGUSER`/`PGPASSWORD`. Verified live at deploy time (per the repo's standing lesson: trust
   the running app, not the green test suite).
6. **`app` service hidden behind a compose `profiles: ["fullstack"]`.** So the existing
   `docker compose up postgres` daily-loop command is untouched; the gate is an explicit
   `docker compose --profile fullstack up --build`.
7. **`apiClient` BASE_URL is relative-safe:** `process.env.EXPO_PUBLIC_API_URL ?? ''`. Empty/
   unset → relative `/api` (prod, same origin); `http://localhost:8080` in `client/.env` →
   absolute (dev web server, cross-origin, covered by the existing `localhost:*` CORS pattern);
   LAN IP for Expo Go. One line, avoids a `"undefined/api"` bug when the var is unset.
8. **CORS left at its default** (`http://localhost:*`). In prod the web is same-origin so CORS
   never triggers; native fetch sends no `Origin`. Harmless either way; not tightened.

## Deliverables

- `Dockerfile` (new) — three-stage build (node → jdk/gradle → jre).
- `.dockerignore` (new) — exclude `node_modules`, `build/`, `.gradle`, `.git`, `.env`, etc.
- `docker-compose.yml` — add `app` service behind the `fullstack` profile, wired to `postgres`.
- `src/main/resources/application.properties` — `server.port=${PORT:8080}`.
- `src/main/java/.../auth/SecurityConfig.java` — matcher change (API authenticated, static public).
- `src/main/java/.../web/SpaWebConfig.java` (new) — SPA fallback resource resolver.
- `src/main/java/.../error/GlobalExceptionHandler.java` — map Spring's `NoResourceFoundException`
  to a clean **404** (envelope `NOT_FOUND`), not the catch-all's 500. A missing static asset now
  reaches the resource handler (it couldn't before — non-API paths were `401`); found during the
  gate's live verification (`GET /nope.js` returned 500). Also avoids error-log spam on stray asset
  requests (source maps, `/robots.txt`, etc.).
- `.gitignore` — ignore `src/main/resources/static/` (only ever populated inside the image build).
- `client/app.json` — `web.output`: `static` → `single`.
- `client/src/lib/apiClient.ts` — relative-safe `BASE_URL`.
- `client/.env.example` — document dev-absolute vs prod-relative.
- `.env.example` — note the env surface used by the compose `app` service.
- `docs/deploy/railway.md` (new) — Phase 2 step-by-step checklist.
- `scripts/smoke.sh` (new) — runnable smoke test for the local gate **and** prod; includes the
  proxy-CORS check below.

## Post-deploy fix — CORS behind Railway's TLS proxy (found on prod)

The first prod deploy booted and served fine, but **browser login returned `403 "Invalid CORS
request"`** (curl didn't — no browser `Origin` header). Root cause: Railway terminates TLS and
forwards over http; browsers send `Origin` on same-origin POSTs; Spring, not honoring
`X-Forwarded-Proto`, saw its own scheme as `http` vs the `https` Origin → treated a *same-origin*
request as cross-origin → checked it against `CORS_ALLOWED_ORIGINS` (`localhost:*`) → rejected.

- **Fix:** `server.forward-headers-strategy=framework` in `application.properties` — Spring honors
  `X-Forwarded-Proto/Host`, sees itself as `https://…railway.app`, matches the Origin, and skips
  CORS for the (correctly recognized) same-origin request. Verified locally that a genuine
  cross-origin request is *still* rejected — CORS is not weakened.
- **Why the gate missed it:** the local gate had no TLS-terminating proxy, so scheme always
  matched and the bug was invisible — the classic "only the real deploy surfaces it" (this repo's
  recurring lesson). **Gate hardening:** `scripts/smoke.sh` now *simulates* the proxy
  (`Origin: https` + `X-Forwarded-Proto: https`) so this class of bug is caught locally before
  deploy. Run `scripts/smoke.sh` against the gate before every deploy.

## Manual verification

**Phase 1 (local gate):**
- `docker compose --profile fullstack up --build` → app + Postgres come up healthy.
- Open `http://localhost:8080` → the **web app** is served by Spring (not the Expo dev server).
- Log in as a seeded user → token stored → `GET /api/entries` succeeds (same origin, no CORS).
- Create / edit / delete an entry, and open the Team Feed → all work end-to-end.
- A hard refresh on `/team` and on an edit route (`/<id>/edit`) serves the app (SPA fallback),
  not a 404.
- Confirm the daily loop is untouched: `docker compose up postgres` + `./gradlew bootRun` +
  `npm run web` still works as before.

**Phase 2 (Railway, with the developer):** AC-1 prod URL serves the app; login + CRUD over
HTTPS. AC-2 no secrets in the repo; `JWT_SECRET` + DB creds only in Railway's env UI;
seeded passwords rotated before sharing the URL.

## Explicitly out of scope (do not build)

CI/CD pipeline, monitoring/observability stack, a Dockerised daily dev loop, independent
frontend hosting/CDN, preview deploys, any change to the entries/auth *logic* (only the auth
*matcher* and `server.port` change), native app store distribution, backups/DR automation.
