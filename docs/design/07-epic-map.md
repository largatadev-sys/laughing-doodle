# 07 — Epic Map  `[MVP-THIN — MVP epic detailed, rest are placeholders]`

Epics are the stable map; stories are elaborated just-in-time. Only the MVP epic's
stories are detailed (to agent-ready depth, Section 5). Everything else is a placeholder.

---

## MVP epic: **Log & see team time**

The hypothesis-testing slice: a Member can log their own work and see the whole team's —
end to end, deployed. This *is* the walking skeleton.

Each story is a **vertical slice**, has **acceptance criteria that map 1:1 to tests**, an
explicit **scope boundary**, and **fits one context window**.

---

### Story 1 — Walking-skeleton base: schema + health

- **Context anchor.** Epic *Log & see team time* · bootstraps the whole backend · no prior patterns (first story).
- **Vertical slice.** Spring Boot app boots → connects to Postgres (containerised locally)
  → Flyway migration creates `users` and `time_entries` → `GET /api/health` pings the DB
  and returns 200.
- **Acceptance criteria (= tests).**
  - AC-1 App starts and `GET /api/health` → `200 {"status":"ok"}` (endpoint does a live
    `SELECT 1`, proving DB connectivity, not just that the process is up).
  - AC-2 Flyway migration creates both tables with the columns from [02](02-domain-model.md):
    `bigint` identity PKs; `users.username varchar(50) NOT NULL` with a unique index on
    `lower(username)`, forced lowercase on write; `users.role varchar(20) NOT NULL CHECK
    (role IN ('member','admin'))`; `time_entries.duration_min integer NOT NULL CHECK
    (duration_min > 0)` (no upper bound); `time_entries.description varchar(500) NOT NULL`;
    `entry_date date NOT NULL`; `created_at`/`updated_at timestamptz NOT NULL DEFAULT now()`.
  - AC-3 `docker compose up` brings up Postgres (`postgres:18-alpine`, pinned); app connects
    via `DATABASE_URL`.
- **Scope boundary — do NOT touch.** No auth, no entry/user endpoints, no client, no JPA
  `@Entity` classes (Flyway + `JdbcTemplate` only — entities are written in Stories 2/3
  against this migration). No `email` column — login identifier is `username` (see
  [02](02-domain-model.md) INV-5).
- **Test infra.** Testcontainers (real ephemeral Postgres, matching the pinned local/CI
  version); one integration test class asserting app boot, `/api/health` → `200`, and that
  the `duration_min > 0` and unique-`lower(username)` constraints actually reject bad
  inserts via raw JDBC.
- **Fits one window?** Yes.

### Story 2 — Auth: login + JWT filter + seeded users

- **Context anchor.** Follows [04](04-architecture.md) ADR-002, [06b](06b-engineering-decisions.md) auth model.
- **Vertical slice.** `POST /api/auth/login` verifies username + BCrypt password → returns a
  signed JWT + user; a Spring Security filter validates the bearer token on all other
  routes and populates the security context. Seed the 4 users (one admin) via migration/seeder.
- **Acceptance criteria (= tests).**
  - AC-1 Valid credentials → `200 {token, user{id,name,username,role}}`.
  - AC-2 Wrong password / unknown username → `401 UNAUTHENTICATED` (envelope shape).
  - AC-3 A request to a protected route with no/invalid/expired token → `401`.
  - AC-4 Passwords are stored BCrypt-hashed; never returned in any response; never logged.
- **Scope boundary — do NOT touch.** No entry endpoints; no self-service reset; no lockout.
- **Fits one window?** Yes.

### Story 3 — Create a time entry

- **Context anchor.** `entries` module · identity from token (INV-1) · [05](05-api-conventions.md).
- **Vertical slice.** `POST /api/entries {entryDate, durationMin, description}` → creates
  an entry **authored by the token's user** → `201` with the entry.
- **Acceptance criteria (= tests).**
  - AC-1 Authenticated create → `201`; `userId` equals the token's user, **even if the
    body tries to send a different `userId`** (it is ignored — INV-1).
  - AC-2 `durationMin <= 0` or missing required field → `400 VALIDATION_FAILED`.
  - AC-3 No token → `401`.
- **Scope boundary — do NOT touch.** No update/delete/list yet; no projects.
- **Fits one window?** Yes.

### Story 4 — List entries (shared read, filterable)

- **Context anchor.** `entries` module · shared visibility (INV-2 read side).
- **Vertical slice.** `GET /api/entries?from=&to=&userId=` → returns **all team members'**
  entries in range (optional `userId` filter), each with author name.
- **Acceptance criteria (= tests).**
  - AC-1 Returns entries from *all* users (not just the caller) → `200` array.
  - AC-2 Empty range → `200 []` (never 404).
  - AC-3 `from`/`to` filter by `entry_date`; `userId` filters to one author.
