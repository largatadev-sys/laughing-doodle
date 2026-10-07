# 04: "Handed off" on each Report

**What to build:** A Member opening a Report sees whether it has already gone out: one
"Handed off <date>" line for every Handoff it was part of. The Handoffs arrive on the existing
reports read, the same way Notes do, so the inbox still loads in one fetch.
Spec: [../spec.md](../spec.md), "Reports read gains Handoffs".

**Blocked by:** 01 — Tracer bullet: hand off and see the text.

**Status:** wontfix — built 2026-10-07 on `feature/28-report-handoff`, then dropped by the developer during the LAN check before the merge (not needed); removed in full, so it never reached `dev`.

- [x] Each Report on the team reports read carries a `handoffs` list: the id and created-at of
      every Handoff it was part of, oldest first; empty when none.
- [x] The intake response keeps returning an empty `handoffs` list, replay included — Largata
      never reads worklog's own records. The intake wire contract is otherwise untouched.
- [x] API integration tests cover: a Report in no Handoff, a Report in two Handoffs (both
      listed, oldest first), and the intake response carrying an empty list.
- [x] The Report detail screen shows one "Handed off <date>" line per Handoff, near the status;
      nothing when there are none.
- [x] Handing off never changes a Report's status (verified in the tests above).
- [x] Backend suite green; client typechecks and lints clean.
- [ ] Demo (manual): hand off the same Report twice; its detail screen shows both dates.

## Comments

- 2026-10-07 (implement-spec exploration): the status-change response (`PUT /api/reports/{id}/status`)
  is built by the same team-response mapping as the reports read, and the client swaps it into its
  cache. It must carry `handoffs` too, or the "Handed off" lines vanish after a triage until the
  next poll. Treat it as in scope and cover it with a test.
- 2026-10-07 (code review): the "Handed off" line was switched from relative ("3h ago") to
  date-only ("Oct 7") per the spec's `Handed off <date>`, so a same-day Handoff still shows its date.

- 2026-10-07 (LAN check): the developer dropped the "Handed off <date>" line from the Report
  detail screen — not needed. The detail screen is restored to its pre-story form.
- 2026-10-07 (LAN check, later): with the line gone nothing displayed the data, so the
  backend half went too at the developer's go — `handoffs` off the reports read and the
  status-change response, `HandoffRefQuery` / `HandoffRef` / the membership query and
  `ReportHandoffsEndpointTest` deleted; `ReportResponse`/`ReportService` restored to their
  pre-story form. "Handing off never changes status" stays tested in `CreateHandoffEndpointTest`.
- 2026-10-07 (post-check review): the last leftover went too — V9's `ix_handoff_reports_report`
  (it only served the dropped reports-read lookup) was removed from the migration at the
  developer's call. V9 had never left this branch (dev/main stop at V8), so the only database
  that had applied it, the local gate's, was reset and re-migrated.
