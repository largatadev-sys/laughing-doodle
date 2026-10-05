# 02: Events land — intake route, table, per-Event verdicts

Story 23 · Epic 4 · spec: [../spec.md](../spec.md) ("Wire contract") · ADR-014

**What to build:** Largata's backend can post a batch of usage Events with the shared
intake secret and get a verdict for every Event — accepted, duplicate or rejected with a
reason — while worklog stores every accepted Event forever in a new append-only table. A
small script posts a realistic sample batch and a Snapshot to any base URL, so every later
ticket's live check has data to look at before Largata sends anything real.

**Blocked by:** None (can start immediately).

**Status:** done — implemented 2026-09-24 on `feature/largata-dashboard-planning` (not yet merged to `dev`); hardened 2026-10-05 after the code review (spec "Amendments")

- [x] Migration creates `largata_events` (client-minted UUID id as PK, kind ≤100, optional
      subject ≤200, occurred_at, received_at defaulting to now, totals jsonb; indexes on
      (kind, occurred_at), (occurred_at), and a partial index for the latest Snapshot) —
      verified **live** with `bootRun` against the compose Postgres, the silent-Flyway check.
- [x] `POST /api/intake/events` joins the existing intake chain by path alone (no
      security-configuration change, no new secret); JSON body with 1–500 Events.
- [x] `200` with `results[]` in request order — `accepted` / `duplicate` / `rejected` plus
      a dotted reason on rejects: `eventId.invalid`, `kind.missing`, `kind.tooLong`,
      `subject.tooLong`, `occurredAt.missing`, `occurredAt.invalid`, `totals.invalid`.
      A rejected Event is not stored; the batch still succeeds.
- [x] Envelope-level `400` (standard field envelope) **only** for an unparseable body,
      `events` missing or empty, or more than 500 Events.
- [x] Idempotent per Event: a replayed `eventId` answers `duplicate` and stores nothing; a
      fully replayed batch is all-`duplicate`, `200`. One transaction per batch.
- [x] `totals` is ignored on any non-`snapshot` kind; on `snapshot` it must be an object of
      non-negative integers or the Event is `totals.invalid`. Future-dated `occurredAt` is
      accepted.
- [x] Mandatory security tests (API integration layer, `*EndpointTest` style): no / wrong
      secret → `401`, nothing stored; a valid Member JWT on intake → `401`; the intake
      secret on a team route → `401`. INV-2's code untouched.
- [x] A script under `scripts/` posts a sample batch (several kinds with subjects, a
      `.created` / `.deleted` pair, one `snapshot`) to a given base URL with the secret,
      printing the verdicts.
- [x] Live check: `bootRun` + the script → `200` with verdicts; run it again → all
      `duplicate`; the rows are visible in the database.
