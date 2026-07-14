# 01 — Restructure repo to `backend/` + `client/` peers

**What to build:** Move the root-level Java/Gradle project (`src/`, `build.gradle`,
`settings.gradle`, `gradlew*`, and the backend's relationship to `docker-compose.yml` /
root `.env`) into a `backend/` directory, so the repo root holds two symmetric peers —
`backend/` and `client/` — instead of the backend being scattered at root while the
client sits in its own folder. Purely a discoverability/symmetry improvement; no behaviour
change.

**Blocked by:** None — can start immediately. **Deferred to backlog** (developer's call,
2026-07-13) — the symmetry win is cosmetic and the cost lands on the immutable decision
record (see below); revisit when there's a reason to touch repo structure anyway.

**Status:** done (2026-07-14) — un-deferred and executed on branch
`chore/repo-restructure-backend-client`. Rationale + rejected alternative recorded as ADR-009;
off-epic ledger line in BUILD_STATUS. Verified: `./gradlew build` green from `backend/`, the
full-stack parity image builds with `backend/`-prefixed COPYs and `scripts/smoke.sh` passes
(health, SPA, proxy-CORS, live login + authed read).

## Why it's deferred, not done

Worked through during the Story 7a session. The code moves cleanly (`build.gradle` /
`settings.gradle` have no hardcoded paths; package structure is unaffected). The cost is
in the *surrounding contract*:

- **Immutable-record tension (the real blocker).** ~14 references to `./gradlew`,
  `src/main/...`, `docker-compose.yml`, `bootRun` live across 6 files. The live ones
  (`CLAUDE.md`, `BUILD_STATUS.md`, `.claude/settings.local.json`) *must* update. But the
  `docs/plans/story-N-*.md` and `docs/tickets/story-1-*.md` references sit in files the
  repo treats as **immutable, point-in-time records** — editing them to new paths rewrites
  history the project's own conventions say not to rewrite. Any restructure has to
  explicitly decide: leave stale historical paths as-is (accurate as-of-then), or add a
  one-time "paths are relative to `backend/` as of <commit>" note rather than editing each.
- **`.env` / docker-compose working-directory assumption.** The backend reads `.env` from
  its run directory; compose defines Postgres at root. Moving the project means re-deciding
  where `.env` and compose live so they still find each other — the same class of mismatch
  that caused a "password authentication failed" boot failure earlier in the 7a session.
- **IDE / Gradle root detection.** Tooling expects the build at repo root by convention;
  nesting it means either making the root a Gradle multi-project (`settings.gradle`
  including `backend`) or accepting the Java project is one level down from where the repo
  opens.

## Acceptance criteria (when un-deferred)

- [x] `backend/` and `client/` are sibling top-level directories; backend builds and runs
      from `backend/` — `cd backend && ./gradlew clean build` green (compile + Testcontainers
      suite), and the full-stack image runs the real jar end-to-end.
- [x] `docker-compose.yml` ↔ backend `.env` re-settled: both stay at root (compose auto-loads
      `.env` there; the backend never read `.env` from disk — it resolves env vars with defaults
      that match compose's). A clean run against the compose Postgres works end-to-end
      (`scripts/smoke.sh` login + authed read pass against the parity image).
- [x] Live docs updated to the new paths (`CLAUDE.md` — new *Repo layout* section + wrapper
      path; `BUILD_STATUS.md` ledger; `docs/deploy/railway.md` migration link); no stale
      root-relative backend paths remain in *live* docs. *(`.claude/settings.local.json` is
      untracked local-only config, not a committed doc — its `./gradlew` permission globs are
      throwaway conveniences re-granted on next use, so it was intentionally not edited.)*
- [x] Immutable `docs/plans/` + `docs/tickets/` records **left frozen** (leave-as-of-then),
      per the explicit decision recorded in ADR-009 and dated by the BUILD_STATUS ledger line.
- [x] Landed as an off-epic change with its own `BUILD_STATUS.md` ledger line.
