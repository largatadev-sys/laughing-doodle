# Spec — Largata usage Dashboard (Largata usage → worklog, live)

Status: ready-for-agent
Date: 2026-09-24
Origin: `/grill-with-docs` session 2026-09-23/24. The design tree closed with every
recommendation accepted ("all reco", 2026-09-24) and `/to-spec` invoked; that acceptance is
the explicit sign-off both stop-rules require — a **new migration** and a **new intake
route**. Architecture: ADR-014. Vocabulary: domain model, "Largata usage (Events & the
Dashboard)". C4 model (kept in step with this spec):
<https://claude.ai/artifact/4MCwpb9araF152bsJfLi5F>.
Scope: **worklog's half only.** The Largata-side half — the analytics sink that writes
Events to an outbox, the delivery loop, and *which kinds to send* — is built in the Largata
repo against the wire contract below. **Largata decides what it sends; worklog decides only
what it accepts and how it counts.**

## Problem Statement

The team can see what Largata's users *complain about* (the Reports inbox) but not what
they *do*. There is no view anywhere of whether Largata is alive and in use: how many
travelers were active today, how many trips, postcards and diaries exist, whether that is
growing week on week, or whether the backend has quietly stopped. Largata's backend already
emits analytics events for all of this — into a log nobody reads. The founders live in
worklog every day; the numbers live nowhere.

## Solution

Largata's backend pushes each usage **Event** — an opaque kind, optionally the Traveler who
did it, and when — through the intake door worklog already has, in batches, plus an hourly
**Snapshot** of its own totals. Worklog stores every Event forever in one append-only log
and computes every figure by counting it. A **Dashboard** screen, reached from a strip at
the top of the Reports tab, shows Travelers active today, a running total per kind, and
counts per day / month / year, refreshing itself every 30 seconds while open. When the
Snapshots stop, the Dashboard says "Largata silent since …" — the absence of Largata's
heartbeat *is* the health signal. Worklog never learns what a trip is: it validates the
envelope, never the vocabulary, so Largata can add a kind without a worklog change.

## User Stories

Members:

1. As a worklog Member, I want to see how many Travelers were active today, so that I know whether Largata is being used right now, not last month.
2. As a worklog Member, I want a running total for each kind of thing Travelers make (trips, postcards, diaries, …), so that I can see the product growing.
3. As a worklog Member, I want counts per day, month and year for each kind, so that I can tell a good week from a bad one.
4. As a worklog Member, I want the Dashboard to refresh itself while I'm looking at it, so that "live" means live and I never pull-to-refresh.
5. As a worklog Member, I want the Dashboard to say when Largata has gone quiet, so that a dead backend is the first thing I see, not something I infer from flat numbers.
6. As a worklog Member, I want "today" and the day buckets to follow my own calendar day, so that an event at 23:30 my time lands on the right day.
7. As a worklog Member, I want the Dashboard one tap from the Reports tab, so that feedback and usage sit together without another tab in the bar.
8. As a worklog Member, I want the headline numbers visible on the Reports tab itself, so that I don't open the Dashboard just to check the pulse.
9. As a worklog Member, I want a failed refresh to leave the last numbers on screen with an honest "as of" time, so that a network blip never blanks the screen.
10. As a worklog Member, I want a new kind that Largata starts sending to appear as a tile on its own, so that nobody has to change worklog when Largata adds a feature.
11. As a worklog Member, I want every Member to see the same Dashboard, so that nobody has to be an Admin to check on the product.
12. As a worklog Member, I want Events and the Dashboard kept out of the Inbox, so that triage stays a list of things to do.

The developer (the contract and its guarantees):

