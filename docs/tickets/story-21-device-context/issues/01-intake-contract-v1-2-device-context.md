# 01 — Intake contract v1.2: device context (os, browser, deviceModel)

**What to build:** The wire contract learns what the reporter was running. Intake accepts
three new optional `context` fields — `os`, `browser`, `deviceModel` — opaque strings
≤200 chars each, stored and displayed verbatim exactly like `screen` (worklog never parses
a user-agent). Requested by the developer 2026-08-29: problem triage wants "which browser,
if a browser — which OS — or was it the native app". Native-vs-browser is already
answerable today via `context.platform` (`web` vs `android`/`ios`, shown on every inbox
row and detail); this story adds the rest. Spec: `../../reports-inbox/spec.md`,
"Amendments → v1.2" — the spec stays in `reports-inbox/` permanently as the
single Largata hand-off artifact; this folder is Story 21's. Demo: curl a report with all
three → 201, see the combined Device row in the detail screen; a pre-v1.2-shaped report
renders unchanged.

**Blocked by:** None for worklog's half. The *visible payoff* is blocked on a
Largata-side session: intake is server-to-server, so the `User-Agent` worklog sees is
Largata's backend, not the reporter's device — only Largata's client can capture these,
at report time. Nothing can be back-filled: every report that arrives before the Largata
half ships stays blank on these fields forever (which is why all three wanted fields go
in one bump rather than trickling in across v1.2/v1.3).

**Implementation sliced 2026-08-29:**
[02 — tracer bullet, three fields end-to-end](02-device-context-tracer-bullet.md) →
[03 — ship: freeze the contract, deploy, hand off](03-ship-v1-2-freeze-deploy-handoff.md).
This ticket stays the scoping record; the checklist below is the contract-level
definition those slices implement — work the slices, reconcile this checklist at ship.

**Status:** done — implemented 2026-08-29 via [02](02-device-context-tracer-bullet.md) →
[03](03-ship-v1-2-freeze-deploy-handoff.md); every checklist item below is satisfied
(ADR-013 records the decision). Kept as the scoping record.

Originally: grilled and signed off by the developer 2026-08-29
(field set + the additive V7 shape below = the schema stop-rule ask; then a six-decision
grill round settled os shape, flat envelope, field-set completeness, validation stance,
rendering, and the glossary term — all as recorded here and in the spec); implementation
deliberately deferred to a later session (developer call the same day: spec + ticket
only for now, no schema change yet). The live wire contract stays **v1.1** until this
story's worklog half ships.

- [x] Migration V7: `reports.os VARCHAR(200) NULL` · `reports.browser VARCHAR(200) NULL`
      · `reports.device_model VARCHAR(200) NULL`. Additive only — no `ALTER` of any
      existing column, nothing dropped.
- [x] Intake accepts `context.os` / `context.browser` / `context.deviceModel`: each
      optional, ≤200 chars (matching `screen` — one rule for every optional context
      string), stored and echoed; absent → null (pre-v1.2 Largata builds must keep
      landing forever); >200 chars → `400` with the dotted envelope key (`context.os`,
      `context.browser`, `context.deviceModel`); **no format or vocabulary validation of
      any kind** — never a whitelist of browsers or OSes (Largata's vocabulary, not
      worklog's).
- [x] Field semantics (the Largata-side build reads this): `os` = OS name **and**
      version as Largata formats it ("Windows 11", "Android 14", "iOS 17.5") — one field,
      not `osVersion`, because for web reporters the OS *name* is the payload (deliberate
      deviation from the epic-map sketch, which presumed the name was already known);
      `browser` = browser name + version ("Chrome 128"), expected only when
      `platform="web"` — but a native build sending one is stored as sent, never a `400`
      (under store-and-forward a `400` is a silently lost report); `deviceModel` =
      whatever the platform API gives ("Samsung SM-S918B", "iPhone15,3"). All captured on
      the reporter's device at report time.
- [x] `GET /api/reports` and both intake responses (201 + idempotent 200 replay) carry
      the three fields; screenshots, statuses, notes, idempotency, both auth schemes
      untouched.
- [x] Contract tests in `IntakeEndpointTest` (the cross-repo pin): with all three ·
      with none (pre-v1.2 shape lands and echoes nulls) · each field overlong → `400`
      with its dotted key · a native-platform report sending `browser` is stored as sent.
- [x] Inbox renders them as **one combined Device row** in the detail metadata block —
      "Chrome 128 · Windows 11 · Pixel 6" — null parts omitted, the whole row omitted
      when all three are null (grill call 2026-08-29: the three facts are consumed
      together, and Story 20's rework keeps the reporter's words dominant — three more
      label rows works against that). List row unchanged — it already carries
      platform + version and is dense.
- [x] Live `bootRun` against the compose Postgres confirms V7 actually applied (standing
      silent-Flyway lesson — the test suite alone doesn't prove autoconfigured migration).
- [x] Manual UI checklist (standing no-automated-e2e decision): a v1.2-shaped report
      shows the Device row; a pre-v1.2 report renders unchanged (no row); a signed-out
      v1.2 report shows both "Signed out" and the Device row together.
- [x] Docs: spec wire-contract section edited in place to v1.2 + the "planned" amendment
      marked implemented; ADR (next free number) recorded in 04; domain model (02): drop
      the "not yet built" marker from the **Device context** entry (the term itself
      landed at scoping); epic map (retire the unscheduled-candidate entry); BUILD_STATUS. Then hand
      the amended spec to the Largata-side session — it is the starting input for the
      capture half.
