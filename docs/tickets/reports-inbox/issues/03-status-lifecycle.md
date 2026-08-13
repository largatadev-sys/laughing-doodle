# 03 — Triage: the status lifecycle

**What to build:** The inbox becomes a tracker. Any Member opens a report from the list
into a drill-in detail screen — full description plus metadata (reporter, platform,
version, submitted/received, last status change and who made it) — and moves it between
statuses: `new` · `discuss` ("For discussion") · `in progress` · `done` · `dismissed`.
Free movement, no ownership, attribution recorded. Triaging a report out of `new` is what
clears the badge, for everyone. Demo: walk a report `new → discuss → done` from the UI
and see the attribution update.

**Blocked by:** 01 — Intake skeleton · 02 — Inbox tab.

**Status:** done

- [x] Status-update endpoint: any Member may move any report between any two statuses →
      `200` with the updated report; unknown id → `404`; unknown status value → `400`.
- [x] Status-changed-by is stamped from the JWT identity — never from the request body —
      with status-changed-at alongside; proven by test.
- [x] No/invalid JWT → `401`; there is no delete or edit-description surface anywhere.
- [x] Detail drill-in from a card (same slide pattern as the existing day drill-in): full
      description, metadata block, status control with the five UI labels (New / For
      discussion / In progress / Done / Dismissed).
- [x] A status change reflects in the list, the filter chips, and the badge without a
      manual refresh of the app.
- [x] Endpoint behavior proven at the API integration seam; UI via the manual checklist
      (web + native).
