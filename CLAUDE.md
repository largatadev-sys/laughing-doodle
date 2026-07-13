# CLAUDE.md — Team Timesheet

Standing rules and pointers for every session. **Rules, not knowledge.** Knowledge lives
in the context package below; this file only _points_ at it. Keep under ~2 pages.

## Context-package index

- Requirements brief → [docs/design/00-requirements-brief.md](docs/design/00-requirements-brief.md)
- Intent & mode → [docs/design/01-intent-and-constraints.md](docs/design/01-intent-and-constraints.md)
- Domain model (invariants!) → [docs/design/02-domain-model.md](docs/design/02-domain-model.md)
- Tenancy (single-tenant) → [docs/design/03-tenancy-model.md](docs/design/03-tenancy-model.md)
- Architecture & ADRs → [docs/design/04-architecture.md](docs/design/04-architecture.md)
- API conventions → [docs/design/05-api-conventions.md](docs/design/05-api-conventions.md)
- Engineering principles (reused core, P1–P10) → [docs/design/06a-engineering-principles.md](docs/design/06a-engineering-principles.md)
- Engineering decisions (per-system + test depth!) → [docs/design/06b-engineering-decisions.md](docs/design/06b-engineering-decisions.md)
- Epic map & stories → [docs/design/07-epic-map.md](docs/design/07-epic-map.md)

## State — always maintain

- BUILD_STATUS → [BUILD_STATUS.md](BUILD_STATUS.md). Every story that lands updates its
  row (status + commit); every unplanned change gets a line in the off-epic ledger.
  A stale tracker misleads the next session with authority — keeping it current is **non-negotiable**.

## Agent skills

### Issue tracker

Local markdown under `docs/tickets/<feature-slug>/issues/`, not GitHub Issues despite the
`origin` remote. See [docs/agents/issue-tracker.md](docs/agents/issue-tracker.md).

### Triage labels

Defaults kept as-is (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`,
`wontfix`), recorded as a `Status:` line per ticket. See [docs/agents/triage-labels.md](docs/agents/triage-labels.md).

### Domain docs

Single-context; the existing `docs/design/` package (02-domain-model.md, 04-architecture.md's
ADR log) stands in for the generic `CONTEXT.md`/`docs/adr/` layout — no parallel structure.
See [docs/agents/domain.md](docs/agents/domain.md).

## Stop rules (ask before doing)

Ask the developer before: schema/migration changes · anything touching auth or the JWT
secret · changing the authorization/ownership logic (INV-2) · deleting data · anything
irreversible. The **single security surface is INV-2** (author-only writes, IDOR defense) —
never weaken it to make a test pass.

## Never commit secrets

- **Structural (the real defence):** `.env`, `.env.local`, and any file with credentials
  are **gitignored before the first commit**. Real secrets live only in gitignored files
  locally and in the platform's env UI in prod. The repo carries `.env.example` (placeholders only).
- **Backstop:** before every commit, scan the staged diff for `JWT_SECRET`, `PASSWORD=`,
  `API_KEY=`, tokens, long high-entropy strings, or any staged `.env`; refuse on a match.
- A committed secret stays in history even if later deleted — **rotate it.**

## Git workflow

`main` ← `dev` ← short-lived `feature/<story-id>-<slug>` branches. `dev` is **long-lived**:
every feature branch merges into `dev` first; `dev` promotes to `main` periodically (not
per-story) once its accumulated stories are verified working end-to-end. `main` is the
slower-moving, stable snapshot — treat it as what's actually deployable.

This is heavier than the collapsed single-`main` flow the mode dial would otherwise
suggest for a solo, pre-validation, no-CI/CD build (see [01](docs/design/01-intent-and-constraints.md)) —
kept anyway at the developer's explicit choice, as a standing staging checkpoint ahead of
a future second contributor or deploy pipeline. Revisit if `dev` never ends up catching
anything `main` wouldn't have caught directly.

Commit convention: `feat(scope): …` / `fix(scope): …`.

## Branches vs. worktrees

Feature branch off `dev` is the default (`git checkout -b feature/<story-id>-<slug> dev`).
Use a worktree only if genuinely running parallel stories; branch first, then check it out
into the worktree; remove the worktree once its branch merges into `dev`.

## Conventions quick-ref

- Layered Spring: controller (HTTP+validation) → service (logic + INV-2) → repository (persistence).
- Identity always from the JWT security context, **never** the request body.
- Every endpoint obeys [05](docs/design/05-api-conventions.md) (status codes + error envelope).
- Never log-and-throw; never log secrets/PII (P2/P3).
- Test the authorization paths (INV-2) at the API integration layer — mandatory ([06b](docs/design/06b-engineering-decisions.md); P8 test-ownership).
- Obey the reused principles [06a](docs/design/06a-engineering-principles.md) at this build's dial (see 06b); name and justify any pattern used (P9).

## Gotchas

- **Stack is Java 25 / Spring Boot 4.1.0 / Testcontainers 2.0.5 / Gradle 9.6.1** (not the
  design docs' "Java 21 / SB 3.x" — see BUILD_STATUS off-epic ledger; both the Java-25 move
  and the 3→4 major-version move were deliberate developer calls, made early while the
  codebase is small). Spring Boot < 3.5 can't scan Java 25 bytecode (ASM `ClassReader`
  throws) — 4.1.0 supports it too (system requirements: Java 17–26).
  **4.x breaking changes hit so far:** `spring-boot-starter-web` → `-webmvc` (old name
  deprecated, still resolves); `spring-boot-starter-test` no longer bundles MockMvc —
  use `spring-boot-starter-webmvc-test`; `@AutoConfigureMockMvc` moved package to
  `org.springframework.boot.webmvc.test.autoconfigure`. Expect more autoconfigure-class
  package moves as later stories add Spring Security / JPA (4.x split those into
  per-feature modules, e.g. `spring-boot-jdbc-test`, `spring-boot-security-test`).
  **Sharpest one so far:** `flyway-core` + `flyway-database-postgresql` alone are NOT
  enough — 4.x moved `FlywayAutoConfiguration` into a separate `spring-boot-flyway`
  module (add it explicitly, or use `spring-boot-starter-flyway`). Missing it fails
  **silently**: app boots, `/api/health` returns 200, and no migration ever runs — no
  error, no log line. Invisible to `@SpringBootTest`/Testcontainers tests (they don't
  drive real Spring autoconfiguration the same way); only surfaced by running `bootRun`
  against the real compose Postgres and checking `\dt`. **Lesson: verify any new SB
  starter-shaped dependency with a live `bootRun` smoke test, not just the test suite.**
  Testcontainers 1.x mis-parses Docker Engine 29's `/info` (400 with a zeroed body) —
  needs 2.x, whose module artifacts are renamed `testcontainers-postgresql` /
  `testcontainers-junit-jupiter` and whose container class moved to
  `org.testcontainers.postgresql.PostgreSQLContainer` (non-generic).
- **`postgres:18-alpine` volume mount is `/var/lib/postgresql`, NOT `.../data`** — pg18+
  refuses to start against the old path. Local host port is env-overridable via
  `DATABASE_PORT` (default 5432) to dodge conflicts with other local stacks.
- **No local JDK 21**; only 17 & 25 installed, and `gradle`/`sdk` aren't on PATH — use
  `./gradlew` (wrapper is Gradle 9.6.1, runs on Java 25).
