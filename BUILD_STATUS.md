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
| 1 | Walking-skeleton base: schema + health | ⬜ | — | — |
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
| — | — | — | — |