13. As the developer, I want Events to enter through the existing intake secret and nothing else, so that the public internet can never write into the log or flood it.
14. As the developer, I want a Member's JWT to be useless on intake and the intake secret useless on team routes, so that the two auth schemes never bleed, exactly as with Reports.
15. As the developer, I want intake to be idempotent per Event, so that Largata's delivery retries never double-count.
16. As the developer, I want a batch judged Event by Event, so that one malformed Event never dead-letters a whole batch in Largata's outbox.
17. As the developer, I want worklog to validate the envelope and never the vocabulary, so that a new kind in Largata is neither a lost Event nor a forced deploy here.
18. As the developer, I want totals to re-base on Largata's own hourly Snapshot, so that a missed Event is wrong for at most an hour, never forever.
19. As the developer, I want an Event dated before the latest Snapshot to be ignored by the totals but still counted in the series, so that late delivery never double-counts and never loses history.
20. As the developer, I want Events kept forever, so that the series never loses its past.
21. As the developer, I want the Traveler on an Event to be the same opaque identity a Report carries, so that one person is one identity across both features — and still never a worklog User.
22. As the developer, I want no new environment variable, so that deploying this is a push to `main`.
23. As the developer, I want the Dashboard rendering on dev to prove the migration ran, so that the silent-Flyway trap is covered by the live check.
24. As the developer, I want the intake tests to pin the wire contract, so that the Largata session builds against a tested interface, not prose.

Largata's backend (the other repo — stated here as the interface it can rely on):

25. As Largata's backend, I want to send Events in batches and receive a verdict per Event, so that my outbox can mark rows delivered or log rejects without retrying forever.
26. As Largata's backend, I want to replay a batch safely after a timeout, so that a cold-started worklog never causes duplicates.
27. As Largata's backend, I want to choose the kinds and what "active" means, so that the contract never needs a worklog change when the product changes.

## Implementation Decisions

### Domain (vocabulary in the domain model doc, "Largata usage (Events & the Dashboard)")

- **Event**: `id` (client-minted UUID — the idempotency key) · `kind` (opaque string,
  1–100 chars) · `subject` (opaque string ≤200 chars, optional — *the Traveler who did
  this*; the same identity `reporter_uid` carries on a Report) · `occurredAt` (Largata's
  clock, UTC) · `receivedAt` (worklog's clock) · `totals` (only when `kind` is `snapshot`:
  a map of counter → non-negative integer).
- **Snapshot** = an Event of the reserved kind `snapshot` — the **only** name worklog
  reserves.
- **Active today** = the number of distinct `subject`s across **all** Events whose
  `occurredAt` falls within the viewer's calendar day. The contract makes `subject` mean
  "the Traveler who did this", so any Event naming a Traveler is an act. Largata may also
  send an explicit activity ping kind (e.g. `traveler.active`); to worklog it is one more
  kind with a subject.
- **Counters**: two verb suffixes are the only names worklog *interprets* —
  `<counter>.created` adds one to `<counter>`, `<counter>.deleted` takes one away. Every
  other kind is counted per period and nothing more. Snapshot totals are keyed by
  `<counter>`.
- Events have no owner, no status, no Notes; every Member reads them equally.
  INV-1–INV-5 untouched. Kept forever — no delete route, no pruning (retention is an
  ADR-014 invalidator, not a feature).

### Schema (one new migration — additive only)

- `largata_events`: `id UUID PK` · `kind VARCHAR(100) NOT NULL` · `subject VARCHAR(200)` ·
  `occurred_at TIMESTAMPTZ NOT NULL` · `received_at TIMESTAMPTZ NOT NULL DEFAULT now()` ·
  `totals JSONB` (null unless `kind = 'snapshot'`). Indexes on `(kind, occurred_at)` and
  `(occurred_at)`, plus a partial index on `occurred_at DESC WHERE kind = 'snapshot'` for
  the latest-Snapshot lookup. Nothing references `users`; no row is ever updated or
  deleted.

### Wire contract (the fixed cross-repo interface — Largata builds to exactly this)

