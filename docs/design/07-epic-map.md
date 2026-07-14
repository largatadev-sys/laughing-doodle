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

## Epic 2: **Largata UX & mobile visual identity**

Post-MVP. The MVP epic proved the slice works end-to-end; this epic makes it feel like a
product and, specifically, like a **Largata** product. Two experience shifts requested by the
developer: the user's home becomes a **social-style activity feed** of the team's logged work,
and the team view becomes an **Outlook-style calendar** (week + month). Delivered against the
**Largata brand** (hot red chrome, rounded/pill surfaces, floating tab bar, multi-colour member
avatars) on a **mobile-native, phone-portrait** target.

Presentation-layer only — **no schema, auth, or INV-2 change.** A wider slice than the MVP's
security stories is acceptable here precisely because the risk surface is presentation, not the
one security surface (INV-2), which is untouched.

### Story 10 — Largata redesign: brand system + tab shell + feed + calendar

- **Context anchor.** Epic *Largata UX* · reskins & restructures the existing client
  (Stories 7a/7b/8) · consumes the same API (Story 4 `GET /api/entries?from=&to=&userId=`) —
  **no new endpoints.** Honest to the domain: entries are date-only + duration (INV-4), so the
  calendar renders **duration-as-load**, never an invented clock-time grid.
- **Vertical slice.** A shared design-token system (colour/type/space) + Plus Jakarta Sans →
  a headless `expo-router/ui` **floating red pill tab bar** (Home · Calendar · centre ＋ compose ·
  Profile) → **Home** = team activity feed (compose header, per-week meter, day-grouped cards
  with per-person avatars + tally bars + hand-drawn day connectors) → **Calendar** = month grid
  + week (7 stacked day-loads) + a **day-detail** agenda drill-in → **Profile** = my entries +
  log out. Login and the create/edit forms are restyled to match.
- **Acceptance criteria (= UI checks; manual per 06b — no automated e2e).**
  - AC-1 Bottom tab bar switches Home/Calendar/Profile; the centre ＋ opens the compose form
    over the tabs; the bar respects the safe-area inset and is thumb-reachable.
  - AC-2 Home shows the team's recent entries grouped by day, each with the author's avatar
    (deterministic per-person colour), a friendly duration, and relative time; my own entries
    expose edit/delete, others' do not (INV-2 read/write split intact in the UI).
  - AC-3 Calendar offers **week** and **month**; today is marked; each day shows its load; tapping
    a day opens that day's entries. No clock-time grid is shown (none exists in the data).
  - AC-4 Every screen (login, forms, rows) wears the Largata identity; runs phone-portrait,
    fonts load behind the splash, reduced-motion respected, ≥44px touch targets.
- **Scope boundary — do NOT touch.** No schema/API change; no reactions/comments (would need a
  new table — deferred); no projects/tags; no desktop/wide layout; INV-2 logic unchanged.
- **Fits one window?** Delivered as one branch/one squash commit; wider than an MVP story by
  design (presentation risk only).

### Story 11 — Self-service account: change name + password

- **Context anchor.** Auth domain (developer-approved despite the auth stop-rule). First slice of
  the backlog "User management UI" epic, pulled forward on request. **No SecurityConfig / JWT /
  INV-2 change** — the endpoints fall under the existing `/api/**` authenticated rule.
- **Vertical slice.** `PUT /api/auth/password {currentPassword, newPassword}` — verifies the
  current password, enforces new ≥ 8 chars and different from current, re-hashes (BCrypt), updates
  the token user's `password_hash`. `PUT /api/auth/name {name}` — trims + validates (1–100 chars,
  matches `users.name`), updates the display name, returns the updated profile so the client
  refreshes its session/header in place. "Change name" + "Change password" screens from Profile.
- **Acceptance criteria (= tests).**
  - AC-1 Valid change → `204`; the new password logs in, the old no longer does.
  - AC-2 Wrong current password → **`400 VALIDATION_FAILED`** (field `currentPassword`), *not* 401
    — a 401 would trip the client into logging the user out mid-change. Password unchanged.
  - AC-3 New password too short or same as current → `400` (field `newPassword`).
  - AC-4 No token → `401`.
