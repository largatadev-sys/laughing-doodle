# 03: The Handoffs list

**What to build:** A Member can find any past Handoff and copy its text again. A Handoffs
entry on the Reports tab opens a list, newest first, showing when, who and how many Reports;
each row reopens that Handoff's frozen text with Copy. Spec: [../spec.md](../spec.md).

**Blocked by:** 01 — Tracer bullet: hand off and see the text.

**Status:** done — implemented 2026-10-07 on `feature/28-report-handoff` via `/implement-spec` (backend suite green, client typechecks and lints clean, three-axis code review applied); the manual demo is the developer's, in ticket 05.

- [x] `GET /api/handoffs` returns every Handoff, newest first, as summaries: id, created at,
      creator id and name, Report count. Summaries never carry the text. `200 []` when there
      are none; 401 without a token.
- [x] API integration tests cover ordering, the summary shape (no text), the empty case and 401.
- [x] The Reports tab offers a visible way into the Handoffs list (web).
- [x] Each row shows the date, the creator's name and the Report count, and opens the existing
      Handoff screen from ticket 01.
- [x] An empty list shows an empty state rather than a blank screen.
- [x] Backend suite green; client typechecks and lints clean.
- [ ] Demo (manual): make a Handoff, edit one of its Reports' Notes, reopen the Handoff from
      the list, and see the text unchanged; Copy works from the reopened screen.

## Comments