- **`POST /api/intake/events`** — `application/json`, header `X-Intake-Secret` (the same
  secret as `/api/intake/reports`; the intake chain already matches all of
  `/api/intake/**`, so no security-configuration change). Body:
  `{ "events": [ { "eventId": "<uuid>", "kind": "<1–100 chars>", "subject": "<≤200 chars,
  optional>", "occurredAt": "<ISO-8601 instant>", "totals": { "<counter>": <int ≥ 0>, … }
  }, … ] }`
  - **1–500 Events per call.** `totals` is meaningful only when `kind` is `snapshot`; on any
    other kind it is **ignored**, never a rejection.
  - **Envelope-level answers**: `401` (no / wrong secret — same envelope as Reports, same
    writer); `400` with the standard field envelope only when the body is unparseable or
    `events` is missing, empty, or over 500. Everything else is per Event.
  - **Per-Event verdicts, `200`**: `{ "results": [ { "eventId": "<uuid>", "status":
    "accepted" | "duplicate" | "rejected", "reason": "<dotted key — rejected only>" }, … ] }`
    in request order. Reasons use dotted keys matching the field-envelope convention:
    `eventId.invalid`, `kind.missing`, `kind.tooLong`, `subject.tooLong`,
    `occurredAt.missing`, `occurredAt.invalid`, `totals.invalid` (non-object, non-integer or
    negative value on a Snapshot). A rejected Event is **not stored** and is Largata's to
    log; **the batch still succeeds**. This is the poison-pill rule: Largata's outbox
    dead-letters any row that gets a 4xx, so a bad Event must never become a 4xx for the
    batch.
  - **Idempotency**: `eventId` is the primary key. A replayed Event answers `duplicate` and
    stores nothing; a fully replayed batch is all-`duplicate`, `200`. One transaction per
    batch: accepted Events land together or not at all (a database failure → `500`, Largata
    retries the whole batch, and the retry answers `duplicate` / `accepted` correctly).
  - **Future-dated `occurredAt`** (clock skew) is accepted and counted by its own timestamp
    — the Reports precedent: `submittedAt` is trusted as a pattern, not a point.
  - **Naming convention** — documented, **not validated** beyond the two suffixes:
    `<counter>.created` / `<counter>.deleted`; Snapshot `totals` keyed by `<counter>`;
    `subject` present ⇔ a Traveler did this. A suggested first set for the Largata session,
    **not enforced by worklog**: `traveler.created` (sign-up), `trip.created` /
    `trip.deleted`, `postcard.created` / `postcard.deleted`, `diary.created` /
    `diary.deleted`, `itinerary.created` / `itinerary.deleted`, `traveler.active`
    (debounced activity ping), and `snapshot` hourly with `{ "traveler": n, "trip": n,
    "postcard": n, "diary": n, "itinerary": n }`. Largata picks the words; worklog renders
    them.
  - Delivery guarantees are Largata's job (store-and-forward, retry on timeout / 5xx);
    worklog's job is that replays are safe and verdicts are per Event.

### Aggregation rules (what the team API computes — the tests pin these)

- **Total for `<counter>`** = the value in the latest Snapshot that carries that counter
  + count of `<counter>.created` with `occurredAt` after that Snapshot's `occurredAt`
  − count of `<counter>.deleted` likewise. A counter seen in Events but in no Snapshot has
  **no baseline**: total = created − deleted over all time, flagged `baseline: "none"` so
  the tile can say so.
- **Active today** = count of distinct `subject` over all Events with a non-null `subject`
  and `occurredAt` within [start of today, start of tomorrow) in the viewer's zone.
