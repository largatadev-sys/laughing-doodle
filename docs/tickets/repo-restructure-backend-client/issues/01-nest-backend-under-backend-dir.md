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

**Status:** ready-for-human

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

- [ ] `backend/` and `client/` are sibling top-level directories; backend builds and runs
      from `backend/` (`./gradlew bootRun`, tests green).
- [ ] `docker-compose.yml` ↔ backend `.env` working-directory relationship is re-settled
      and a clean `bootRun` against the compose Postgres still works end-to-end.
- [ ] Live docs (`CLAUDE.md`, `BUILD_STATUS.md`, `.claude/settings.local.json`) updated to
      the new paths; no stale root-relative backend paths remain in *live* docs.
- [ ] Immutable `docs/plans/` + `docs/tickets/` records handled per an explicit, recorded
      decision (leave-as-of-then vs. one-time relocation note) — not silently edited.
- [ ] Landed as an off-epic change with its own `BUILD_STATUS.md` ledger line.
