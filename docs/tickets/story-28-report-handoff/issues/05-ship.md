# 05: Ship

**What to build:** Story 28 lands on `dev`, verified the way this repo requires: the migration
proven against a real database, the decision that the client writes the frozen text recorded
as an ADR, the developer's own live check in a browser, and the bookkeeping current.
Spec: [../spec.md](../spec.md).

**Blocked by:** 02 — The full text format; 03 — The Handoffs list; 04 — "Handed off" on each
Report.

**Status:** done — shipped 2026-10-07 (`dev` and `main` at `dc45780`, squash `63d3c77`, smoke 16/16 on both); the developer live-checked dev and prod the same day.

- [x] A live `bootRun` against the compose Postgres confirms V9 applied (both tables present) —
      the standing silent-Flyway lesson; the test suite alone does not count.
- [x] ADR-015 recorded in `docs/design/04-architecture.md`: a Handoff's frozen text is written
      by the client and stored as given, with the trade-off against server generation and its
      accepted consequences (no automated formatter tests; text reflects the client's loaded
      data; the server checks only that the Reports exist).
- [x] `scripts/smoke.sh` passes against the local gate.
- [x] The developer's live check on the full local image in a real browser: the demos of
      tickets 01–04 run end to end. Reported as "automated checks pass; needs your live check"
      until the developer confirms.
- [x] BUILD_STATUS: Story 28's row updated (status, ticket links, squash commit), and the
      skills-package re-sync that rides on this branch recorded in the off-epic ledger.
- [x] Squash-merged into `dev` with a fresh `feat(handoffs): …` message that also names the
      skills re-sync; the squash SHA fixed in BUILD_STATUS directly on `dev` afterwards.
- [x] Staged diff scanned for secrets before every commit.

## Comments

- 2026-10-07 (ticket 05, agent half): V9 proven live against the compose Postgres through the
  full-stack gate image (`docker compose --profile fullstack up --build`, the same jar
  `bootRun` would run): `\dt` shows `handoffs` and `handoff_reports`, and
  `flyway_schema_history` rank 9 (`handoffs`) succeeded. `scripts/smoke.sh` gained a Report
  Handoffs block (rejection paths only: both routes 401 without a token, the intake secret
  opens neither) and passes 17/17 on `http://localhost:8080` and on the LAN address.
  ADR-015, the 06b trigger note and the BUILD_STATUS row/narrative/ledger are committed.
  Remaining: the developer's live check, then the squash and the `dev`-side SHA fix.

- 2026-10-07 (live check): the developer checked the full local image over LAN on mobile web
  across several rounds and confirmed "all good". Changes made during the check (centre-disc
  hand-off, morphing nav bar, floating Copy with an http fallback, the dropped "Handed off"
  line and reports-read field, tab-header and tab-transition fixes, the animation-freeze fix)
  are recorded in the spec's Further Notes, ticket 01/04 comments and BUILD_STATUS. Remaining:
  BUILD_STATUS row + squash SHA, the squash into `dev`.

- 2026-10-07 (ship): squashed into `dev` at `63d3c77` (SHA recorded on `dev` in `8cfd8e5`),
  pushed; dev went live in ~100 s, smoke 16/16. `main` fast-forwarded to `8cfd8e5` and pushed;
  prod went live in ~180 s serving the same bundle, smoke 16/16. V9 inferred applied (healthy
  boot); the developer's live check on dev and prod is the remaining step.

- 2026-10-07 (close): the developer confirmed the live check on dev and prod ("all good").
