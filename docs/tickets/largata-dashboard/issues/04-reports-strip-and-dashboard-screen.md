# 04: Reports tab strip and the Dashboard screen

Story 25 · Epic 4 · spec: [../spec.md](../spec.md) ("Client shape")

**What to build:** on the Reports tab a Member sees a compact Largata strip — active
today, the first totals, and when it was last updated — or "Largata silent since …" in the
warning treatment. Tapping it opens the Dashboard: the freshness line first, then the
tile row. Both refresh every 30 seconds while focused and foregrounded, stay quietly on
the last good numbers when a refresh fails, and use the device's own calendar day.

**Blocked by:** 01 (polling hook), 03 (summary read).

**Status:** done — implemented 2026-09-24 on `feature/largata-dashboard-planning` (not yet merged to `dev`); hardened 2026-10-05 after the code review (spec "Amendments")

- [x] The strip sits above the Inbox's status chips, reads from the summary, and shows
      the silent state in the warning treatment; tapping it opens the Dashboard as a
      drill-in slide, the report-detail pattern. The Inbox itself is otherwise unchanged.
- [x] The Dashboard screen: freshness line (`as of` / silent since) first; a tile row —
      Active today, then one tile per counter with its total; a baseline-less tile says
      "since events began". Read-only, no actions. Largata brand tokens; tiles follow the
      repo's data-viz conventions.
- [x] One provider feeds the strip and the screen; it polls every 30 s through the
      ticket-01 hook only while the Reports tab or the Dashboard is focused and the app
      foregrounded; a failed poll leaves the last numbers and their `asOf` on screen.
- [x] The device's IANA zone is sent on every request.
- [x] Web and native both work with the real input: pointer and touch, no text selection
      on controls, prose selectable (the Story 20 lesson: drive the real input, not the
      code path).
- [x] Typecheck + lint clean; a headless-browser drive of login → Reports → strip →
      Dashboard; the developer's live check on the LAN gate with the ticket-02 script's
      data, including the silent state (post a Snapshot dated more than two hours ago).
