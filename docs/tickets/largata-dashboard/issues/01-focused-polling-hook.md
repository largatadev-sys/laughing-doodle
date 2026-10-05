# 01: Extract the Inbox's focused polling into a shared hook (prefactor)

Story 22 · Epic 4 · spec: [../spec.md](../spec.md)

**What to build:** no visible change. The Inbox keeps refreshing itself every ~60 s while
its tab is focused and the app is foregrounded, pauses otherwise, and a failed refresh
still leaves the list exactly as it was with no banner. The focus-and-foreground timer and
the quiet-failure rule move into one reusable hook, so the Dashboard (ticket 04) reuses
the Story 20 behaviour instead of copying it — and its fixes (no polling from a
backgrounded app, none from other screens, silent ticks) are protected in one place.

**Blocked by:** None (can start immediately).

**Status:** done — implemented 2026-09-24 on `feature/largata-dashboard-planning` (not yet merged to `dev`). The Story 20 manual checklist was not re-run; a headless drive confirmed focus-only polling and quiet failures.

- [x] One hook owns the interval timer, the app-state listener and the focus lifecycle,
      parameterised by the interval and the refresh function; the Inbox is its first caller.
- [ ] The Inbox's polling behaviour is unchanged: starts on focus with the app active,
      stops on blur or background, resumes on foreground, ~60 s tick, failures silent —
      verified by the same manual checklist Story 20 used (ticket 13 of reports-inbox).
- [x] Client typechecks and lints clean; no backend change; no new dependency.
