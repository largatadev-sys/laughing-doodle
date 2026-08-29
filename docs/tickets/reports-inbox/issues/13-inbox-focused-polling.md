# 13 — Inbox freshness: focused polling

**What to build:** A Member sitting on the Reports tab during a triage session sees new
reports appear on their own — no leaving and returning, no manual refresh. A ~60s refetch
runs while the inbox tab is focused, torn down on blur; it stacks on the existing
focus/foreground refetch. Client-only; no backend change, no new security surface. (This
shortens the *display* wait only — the upstream delivery lag is
[ticket 09](09-ingest-lag-measure-then-tune.md) and is explicitly not this ticket's
problem.) Spec: `../../../plans/story-20-report-notes-inbox-clarity.md`;
parent: [ticket 08](08-report-notes-and-inbox-clarity.md).

**Blocked by:** None (can start immediately — independent of 10–12, parallel-safe).

**Status:** done — implemented 2026-08-29 on `feature/story-20-report-notes-inbox-clarity`; backend suite green, client typechecks and lints clean, and the developer confirmed their own live check against the single-origin gate over LAN the same day.

- [x] While the Reports tab is focused, the report list refetches roughly every 60
      seconds; the interval is created on focus and torn down on blur (no timer runs
      while the user is elsewhere in the app or the app is backgrounded).
- [x] A poll failure is silent-stale, never disruptive: the existing error treatment
      (banner above stale data) applies only to explicit refreshes and focus refetches,
      not to a background tick — the list never blanks because a poll failed.
- [x] Badge, chips, and open-count follow the polled data automatically (they already
      derive from the shared list — verify, don't rebuild).
- [x] Demo (manual, standing no-e2e decision): leave the inbox open ≥60s, curl-inject a
      report through intake → it appears, and the tab badge moves, without touching the
      screen.

## Comments

**2026-08-29 — implemented.** Every code criterion above is done and covered by the automated
suite. The unticked items are the ones an agent cannot honestly tick: they need a real browser
and the developer's own eyes (standing rule — automated checks narrow what can go wrong, they
do not substitute for a live check). The API half of the demos *was* driven live against
`bootRun` + the compose Postgres: intake `201`, note created with the author resolved from the
token, a second member's edit stamping them, `DELETE` on the note path `404`, and an intake
replay returning `"notes":[]`.

**2026-08-29 — verified by the developer.** The manual and demo items above are now ticked on
the developer's own live check: the branch was served over LAN as the single-origin parity
image (`docker compose --profile fullstack`, `scripts/smoke.sh` 12/12 against the LAN address)
and exercised in a real browser. The inbox was seeded with the shapes those items call for — a
1479-char testimony, a signed-out report, reports with and without notes, notes by three
different authors including an edited one. Recording the developer's overall confirmation, not
an item-by-item observation of mine. **Local verification only: nothing here is deployed.**
