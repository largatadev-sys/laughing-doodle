# 02 — Device context tracer bullet: three fields end-to-end

**What to build:** A v1.2-shaped report lands and is fully visible. Curl a report whose
`context` carries `os`, `browser`, and `deviceModel` into intake → `201`; open it in the
inbox detail → one combined **Device** row ("Chrome 128 · Windows 11 · Pixel 6").
Everything the scoped contract promises holds end-to-end — stored, echoed by both intake
responses, carried by the team list, rendered — and pre-v1.2 shapes keep landing and
rendering unchanged. Contract-level definition: `../../reports-inbox/spec.md`,
"Amendments → v1.2 (planned)"; scoping record:
[ticket 01](01-intake-contract-v1-2-device-context.md).

**Blocked by:** None (can start immediately) — the schema stop-rule ask was signed off
at scoping (2026-08-29, ticket 01).

**Status:** ready-for-human — implemented 2026-08-29 (full backend suite green; client
typecheck clean; V7 confirmed applied by a live `bootRun` against the compose Postgres).
Remaining for the developer: the manual UI checklist below, then [ticket 03](03-ship-v1-2-freeze-deploy-handoff.md).

- [x] Migration V7: three nullable ≤200-char columns on `reports`, additive only, all in
      the one migration (V6 precedent: the schema lands once).
- [x] Intake accepts `context.os` / `context.browser` / `context.deviceModel` under
      `screen`'s rule verbatim: optional; absent → null (pre-v1.2 Largata builds must
      keep landing forever); >200 chars → `400` with the dotted envelope key; **no format
      or vocabulary validation of any kind**; a native-platform report sending `browser`
      is stored as sent, never rejected (store-and-forward: every `400` is a silently
      lost report).
- [x] Both intake responses (`201` and the idempotent `200` replay) and `GET /api/reports`
      carry the three fields; screenshots, statuses, notes, idempotency, both auth
      schemes untouched.
- [x] Detail screen renders **one combined Device row** — null parts omitted, the whole
      row omitted when all three are null; list row unchanged.
- [x] Contract tests (the cross-repo pin): with all three · with none (pre-v1.2 shape
      lands and echoes nulls) · each field overlong → `400` with its dotted key · a
      native report sending `browser` stored as sent.
- [x] Live `bootRun` against the compose Postgres confirms V7 actually applied (standing
      silent-Flyway lesson — the test suite alone doesn't prove autoconfigured migration).
      Verified 2026-08-29: Flyway log "Migrating schema public to version 7 - device
      context … Successfully applied", and `\d reports` shows all three columns.
- [ ] Manual UI checklist (standing no-automated-e2e decision): a v1.2 report shows the
      Device row; a pre-v1.2 report renders unchanged (no row); a signed-out v1.2 report
      shows "Signed out" and the Device row together. **Developer's check — note the
      running fullstack-gate container predates this change; rebuild it first
      (`docker compose --profile fullstack up --build`).**
