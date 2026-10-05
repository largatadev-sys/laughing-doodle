# 03: Summary read — active today, totals, freshness

Story 24 · Epic 4 · spec: [../spec.md](../spec.md) ("Aggregation rules", "Team-facing API")

**What to build:** an authenticated Member can ask worklog for the Dashboard's headline
numbers in their own time zone and get back Travelers active today, one running total per
counter re-based on Largata's latest Snapshot, and whether Largata has gone silent. These
tests are the contract tests for the numbers.

**Blocked by:** 02 (Events land).

**Status:** done — implemented 2026-09-24 on `feature/largata-dashboard-planning` (not yet merged to `dev`); hardened 2026-10-05 after the code review (spec "Amendments")

- [x] `GET /api/dashboard/summary?zone=<IANA>` behind the JWT chain, any Member; response
      carries `asOf`, `zone`, `lastEventAt`, `lastSnapshotAt`, `silent`, `silentSince`,
      `activeToday` and `totals[]` of `{ counter, value, baseline }`.
- [x] Total per counter = the latest Snapshot's value for it + `<counter>.created` −
      `<counter>.deleted` with `occurredAt` after that Snapshot; a counter with no Snapshot
      → created − deleted over all time with `baseline: "none"`; totals ordered as the
      latest Snapshot lists them, then baseline-less counters alphabetically.
- [x] Active today = distinct `subject` over all Events with a subject whose `occurredAt`
      falls in [start of today, start of tomorrow) in the zone.
- [x] `silent` = no Snapshot yet, or the latest older than two hours; `silentSince` set.
- [x] Zone: invalid IANA name → `400` with key `zone`; absent → UTC.
- [x] Aggregation tests: a `.created` dated before the latest Snapshot changes nothing; a
      subject named by two kinds counts once; an Event at 23:30 UTC is "today" in
      `Asia/Manila` on the next calendar day; `silent` flips after two hours; a
      baseline-less counter; an empty log answers zeros and `silent: true`.
- [x] Security: no / invalid JWT → `401`; the intake secret on this route → `401`.
- [x] Live check: the ticket-02 script's data → the summary via curl with a token matches
      hand-computed numbers.
