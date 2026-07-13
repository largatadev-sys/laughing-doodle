# Domain Docs

How the engineering skills should consume this repo's domain documentation when
exploring the codebase.

**This repo does not use the generic `CONTEXT.md` / `docs/adr/` layout.** It already has
a curated, production-depth context package that `CLAUDE.md` points at directly — use
that instead of creating a parallel structure.

## Before exploring, read these

- **[docs/design/02-domain-model.md](../design/02-domain-model.md)** — the glossary
  (ubiquitous language), entities, aggregates, and invariants. This is the `CONTEXT.md`
  equivalent.
- **[docs/design/04-architecture.md](../design/04-architecture.md)** — the ADR log lives
  inline in this file's `## ADR log` section, not as one-file-per-decision under
  `docs/adr/`. Read the ADRs that touch the area you're about to work in.
- **[docs/design/06b-engineering-decisions.md](../design/06b-engineering-decisions.md)** —
  the per-system decisions the reused engineering principles leave open (error taxonomy,
  auth model, test-depth dial). Treat this the way you'd treat a second ADR log scoped to
  "how this system is built," not "what the domain means."
- **[docs/plans/story-N-*.md](../plans/)** — per-story decision logs, written by
  `/grill-with-docs` sessions. These are the closest thing this repo has to
  implementation-scoped ADRs — read the plan for any story your ticket touches or blocks
  on.
- **[BUILD_STATUS.md](../../BUILD_STATUS.md)** — live status of what's actually built vs
  planned. Check this before assuming a story has landed.

If any of these files don't exist yet for a given area, proceed silently — don't flag
their absence or suggest creating `CONTEXT.md`/`docs/adr/` instead. `/grill-with-docs`
extends `docs/design/` and writes new `docs/plans/story-N-*.md` files lazily as terms and
decisions actually get resolved.

## File structure

```
/
├── CLAUDE.md                          ← points at everything below
├── BUILD_STATUS.md                    ← live build status
└── docs/
    ├── design/                        ← the context package (glossary, ADRs, decisions)
    │   ├── 02-domain-model.md         ← glossary + invariants (the CONTEXT.md equivalent)
    │   ├── 04-architecture.md         ← ADR log (the docs/adr/ equivalent)
    │   └── 06b-engineering-decisions.md
    ├── plans/                         ← per-story decision logs (story-N-*.md)
    └── tickets/                       ← issue tracker (see issue-tracker.md)
```

Single-context: this is a small solo backend + client repo, not a monorepo — no
`CONTEXT-MAP.md`/multi-context split applies.

## Use the glossary's vocabulary

When your output names a domain concept (in a ticket title, a refactor proposal, a test
name), use the term as defined in `02-domain-model.md` (e.g. **Member**, **TimeEntry**,
**shared visibility** — not "user," "log entry," or "team view"). Don't drift to synonyms
the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're
inventing language the project doesn't use (reconsider) or there's a real gap (note it
for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR (in `04-architecture.md`'s ADR log) or an
invariant in `02-domain-model.md` (especially **INV-2**, the author-only-writes
authorization boundary), surface it explicitly rather than silently overriding:

> _Contradicts ADR-002 (roll-your-own auth) — but worth reopening because…_