- **Scope boundary — do NOT touch.** Identity from the token, never the body (INV-1 spirit). No
  admin reset of others' passwords (deferred); no session/token revocation (stateless JWT stays
  valid till expiry — noted).
- **Fits one window?** Yes.

### Story 12 — Entry activity label: "logged / edited · when"

- **Context anchor.** Presentation refinement to the shared `EntryCard` (Stories 8/10). **Client
  only — no schema/API/auth/INV-2 change** (`EntryResponse` already returns `createdAt` + `updatedAt`).
  Reinforces the two-axis model (INV-4): `entryDate` is the stable work day; `createdAt`/`updatedAt`
  are the log/edit *instants*.
- **Vertical slice.** The card caption reads `logged · <when>` until an entry is edited, then
  `edited · <when>` keyed to `updatedAt`. `<when>` is relative ("22m ago"/"5h ago") only when the
  action falls on the **viewer's** local calendar day; on any other day it shows the **date**
  ("Jul 3" / "Jul 3, 2025"), never "N days ago". List order stays by `createdAt` (an edit must not
  reorder rows).
- **Acceptance criteria (= UI checks; manual per 06b + a `datetime` logic repro).**
  - AC-1 Unedited entry → `logged · <when>`; once edited → `edited · <when>` (from `updatedAt`).
  - AC-2 Same-viewer-day action → relative; any other day → the date, never "Nd ago"; future clock
    skew degrades to "just now".
  - AC-3 Order unchanged (still `createdAt` desc); editing a row does not move it.
- **Scope boundary — do NOT touch.** No second/paired timestamp, no "edited" badge/colour (caption
  stays quiet — the tally bar is the card's signature), no backend/sort-order change.
- **Fits one window?** Yes. Design rationale + spec: [plan](../plans/story-12-entry-activity-label.md).

---

## Backlog epics (placeholders — post-validation, signal-driven)

- **Projects** — optional `project_id` on entries + admin project management (additive; ADR-007).
- **Reporting & aggregation** — per-person / per-project rollups over periods.
- **Approvals** — if the team ever wants a submit → review flow (adds a `status` state machine).
- **Attachments** — photos & files on an entry (upload + storage), plus an **activity-detail
  modal**: tap an entry to open a modal showing the full description, metadata, and its attached
  photos/files. Needs a `time_entry_attachments` table + object storage + upload endpoints; the
  detail modal is the client surface. Requested 2026-07-14.
- **Pagination, virtualization & scale (the feed at volume)** — _current behaviour, noted
  2026-07-14:_ the Home feed loads a **fixed 14-day window** in one request (won't ever load a
  year — but also can't show older on Home); the **Calendar** loads per week/month; **Profile
  loads a user's entire history unbounded**, and `GET /api/entries` has **no server pagination or
  LIMIT** (Story 4 scope call). The client also renders whole lists in a plain scroll container
  (no virtualization — `FlatList` was swapped out in Story 10 for layout control). Fine for one
  team + weeks of data; **before real historical volume (a year+) or a second team**, add: cursor
  pagination (`?before=&limit=`) on the list endpoint → client "load older" / infinite scroll →
  list virtualization. Trigger is data volume, not the calendar.
- **User management UI** — admin creates/deactivates users, resets passwords in-app.
- **Multi-tenancy** — only if a second team ever needs isolation (ADR-004 invalidator).
- **Offline & sync** — local queue + conflict resolution (large; only if connectivity hurts).
- **Native app distribution** — build/ship the mobile app beyond local practice.
- **Observability & hardening** — metrics, tracing, rate limiting, backups/DR (first post-validation work).

## Rough sequence / dependencies

`1 → 2 → 3 → 4 → 5 → 6` (backend vertical), then `7a → 7b → 8` (client), then `9` (deploy).
Deploy (9) can be pulled forward right after Story 3 to get a *thin* skeleton live early
(the highest-leverage move) and re-deployed as stories land.