- **Scope boundary — do NOT touch.** No aggregation/grouping logic (that's Story 8); no pagination.
- **Fits one window?** Yes.

### Story 5 — Edit own entry (ownership enforced)

- **Context anchor.** INV-2 write side — **the security-critical story.** Test depth: this
  is the mandatory-coverage path from [06b](06b-engineering-decisions.md).
- **Vertical slice.** `PUT /api/entries/{id}` → updates the entry **only if the token's
  user is its author**, else `403`.
- **Acceptance criteria (= tests).**
  - AC-1 Author edits own entry → `200` with updated fields; `updated_at` changes.
  - AC-2 **A different user edits someone else's entry → `403 FORBIDDEN`; the entry is
    unchanged.** (IDOR defense.)
  - AC-3 Unknown id → `404`.
- **Scope boundary — do NOT touch.** No delete; no changing authorship.
- **Fits one window?** Yes.

### Story 6 — Delete own entry (ownership enforced)

- **Context anchor.** INV-2 write side; same ownership pattern as Story 5.
- **Vertical slice.** `DELETE /api/entries/{id}` → deletes only if the token's user is the author.
- **Acceptance criteria (= tests).**
  - AC-1 Author deletes own entry → `204`; it's gone.
  - AC-2 **Different user → `403`; entry still present.** (IDOR defense.)
  - AC-3 Already-absent id → `204` (idempotent) *or* `404` if not previously owned — pick
    per [05](05-api-conventions.md) DELETE rule (idempotent `204`).
- **Scope boundary — do NOT touch.** No cascade beyond the single entry.
- **Fits one window?** Yes.

### Story 7a — Expo client: scaffold + login + my-entries list

- **Context anchor.** Single Expo codebase (ADR-001); web is the real target. Consumes
  stories 2 (login) and 4 (list, filtered to the caller via `?userId=`). First story to
  touch the client — also stands up the Expo project itself (routing, API client, token
  storage) that Story 7b builds on.
- **Vertical slice.** Expo/TypeScript project scaffolded → login screen (stores JWT) →
  read-only list of my own entries (`GET /api/entries?userId=<me>`). Runs on web (primary)
  and native (dev).
- **Acceptance criteria (= tests, manual/UI for MVP — no automated e2e per 06b).**
  - AC-1 Log in on web → token stored → authenticated calls succeed.
  - AC-2 List shows only the caller's own entries, pulled live from the API.
  - AC-3 An expired/invalid token on any call clears the stored token and returns to login.
  - AC-4 Runs as a web build and in Expo native dev from the same code.
- **Scope boundary — do NOT touch.** No create/edit/delete UI (Story 7b); no team-feed
  grouping (Story 8); no offline.
- **Fits one window?** Yes (split from the original combined Story 7 for this reason —
  see [plan](../plans/story-7a-expo-scaffold-login-list.md)).

### Story 7b — Expo client: create/edit/delete my entries

- **Context anchor.** Builds directly on 7a's scaffold, API client, and auth/token
  plumbing. Consumes stories 3 (create), 5 (edit), 6 (delete).
- **Vertical slice.** Form to create an entry → edit/delete controls on entries I authored
  in the 7a list (others' entries stay read-only, per INV-2).
- **Acceptance criteria (= tests, manual/UI for MVP — no automated e2e per 06b).**
  - AC-1 Create my entry → appears in the list without a manual refresh.
  - AC-2 Edit my entry → list reflects the change.
  - AC-3 Delete my entry → it disappears from the list.
  - AC-4 No edit/delete affordance is shown for another user's entry.
- **Scope boundary — do NOT touch.** No team-feed grouping (Story 8); no offline.
- **Fits one window?** Yes.

### Story 8 — Team feed (shared visibility view)

- **Context anchor.** The payoff of shared visibility; consumes Story 4.
- **Vertical slice.** A screen showing the whole team's entries for a period, grouped by
  day (and/or by member) — read-only for others' entries.
- **Acceptance criteria (= tests, UI).**
  - AC-1 Shows all members' entries for the selected week, grouped by `entry_date`.
  - AC-2 Others' entries are read-only; only my own show edit/delete.
- **Scope boundary — do NOT touch.** No charts/reports; no per-project rollups (deferred).
- **Fits one window?** Yes.

### Story 9 — Deploy to prod (skeleton goes live)

- **Context anchor.** [04](04-architecture.md) deployment section + **ADR-008** (bundled
  single-origin); collapse dev→prod.
- **Vertical slice.** A multi-stage `Dockerfile` bundles the Expo web export into the Spring
  image; **one** container (web at `/` + API at `/api`) deploys to a PaaS (Railway) with
  managed Postgres, single origin over HTTPS; secrets via the platform's env UI. A local
  `docker compose --profile fullstack` gate runs the identical image before deploy.
- **Acceptance criteria.** *(unchanged)*
  - AC-1 Prod URL serves the web app; login + CRUD work end-to-end over HTTPS.
  - AC-2 No secrets in the repo; prod secrets set in the platform env UI.
- **Scope boundary — do NOT touch.** No CI/CD pipeline, no monitoring stack (deferred).
  Daily dev stays native (`bootRun` + Expo host); Docker is the parity gate, not the loop.
- **Fits one window?** Yes.

---

## Backlog epics (placeholders — post-validation, signal-driven)

- **Projects** — optional `project_id` on entries + admin project management (additive; ADR-007).
- **Reporting & aggregation** — per-person / per-project rollups over periods.
- **Approvals** — if the team ever wants a submit → review flow (adds a `status` state machine).
- **User management UI** — admin creates/deactivates users, resets passwords in-app.
- **Multi-tenancy** — only if a second team ever needs isolation (ADR-004 invalidator).
- **Offline & sync** — local queue + conflict resolution (large; only if connectivity hurts).
- **Native app distribution** — build/ship the mobile app beyond local practice.
- **Observability & hardening** — metrics, tracing, rate limiting, backups/DR (first post-validation work).

## Rough sequence / dependencies

`1 → 2 → 3 → 4 → 5 → 6` (backend vertical), then `7a → 7b → 8` (client), then `9` (deploy).
Deploy (9) can be pulled forward right after Story 3 to get a *thin* skeleton live early
(the highest-leverage move) and re-deployed as stories land.
