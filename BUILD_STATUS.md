# BUILD_STATUS — Team Timesheet

**What this is.** The live map of what's built and the first thing a cold session reads.
Derived from the epic map ([07](docs/design/07-epic-map.md)); maintained live during the build.

**Source-of-truth index.**
- Design artifacts (what the system *is* & why) → [docs/design/](docs/design/) — current, curated.
- Story plans (why each piece was built that way) → `docs/plans/` *(created per story; immutable, point-in-time)*.
- Commits (what literally changed) → git history — complete but unranked; consult only when the curated record has a hole.

**On session start:** read this, then verify against the code — **code wins**; flag mismatches.

---

## Story table — MVP epic: *Log & see team time*

Key: ⬜ not started · 🔄 in progress · ✅ done · ⚠ blocked

| # | Story | Status | Plan | Commit |
|---|-------|--------|------|--------|
| 1 | Walking-skeleton base: schema + health | ✅ | [plan](docs/plans/story-1-walking-skeleton.md) | `ec07a60` |
| 2 | Auth: login + JWT filter + seeded users | ⬜ | — | — |
| 3 | Create a time entry | ⬜ | — | — |
| 4 | List entries (shared read, filterable) | ⬜ | — | — |
| 5 | Edit own entry (ownership enforced) | ⬜ | — | — |
| 6 | Delete own entry (ownership enforced) | ⬜ | — | — |
| 7 | Expo client: login → my entries → CRUD | ⬜ | — | — |
| 8 | Team feed (shared visibility view) | ⬜ | — | — |
| 9 | Deploy to prod (skeleton goes live) | ⬜ | — | — |

*(Deploy story 9 may be pulled forward after story 3 for an early thin skeleton — see 07.)*

## Off-epic ledger

_(Unplanned changes — a line each so small adjustments don't vanish. Starts empty.)_

| Date | Change | Why it wasn't a story | Commit |
|------|--------|-----------------------|--------|
| 2026-07-13 | Stack bumped from the plan's Java 21 / Spring Boot 3.x to **Java 25 / Spring Boot 4.1.0 / Testcontainers 2.0.5 / Gradle 9.6.1** | Local env has only JDK 17 & 25 (no 21); dev chose to align on Java 25 (needed SB 3.5+; landed on 3.5.16 first, since superseded). SB 4.x was then a **deliberate second move** — dev chose to absorb the 3→4 major upgrade now, while the codebase is 2 files, rather than let it compound later. Required: `spring-boot-starter-web`→`-webmvc` (deprecated rename) and `spring-boot-starter-test`→`-webmvc-test` (4.x split MockMvc out of the generic test starter; `@AutoConfigureMockMvc` moved package to `org.springframework.boot.webmvc.test.autoconfigure`). TC 1.x mis-parses Docker Engine 29's `/info` → 2.0.5. All version-compat/deliberate-upgrade, not scope. | `931f5b8` |
| 2026-07-13 | Added missing `spring-boot-starter-flyway` — SB 4.x split Flyway's Spring autoconfiguration into its own module; without it, migrations silently never ran (app boots, `/api/health` returns 200, zero tables exist, no error anywhere) | Invisible to the test suite (`SchemaMigrationTest` drives Flyway directly; `HealthEndpointTest` never checked for tables) — only caught by manually smoke-testing `bootRun` against the real `docker-compose` Postgres. Bug from the 4.1.0 move, not new scope. | `7d0f72d` |
| 2026-07-13 | `docker-compose.yml`: Postgres volume mount `/var/lib/postgresql/data` → `/var/lib/postgresql`; host port made env-overridable (`DATABASE_PORT`) | `postgres:18+` refuses to start against the old mount path; port var lets a dev dodge a local 5432 conflict. Bug found during `docker compose up` verification. | `ec07a60` |