- **Series for `<counter>`** = per bucket (day / month / year, in the viewer's zone) the
  `created` and `deleted` counts. Buckets with no Events are returned as zeros within the
  range.
- **Freshness**: `lastEventAt` (max `occurredAt`), `lastSnapshotAt`; `silent` = no
  Snapshot yet, or the latest is older than **2 × the expected cadence (1 h)**;
  `silentSince` = `lastSnapshotAt` when silent.
- **Zone**: an IANA zone name from the client; invalid → `400` with key `zone`; absent →
  `UTC`. The server never guesses a zone.

### Team-facing API (bearer-JWT, standard conventions, any Member)

- `GET /api/dashboard/summary?zone=<IANA>` → `{ "asOf", "zone", "lastEventAt",
  "lastSnapshotAt", "silent", "silentSince", "activeToday", "totals": [ { "counter",
  "value", "baseline": "<Snapshot occurredAt>" | "none" }, … ] }`. Totals ordered as the
  latest Snapshot lists them, then any baseline-less counters alphabetically.
- `GET /api/dashboard/series?counter=<counter>&bucket=day|month|year&zone=<IANA>[&from=&to=]`
  → `{ "counter", "bucket", "zone", "points": [ { "start": "<date | month | year, in
  zone>", "created", "deleted" }, … ] }`. Defaults: `day` → last 30 days, `month` → last
  12 months, `year` → every year with data. An unknown counter → empty `points`, `200`
  (a kind that has never arrived is not an error).
- Both are reads; this surface has no write. No separate counts endpoint for the strip: it
  uses `summary`.

### Backend shape

- New `dashboard` module beside `reports`, same layering: controller (HTTP + validation)
  → service (logic) → repository (persistence). An intake controller / service /
  repository for Events, a query service for the aggregation rules, and a read controller.
  The intake route joins the existing intake filter chain by path match alone; the read
  routes sit behind the JWT chain like every `/api/**` route. INV-2's enforcement code is
  untouched — nothing here has an owner.
- Aggregation is SQL over the one table (count, count distinct, `date_trunc` with
  `AT TIME ZONE`), computed on read. No caching, no materialised views — volume is
  trivial; ADR-014 names the invalidator.
- JSON, not multipart: Events carry no files.

### Client shape

- **Reports tab strip**: a compact row above the status chips — "Largata ·
  {activeToday} active today · {first two totals} · updated {relative}" — or, when silent,
  "Largata silent since {when}" in the warning treatment. Tapping opens the Dashboard. The
  strip shares the Dashboard's fetch (one provider, one poll) and polls only while the
  Reports tab or the Dashboard is focused and the app foregrounded.
- **Dashboard screen** (drill-in slide, the report-detail pattern): the freshness line
  first (`as of` / the silent state); a tile row — Active today, then one tile per counter
  with its total (a baseline-less tile says "since events began"); a counter picker and a
  Day / Month / Year segmented control; one bar series for the selected counter. Tiles and
  series follow the repo's data-viz conventions and the Largata brand tokens. Read-only —
  no actions.
- **Polling**: every 30 s while focused and foregrounded, quiet on failure — the Inbox's
  exact pattern: the last good numbers stay, the freshness line shows their `asOf`.
- **Zone**: the device's IANA zone via the Intl API, sent on every request. "Today"
  therefore follows the viewer, as the Story 12 activity label does.
- **The developer will revise placement and tiles after using it** — expected, not a
  defect; strip-on-Reports is the v1 default, not a commitment.

### Environments & ops

- **No new env var**: `REPORTS_INTAKE_SECRET` already guards `/api/intake/**` in both
  Railway environments (with different values per environment, so a mis-pointed Largata
  build 401s). The `.env.example` comment gains one line: the secret now guards both
  intake routes.
- Which Largata environment pushes to which worklog environment is the Largata repo's
  configuration; its existing base-URL variable already points at the right worklog per
  environment.
- `scripts/smoke.sh` gains one probe: `POST /api/intake/events` without the secret →
  `401` (proves the route is wired). The Dashboard rendering on dev after the deploy is the
  probe that proves the migration ran — the standing silent-Flyway lesson.

## Testing Decisions

- **Seam: the existing backend API integration layer** — MockMvc + Testcontainers
  Postgres, `*EndpointTest` style (prior art: the reports package's endpoint tests,
  `JwtFilterChainTest`, the intake-secret chain tests). No new seams; no unit tests of
  internals; tests assert status codes, envelope shapes and persisted effects only. It is
  the highest existing seam, and the one the wire contract lives at.
- **Mandatory security coverage** (06b's rule extends to the new surface): intake with no
  / wrong secret → `401`, nothing stored; a valid Member JWT on intake → `401`; the intake
  secret on `/api/dashboard/*` → `401`; no / invalid JWT on `/api/dashboard/*` → `401`.
- **Envelope & verdicts**: a mixed batch → per-Event `accepted` / `rejected` with the right
  dotted reasons, rejects not stored, batch `200`; a replay → all `duplicate`, no new
  rows; 501 Events → `400` envelope; `totals` on a non-Snapshot ignored; an unparseable
  body → `400`.
- **Aggregation** (the contract tests for the numbers): total = Snapshot + created −
  deleted after it; a `created` dated *before* the latest Snapshot changes the series but
  not the total; a counter with no Snapshot → `baseline: "none"`; active today counts a
  subject once across two kinds; a 23:30 UTC Event lands on the *next* day in
  `Asia/Manila` for both "today" and the day series; month and year bucketing; empty
  buckets are zeros; `silent` flips after two hours without a Snapshot; invalid zone →
  `400` with key `zone`; absent zone → UTC.
- **Client**: no automated e2e (standing 06b decision) — typecheck + lint, a
  headless-browser drive of Reports → strip → Dashboard → picker, and the developer's live
  check, which per the standing rule is the only thing that closes a deploy.
- The intake and aggregation tests **are** the contract tests the Largata session builds
  against.

## Out of Scope

- **Everything Largata-side**: the analytics sink that writes Events to an outbox, the
  delivery loop and its batching, *which kinds exist*, what "active" means, debounce
  windows, and which Largata environment pushes where. Separate repo, separate session,
  built to the wire contract above.
- Reports figures on the Dashboard (open vs done, reports per week) — the Inbox chips
  already count; revisit if the screen earns a second section.
- A sixth tab; alerts, push or email on silence; an attributes bag on Events; pruning or
  retention; a bespoke per-kind layout with fixed semantics (that is a vocabulary, and it
  belongs in Largata); history before go-live (the first Snapshot seeds the totals; the
  series starts when Events start); anonymisation beyond what Reports already carry; any
  read of Largata's data by worklog; any Largata-facing status; changes to the JWT chain,
  INV-2, the login surface, or anything in `reports`.

## Amendments (2026-09-24, during the build)

- **`totals` is `JSON`, not `JSONB`** (migration V8). JSONB re-sorts object keys by length,
  which would break "totals ordered as the latest Snapshot lists them". Nothing queries inside
  the column by key, so JSONB's indexing bought nothing.
- **Two more rejection reasons** on the wire: `kind.invalid` (kind present but not a string)
  and `subject.invalid` (subject present but not a string). A blank `subject` is stored as no
  Traveler. `eventId` must be a UUID in canonical form. A Snapshot `totals` with a blank
  counter name is also `totals.invalid` (a counter named "" could never render as a tile).
  Still per-Event verdicts; the batch still answers `200`.
- **Smoke probes:** three rather than one — the Events intake without the secret, and the
  Dashboard's two auth boundaries (no token; the intake secret) — mirroring the Reports probes.
- **Series ranges:** `from` / `to` are calendar days (`YYYY-MM-DD`) in the viewer's zone,
  both inclusive; `from` after `to` or a range over 1,100 buckets is `400` with key `from`;
  `counter` missing or `bucket` missing/unknown is `400` with that key. Bucket bounds are
  computed in Java and sorted with `width_bucket`, so Postgres never interprets a zone name.
- **New read for design v2:** `GET /api/dashboard/active?bucket=&zone=[&from=&to=]` →
  `{ bucket, zone, points: [ { start, active } ] }` — distinct Travelers per bucket, the same
  rule as `activeToday` (today's day bucket always equals it). Empty `points` when no Event
  has ever named a Traveler.
- **Client: design v2 replaces the screen layout** described under "Client shape"
  (handoff and build notes: [design-v2/README.md](design-v2/README.md)). The screen is titled
  "Activity"; freshness moved into the header; the hero card charts Travelers active; the
  totals tiles are the chart picker; the chart shows 14 days / 12 months / every year with
  data and no deleted figures. Design v2 also drops the tile caption "since events began"
  for baseline-less counters (ticket 04); it survives only in the tile's accessibility label.
  A failed refresh shows "couldn't refresh since {asOf}" in the header, so a worklog-side
  outage never reads as Largata going quiet. The Reports-tab strip is unchanged.

## Amendments (2026-10-05, after the code review and the contract check)

- **`occurredAt` window**: an Event must fall in [2020-01-01T00:00Z, worklog's now + 1 h], else
  `occurredAt.invalid`. This replaces "future-dated is accepted" for anything beyond an hour.
  The log is append-only, so one far-future Snapshot used to freeze every total and mute
  "silent" for good, and a sentinel date (e.g. 0001-01-01) broke the year chart for good.
- **Control characters** in `kind`, `subject` or a Snapshot counter name are `kind.invalid` /
  `subject.invalid` / `totals.invalid`. A NUL used to 500 the whole batch on every retry and,
  inside a counter name, to break every later summary. No new reason key.
- **The summary is one consistent read**: totals come from a single statement, and the
  summary runs in a REPEATABLE READ transaction, so an hourly Snapshot committing mid-request
  can't drop that hour's changes from the answer.
- **Period reads**: `from` / `to` outside 1970-01-01..9999-12-31 are a `400` (not a 500). For
  `month` and `year`, `from` / `to` select the buckets they fall in, and a bucket is always the
  whole month or year.
- **Logging**: one INFO line per intake batch with the verdict counts, and a WARN line with
  the rejection reasons. It never logs a subject, totals or the body.
- **Structure**: the `dashboard` module is split by role (`controller/ dto/ service/
  repository/ domain/`), the reference for the convention now in CLAUDE.md, with an ArchUnit
  test guarding its internals. The client mirrors it under `components/dashboard/`.
- **The hand-off** ([largata-handoff-prompt.md](largata-handoff-prompt.md)) is now
  contract-only: the sender's internals are the Largata developer's design.
- **The Dashboard counts in UTC** (developer's call: "we all base our times for UTC"). The client
  sends `zone=UTC` on every request, so "today", "active today" and every day / month / year
  bucket are UTC days, the same for every Member — this supersedes user story 6 and the
  "device's IANA zone" line under Client shape. Times of day still display in the viewer's local
  time. The API keeps accepting any IANA zone (the contract is unchanged); the client's one
  constant is the switch.
- **Deployed secrets**: a deployed worklog (Railway sets `RAILWAY_*` variables) refuses to start
  if `JWT_SECRET` or `REPORTS_INTAKE_SECRET` is unset, blank or the repo's placeholder
  (developer-approved auth change, 2026-10-05). Local dev and the gate are unaffected.

## Further Notes

- **Stop-rules**: the new migration and the new intake route were signed off by the
  developer at the close of the grilling (2026-09-24 — "all reco", then `/to-spec`). INV-2
  and its tests are untouched; the intake secret now guards a second route, which is why
  the security tests above are mandatory, not optional.
- **Latency floor**: Largata's outbox polls every 60 s with backoff; the Dashboard polls
  every 30 s. "Live" therefore means one to two minutes behind, whatever either side tunes
  later.
- **Facts about Largata gathered during the grilling** (from the local checkout — for the
  Largata session's convenience, **not binding**): the backend is Spring Boot 4.1 /
  Java 25 / Spring Modulith with `@EnableScheduling` on; an `Analytics` interface with a
  logging sink already emits `postcard_created`, `postcard_deleted`, `diary_created`,
  `diary_deleted`, `itinerary_created` (that is a Trip), `trip_destroyed`,
  `itinerary_object_published` / `_retired` / `_destroyed` (published itineraries) and
  `traveler_signed_up` (lazy provisioning on the first authenticated request), mostly after
  commit — a second sink writing to an outbox is the natural hook, no AOP needed; the
  reports outbox is report-specific (one row per HTTP call, dead-letters on 4xx), so an
  Events outbox is new work there; the reporter uid Reports carry is Largata's Traveler
  UUID; Trips have a lifecycle (upcoming / ongoing / completed) and can be archived
  (reversible) or destroyed; "itinerary" in Largata's code means the *published* snapshot
  of a Trip, so the Largata session must pick which of the two it calls `itinerary` on the
  wire. **The developer's stated position: worklog does not need these facts; Largata
  decides what to send.** They are recorded so that session starts warm.
- **Bookkeeping**: Epic 4 section + story rows in the epic map and BUILD_STATUS at ticket
  time; the `.env.example` comment; the smoke probe.
- **Hand-off artifact for the Largata repo**: the "Wire contract" and "Aggregation rules"
  sections are the interface. The Largata session should receive this spec as its starting
  input.
