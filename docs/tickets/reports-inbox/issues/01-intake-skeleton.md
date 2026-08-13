# 01 — Intake skeleton: a text-only Report lands and is listable

**What to build:** A Report submitted by Largata's backend arrives in worklog and the team
can read it. The relay's caller experience: post the wire contract's JSON part with the
shared secret → get a 201 (or an idempotent 200 on replay); worklog's experience: the
report is persisted with status `new` and comes back from the authenticated team list,
newest-first. This is the feature's walking skeleton — it pins the wire contract
(spec: `docs/tickets/reports-inbox/spec.md`, "Wire contract") that the Largata repo
builds against. Screenshots are later tickets; the multipart envelope exists from day
one, with only the `report` JSON part accepted.

**Blocked by:** None — can start immediately.

**Status:** done

- [x] A new migration creates the reports schema per the spec: client-minted UUID primary
      key, checked `type` and `status` enums, reporter name/uid as opaque strings,
      platform + app version, `submittedAt` (from the payload) and `receivedAt` (stamped
      on arrival), status-changed-by (nullable, references a worklog user) + status-changed-at.
- [x] Intake with a valid `X-Intake-Secret` and valid payload → `201` with the report;
      the row is persisted with status `new`.
- [x] Replaying the same `reportId` → `200` with the already-stored report; exactly one
      row exists regardless of replays.
- [x] Missing or wrong secret → `401` error envelope, nothing persisted; the comparison
      is constant-time; the secret value never appears in any log line.
- [x] A valid member JWT does not open the intake route (the two auth schemes don't bleed).
- [x] Malformed payload — missing/unknown type, empty or over-2000-char description,
      unknown platform, missing reportId/submittedAt — → `400` envelope with per-field
      details, nothing persisted.
- [x] Team list endpoint behind bearer JWT returns all reports newest-first by
      `submittedAt`, with an optional status filter; no/invalid JWT → `401`; empty → `200 []`.
- [x] The secret is configuration (env var), with a placeholder in `.env.example` only —
      never a real value in the repo.
- [x] All of the above proven at the API integration seam in the established
      endpoint-test style (real Postgres via Testcontainers).
