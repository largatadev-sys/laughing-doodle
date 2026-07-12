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

_(grows as the build surprises us — none yet)_
