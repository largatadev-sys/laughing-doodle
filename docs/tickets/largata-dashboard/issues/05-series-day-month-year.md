# 05: Series — counts per day, month and year

Story 26 · Epic 4 · spec: [../spec.md](../spec.md) ("Aggregation rules", "Client shape")

**What to build:** on the Dashboard a Member picks a counter and Day / Month / Year and
sees a bar series of how many were created (and deleted) in each bucket of their own
calendar, with empty buckets shown as zero rather than missing.

**Blocked by:** 04 (Dashboard screen).

**Status:** done — implemented 2026-09-24 on `feature/largata-dashboard-planning` (not yet merged to `dev`); hardened 2026-10-05 after the code review (spec "Amendments")

- [x] `GET /api/dashboard/series?counter=&bucket=day|month|year&zone=[&from=&to=]` behind
      the JWT chain; response carries `counter`, `bucket`, `zone` and `points[]` of
      `{ start, created, deleted }`; defaults last 30 days / last 12 months / every year
      with data; buckets with no Events are zeros; an unknown counter answers empty
      `points`, `200`; invalid zone → `400` with key `zone`; absent → UTC.
- [x] Bucketing happens in the zone: an Event at 23:30 UTC falls in the next day's bucket
      for `Asia/Manila`; month and year bucketing; explicit `from` / `to` bounds respected.
- [x] The Dashboard gains a counter picker and a Day / Month / Year segmented control, and
      one bar series for the selection with the latest bucket emphasised, following the
      repo's data-viz conventions; the series refreshes on the same 30 s poll and on any
      picker change.
- [x] Security: no / invalid JWT → `401`; the intake secret on this route → `401`.
- [x] Typecheck + lint clean; headless drive of the picker on web; the developer's live
      check on the LAN gate against script data spanning several days and two months.
