# 07 — Intake contract v1.1: screen context + signed-out reporters

**What to build:** The wire contract learns where the reporter was. Largata's tracker entry
point is going globally visible — signed-out screens included — so intake now accepts an
optional `context.screen` ("the screen the reporter was on when they opened the report
flow", opaque to worklog, ≤200 chars) and an absent `reporter` (no identity exists signed
out; the inbox shows "Signed out"). Context validation-envelope keys normalise to dotted
`context.*` while nothing consumes the envelope yet. Spec: `../spec.md`, "Amendments →
v1.1"; ADR-011. Demo: curl a report with a screen and no reporter → 201; see it in the
inbox with a Screen row and a "Signed out" reporter.

**Blocked by:** None — amends the shipped Epic 3 surface (01–06).

**Status:** done — tests green (45/45 reports suite), developer verified all three report
shapes live in the browser 2026-08-28 (local bootRun + Expo web; real Flyway V5 run
confirmed in `flyway_schema_history`). Squashed to `dev` (SHA in BUILD_STATUS). Prod
promotion rides the next dev → main cycle; no new env vars anywhere.

- [x] Migration V5: `reports.screen VARCHAR(200) NULL`; `reporter_name` / `reporter_uid`
      drop NOT NULL. No other schema change.
- [x] Intake accepts `context.screen`: stored and echoed; absent → null (older Largata
      builds must keep landing forever); >200 chars → `400` `context.screen`; no format
      validation of any kind (Largata's vocabulary, not worklog's).
- [x] Intake accepts a missing `reporter`, per-field: absent name/uid stored as null,
      `201` as normal; a half-sent identity is stored as sent, never rejected (under
      store-and-forward a `400` is a silently lost report).
- [x] Envelope keys for context fields are dotted: `context.platform`,
      `context.appVersion`, `context.screen` (were flat `platform` / `appVersion`).
- [x] `GET /api/reports` / intake responses carry `screen` and nullable reporter fields;
      screenshots, statuses, idempotency, both auth schemes untouched.
- [x] Contract tests updated in `IntakeEndpointTest` (the cross-repo pin): with-screen,
      without-screen, overlong-screen, signed-out reporter, partial identity, dotted keys.
- [x] Tests green (`*.reports.*` suite against Testcontainers Postgres — V5 applies on a
      clean database every run): 45/45 across the four endpoint classes.
- [x] Inbox renders it: row + accessibility label fall back to "Signed out"; detail gains
      a Screen meta row (verbatim, omitted when null).
- [x] Manual UI checklist (standing no-automated-e2e decision): a signed-out report shows
      "Signed out" in list + detail; a report with `screen` shows the Screen row; a
      pre-v1.1-shaped report renders unchanged. Developer-verified live 2026-08-28
      against three curl-injected reports (plus 200-replay / 400-overlong / 401 probes).
- [x] Docs: spec amended in place + Amendments section; domain model (02) Reporter/Screen
      context; ADR-011 in 04; epic map Story 19; BUILD_STATUS row. The spec remains the
      hand-off artifact for the Largata-side session.
