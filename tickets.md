# Tickets: Story 1 — Walking-skeleton base: schema + health

Bootstraps the backend: local Postgres with the Flyway schema migration (full constraint
set, verified by tests that attempt to violate the constraints), and a DB-readiness
health endpoint on top. Source:
[docs/plans/story-1-walking-skeleton.md](docs/plans/story-1-walking-skeleton.md), itself
elaborated from [docs/design/07-epic-map.md](docs/design/07-epic-map.md) Story 1 via a
`/grilling` session.

Work the **frontier**: any ticket whose blockers are all done. This chain is linear —
top to bottom.

---

## Datastore foundation: schema + constraints

**What to build:** A local, containerised Postgres instance plus the Flyway migration
that creates `users` and `time_entries` with the full column and constraint set from
the domain model — proven correct by tests that actually attempt to violate the
constraints. Verifiable entirely on its own: no HTTP surface needed to demo this ticket.

**Blocked by:** None — can start immediately.

- [x] `docker-compose.yml` at repo root runs a single Postgres service, image pinned to
      `postgres:18-alpine` (not `latest` — floating tags let local/CI/prod silently
      drift to different major versions). Credentials/DB name are env-driven, matching
      the `DATABASE_URL` shape the app will use to connect.
- [x] `docker compose up` brings the container to a healthy, connectable state.
      (Verified end-to-end; volume mount corrected to `/var/lib/postgresql` for pg18+.)
- [x] `V1__init.sql` creates `users`: `id` (bigint identity PK), `name`,
      `username varchar(50) NOT NULL` with a unique index on `lower(username)`,
      `password_hash`, `role varchar(20) NOT NULL CHECK (role IN ('member','admin'))`,
      `created_at timestamptz NOT NULL DEFAULT now()`.
- [x] `V1__init.sql` creates `time_entries`: `id` (bigint identity PK), `user_id` FK →
      `users.id`, `entry_date date NOT NULL`,
      `duration_min integer NOT NULL CHECK (duration_min > 0)` (no upper bound),
      `description varchar(500) NOT NULL`, `created_at`/`updated_at timestamptz NOT NULL
      DEFAULT now()`.
- [x] No `email` column anywhere — `username` is the sole login identifier (INV-5).
- [x] No JPA `@Entity` classes introduced in this ticket — migration only; entities are
      written in later stories against this schema.
- [x] Testcontainers-backed test (`postgres:18-alpine`, matching the pinned local
      version) runs the migration cleanly against a fresh container.
- [x] Testcontainers test: a raw-JDBC insert with `duration_min <= 0` is rejected by the
      DB (proves INV-3 is enforced at the schema level, not just assumed).
- [x] Testcontainers test: a raw-JDBC insert of a duplicate username differing only in
      case (e.g. `jsmith` then `JSmith`) is rejected by the unique `lower(username)`
      index (proves INV-5's case-insensitivity is enforced at the schema level).

---

## Health endpoint proves DB connectivity

**What to build:** `GET /api/health`, reachable once the app boots, that proves the app
can actually talk to Postgres — not just that the process started.

**Blocked by:** Datastore foundation: schema + constraints.

- [x] `GET /api/health` returns `200 {"status":"ok"}` when the database is reachable.
- [x] The endpoint executes a live `SELECT 1` via `JdbcTemplate` on each call (DB-readiness,
      not liveness-only) — hand-rolled, not Spring Boot Actuator.
- [x] App boots via Flyway running the migration from the prior ticket automatically on
      startup against `DATABASE_URL`.
- [x] `@SpringBootTest` + Testcontainers integration test asserts the `200` status and
      exact response body shape.
- [x] No JPA `@Entity` classes introduced (`JdbcTemplate`/`data-jdbc` wiring only, per
      the Story 1 plan's scope boundary — no auth, no entry/user endpoints, no client).
