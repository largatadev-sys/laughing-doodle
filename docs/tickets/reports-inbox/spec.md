# Spec — Reports inbox (Largata feedback → worklog)

Status: ready-for-agent
Date: 2026-08-13
Amended: 2026-08-28 — contract **v1.1** (screen context + signed-out reporters), grilled
and signed off in-session (the schema change's stop-rule ask). See "Amendments" at the end;
the contract sections below are edited in place to v1.1 — this spec stays the single
hand-off artifact for the Largata repo.
Origin: grilling session 2026-08-13 (design confirmed by the developer; the confirmation
is the explicit sign-off both stop-rules require — new schema, new authenticated surface).
Scope: **worklog's half only.** The Largata-side half (report form, accept endpoint,
store-and-forward relay) is built in the Largata repo against the wire contract below.

## Problem Statement

The team's other product — **Largata**, the collaborative trip-planning app at largata.com —
is in the hands of real users, but those users have no way to tell the team when something
is broken or could be better. There is no feedback mechanism in Largata at all (no crash
reporting, no feedback form), so problems surface only if a user happens to know a founder
personally. Meanwhile the team already lives in worklog daily to log time, but worklog has
no surface where such feedback could land. Feedback is currently lost.

## Solution

Largata users get a "report a problem / suggest an idea" flow inside the Largata app
(that half is Largata-repo work). Each **Report** — type, description, reporter identity,
platform/app-version context, up to 3 optional screenshots — travels from the user's phone
to Largata's backend, then server-to-server into worklog through a shared-secret intake
endpoint. In worklog, Reports land in a new **Inbox**: a fifth tab (bug icon) beside
Home · Calendar · ＋ · Profile, with a badge counting untouched reports. Any of the four
Members can read every report and move it through a small status lifecycle
(`new → discuss → in progress → done`, or `dismissed`), so the team always knows what
users are hitting and what's been dealt with. Reporters are fire-and-forget: they get an
instant thank-you in Largata and never see worklog.

## User Stories

1. As a Largata traveler, I want to report a problem from inside the app, so that the team learns about bugs without me needing their contact details.
2. As a Largata traveler, I want to suggest an improvement the same way, so that my ideas reach the people who build the app.
3. As a Largata traveler, I want to pick only between "something's wrong" and "I have a suggestion", so that I'm never forced to classify my feedback into categories I don't understand.
4. As a Largata traveler, I want to attach up to 3 screenshots to my report, so that I can show the problem instead of describing it.
5. As a Largata traveler, I want to submit without screenshots, so that a quick suggestion isn't blocked by ceremony.
6. As a Largata traveler, I want an instant confirmation when I submit — even if worklog happens to be down — so that reporting a bug never itself looks buggy.
7. As a Largata traveler, I want my report to carry my name automatically, so that I don't fill in identity fields the app already knows.
8. As a worklog Member, I want a Reports tab in the tab bar, so that incoming feedback is a first-class surface I check like Home or Calendar.
9. As a worklog Member, I want a badge on the Reports tab counting reports nobody has touched, so that new feedback is visible from any screen without opening the tab.
10. As a worklog Member, I want the badge to clear only when reports are actually triaged out of `new` — not merely by my opening the tab — so that the count stays honest for the whole team.
11. As a worklog Member, I want the inbox list newest-first showing each report's type, a text snippet, the reporter's name, platform + app version, when it was submitted, and its status, so that I can scan arrivals at a glance.
12. As a worklog Member, I want to filter the list by status, so that I can see only what's open, or review what was dismissed.
13. As a worklog Member, I want to open a report to read its full description and view its screenshots, so that I can understand exactly what the user experienced.
14. As a worklog Member, I want to move any report between statuses (no ownership), so that whoever is free can triage — same shared-visibility spirit as the rest of worklog.
15. As a worklog Member, I want a `discuss` status ("For discussion"), so that reports needing a founders' decision are parked on a visible agenda rather than sitting ambiguously in `new`.
16. As a worklog Member, I want to `dismiss` reports we won't act on, so that the open list stays meaningful without deleting anything.
17. As a worklog Member, I want to see who last changed a report's status and when, so that triage actions are attributable without needing comments.
18. As a worklog Member, I want reports kept forever, so that old feedback remains searchable history rather than disappearing.
19. As a worklog Member, I want screenshots stored by worklog itself, so that the inbox keeps working even when Largata is down or changes.
20. As a worklog Member, I want the report to carry the platform and app version it came from, so that "which build did this happen on?" never needs a follow-up question nobody can send (reporters are unreachable by design).
21. As the developer, I want the intake endpoint to reject every caller except Largata's backend, so that the public internet can never write into — or flood — the team's inbox.
22. As the developer, I want intake to be idempotent, so that Largata's delivery retries can never create duplicate reports.
23. As the developer, I want reporters kept entirely out of worklog's `users` table and auth system, so that INV-2 and the login surface stay exactly as they are.

## Implementation Decisions

### Domain (vocabulary in the domain model doc, "Incoming feedback (Reports)")

- One new entity, **Report**: client-minted UUID id (the idempotency key) · type
  (`problem | idea`) · description (required, 1–2000 chars, no title field) · reporter
  name + reporter uid (opaque strings from Largata — **foreign identity as data**, never a
  worklog User; **both optional since v1.1** — absent when filed from a signed-out screen)
  · platform (`android | ios | web`) · app version · screen (optional, ≤200 chars — v1.1,
  opaque) · `submittedAt` (stamped
  by Largata when the user submitted) · `receivedAt` (stamped by worklog on arrival —
  store-and-forward means these differ) · status · status-changed-by (worklog user) +
  status-changed-at · 0–3 screenshots.
- **Statuses:** `new` (arrived, untouched) · `discuss` (UI label "For discussion" —
  parked for a founders' decision) · `in_progress` · `done` · `dismissed` (won't act).
  Free movement between any statuses by any Member; no enforced transitions. No
  assignment, no deletion — Reports are permanent. ~~No comments~~ (**superseded
  2026-08-29:** team **Notes** — append-only log, author-editable with a visible
  stamp — see Amendments; threaded/reporter-visible comments stay excluded).
- Reports have **no owner**; every Member reads and updates equally. INV-1–INV-5 untouched.
- Display and sort use `submittedAt` (honest to the user's action; retry delivery must not
  reorder), newest first.

### Schema (new migration)

- `reports` table: the fields above; primary key is the client-minted UUID (uniqueness
  enforces idempotency at the database); status as a checked varchar like `users.role`;
  status-changed-by references `users(id)`, nullable (null until first triage).
- `report_screenshots` table: report reference · ordinal (0–2) · content type ·
  image bytes (`bytea`). **Bytes live in Postgres** — at a 4-person team's feedback
  volume this avoids standing up object storage; reports and screenshots back up with
  the database as one unit. Revisit only if volume embarrasses it.

### Wire contract (the fixed cross-repo interface — Largata builds to exactly this)

- **`POST /api/intake/reports`** — multipart/form-data:
  - part `report` (application/json): `{ "reportId": "<uuid>", "type": "problem"|"idea",
    "description": "<1–2000 chars>", "reporter": { "name": "...", "uid": "..." },
    "context": { "platform": "android"|"ios"|"web", "appVersion": "...",
    "screen": "<optional, ≤200 chars>" }, "submittedAt": "<ISO-8601 instant>" }`
  - The `report` part must be a **file** part — a filename in its `Content-Disposition`
    (curl: `-F "report=@report.json;type=application/json"`). A bare form field is
    rejected as a missing part (found the hard way during the v1.1 live check).
  - `reporter` (v1.1): **optional, per-field** — absent when the report was filed from a
    signed-out screen (sign-in, onboarding, invite links). Name and uid are independently
    optional and stored as sent: a half-sent identity is a Largata bug, never a `400` —
    under store-and-forward a `400` is a silently lost report. The inbox shows a missing
    name as "Signed out".
  - `context.screen` (v1.1): **optional, ≤200 chars** — *the screen the reporter was on
    when they opened the report flow* (not where the bug happened — no client can know
    that). How Largata identifies a screen (route pattern vs. human label) is that repo's
    call, made against this sentence; worklog treats the value as opaque — no format
    validation, never checked against a route table, rendered verbatim in the inbox.
    Capture it when the report flow *opens*: read at submit time, a pushed report route
    would record the report form itself, every time.
  - parts `screenshot` (0–3 files): JPEG/PNG, ≤ 5 MB each (Largata sends its sanitized,
    downsized display variant — EXIF-stripped, ≤2048px).
- **Auth:** header `X-Intake-Secret: <secret>` — a long random value in an env var on both
  sides (`REPORTS_INTAKE_SECRET`), compared constant-time, never logged, rotated by
  redeploy. Missing/wrong secret → `401` with the standard error envelope. This is the
  API's **second and only other** exception to bearer-JWT auth (after login) — recorded
  as ADR-010.
- **Idempotency:** first accept of a `reportId` → `201` with the report; any replay of the
  same `reportId` → `200` with the already-stored report, no second row, no screenshot
  re-write. Validation failure → `400` with the field-level envelope; oversized/malformed
  images → `400`. Envelope keys for context fields are dotted (v1.1): `context.platform`,
  `context.appVersion`, `context.screen` — matching `reporter.name` / `reporter.uid`.
- Delivery guarantees are Largata's job (store-and-forward with retry until a 2xx);
  worklog's job is only that replays are safe.

### Team-facing API (bearer-JWT, standard conventions)

- `GET /api/reports` — all reports, newest-first by `submittedAt`; optional `?status=`
  filter. Returns the full report minus image bytes (screenshots as a count/ordinal list).
- `PUT /api/reports/{id}/status` — body `{ "status": "..." }`; stamps status-changed-by
  from the JWT identity (never the body) and status-changed-at; → `200` updated report.
  Unknown id → `404`; bad status value → `400`.
- `GET /api/reports/{id}/screenshots/{ordinal}` — streams the image bytes with its content
  type to authenticated Members. Unknown → `404`.
- No counts endpoint: the client derives the tab badge from the fetched list (volume is
  trivial; refetch on app foreground and tab focus).

### Backend shape

- New `reports` module beside `auth`/`entries`/`users`, same layering: controller
  (HTTP + validation) → service (logic) → repository (persistence).
- The intake route is opened in the security configuration as its own matcher with a
  dedicated secret check **before** the JWT filter chain; every `/api/reports/**` team
  route stays behind bearer JWT as normal. INV-2's enforcement code is untouched.
- Multipart limits configured to accommodate 3 × 5 MB + JSON part, and to reject beyond.

### Client shape

- Fifth tab **Reports** (bug icon) in the pill — order: Home · Calendar · ＋ · Reports ·
  Profile — with the `new`-count badge, wired through the existing headless tab bar and
  its crossfade transition; badge rendering respects the existing motion/reduced-motion
  language.
- Inbox screen: status filter chips (~~default: everything open — `new` + `discuss` +
  `in_progress`~~ — **superseded 2026-08-29:** the screen lands on **New**, though
  clearing the chip still shows everything open; see Amendments → "Team notes + inbox
  clarity"), report cards (type
  glyph, snippet, reporter, platform + version, submitted time, status pill),
  Largata-brand styling.
- Drill-in report detail (same slide pattern as the day drill-in): full description,
  screenshot viewer, metadata block, and the status control. Status changes are the only
  write this surface has.

## Testing Decisions

- **Seam: the existing backend API integration layer** — HTTP-level tests with MockMvc +
  Testcontainers against real Postgres, in the established `*EndpointTest` style
  (prior art: `CreateEntryEndpointTest`, `ChangePasswordEndpointTest`,
  `JwtFilterChainTest`). No new seams anywhere; no unit-testing of internals — tests
  assert external behavior (status codes, envelope shapes, persisted effects) only.
- **Mandatory security coverage** (the 06b test-depth rule extends to the new surface):
  - intake with no / wrong `X-Intake-Secret` → `401`, nothing persisted;
  - intake with a valid secret but malformed payload (missing type, empty/oversized
    description, >3 screenshots, non-image part) → `400` envelope, nothing persisted;
  - team routes (`GET /api/reports`, `PUT .../status`, screenshot read) with no/invalid
    JWT → `401`;
  - a valid JWT cannot reach intake without the secret (the two auth schemes don't bleed).
- **Idempotency:** same `reportId` posted twice → one row, `201` then `200`, byte-identical
  stored screenshots.
- **Round-trip:** multipart in → list shows the report → screenshot endpoint returns the
  same bytes/content type.
- **Status flow:** any-to-any transitions succeed; changed-by comes from the JWT identity,
  never the body; `submittedAt` ordering is stable under out-of-order arrival.
- **Client:** no automated e2e (standing 06b decision) — manual UI checklist in the
  implementation tickets, as every story since 7a; there is no tricky pure logic here
  warranting a node repro.
- The intake tests **are** worklog's contract tests: they pin the wire contract the
  Largata repo builds against.

## Out of Scope

- **Everything Largata-side:** the report form UI, its accept endpoint, screenshot
  sanitization, the store-and-forward outbox and retry loop. Separate repo, separate
  session, built to the wire contract above.
- Reporter feedback loop (no status back to the reporter, no "my reports" screen),
  ~~comments~~ (**team Notes in scope since the 2026-08-29 amendment** — threaded /
reporter-visible comments stay out), assignment, priorities, labels beyond status,
report deletion or editing,
  push/email notifications, ~~capture of which screen the reporter was on~~ (**in scope
  since v1.1**, 2026-08-28 — see Amendments), worklog-side
  Firebase anything, a public/unauthenticated worklog endpoint of any kind, object
  storage, pagination (volume is trivial; conventions define the pattern when needed),
  admin-only gating of any inbox action.

## Further Notes

- **Stop-rules:** the schema change and the new authenticated surface were explicitly
  signed off by the developer at the end of the grilling (2026-08-13). INV-2 and its
  tests are untouched; the intake secret is a **second security surface** and is why the
  security tests above are mandatory, not optional.
- **ADR-010** (relay-only intake) records why there is no public endpoint and no Firebase
  in worklog; the domain vocabulary lives in the domain model doc under "Incoming
  feedback (Reports)".
- **Bookkeeping when this ships:** new epic section + story rows in the epic map and
  BUILD_STATUS; `REPORTS_INTAKE_SECRET` joins the env documentation (`.env.example`
  placeholder only — never a real value in the repo) and the Railway variables at deploy.
- **Hand-off artifact for the Largata repo:** the "Wire contract" section above is the
  interface. The Largata-side session should receive this spec (or that section verbatim)
  as its starting input.

## Amendments

### v1.1 — Screen context + signed-out reporters (2026-08-28)

Grilled 2026-08-28; the schema change was signed off by the developer in-session (the
stop-rule ask). Trigger: Largata plans a **globally visible** tracker entry point — every
screen, signed-out ones included — and wants each report to carry where the user was.
Both halves were still soft (the Largata relay is unbuilt), so the loosenings below are
non-breaking; they freeze once that repo ships. Recorded as **ADR-011**; story/ticket:
Story 19 / [ticket 07](issues/07-intake-contract-v1-1.md).

- **`context.screen` — new, optional, ≤200 chars.** Defined as *the screen the reporter
  was on when they opened the report flow* — captured at open, not submit (a pushed report
  route read at submit records the report form itself); and not "where the bug happened",
  which no client can know. Opaque to worklog: no format validation, never checked against
  a route table — a Largata route rename must never cost a report or force a worklog
  deploy. Whether Largata sends a route pattern or a human label is that repo's decision.
  Absent → stored null; the inbox simply omits the row.
- **`reporter` — now optional, per-field.** Signed-out screens (sign-in, verify-code,
  onboarding, `join/[token]`) have no identity to send, and they are precisely where
  "I can't get in" bugs live. Name and uid are independently optional, stored as sent —
  a half-sent identity is never a `400`, because under store-and-forward every `400` is a
  silently lost report (user story 6). The inbox renders a missing name as "Signed out".
  **Rejected:** Largata synthesizing an anonymous identity (device id + placeholder name)
  — it keeps the contract frozen but plants fake reporters in permanent, undeletable data,
  indistinguishable from real ones the day anyone counts distinct reporters.
- **Envelope keys normalised.** Context-field validation details are now dotted —
  `context.platform` / `context.appVersion` / `context.screen` — matching
  `reporter.name` / `reporter.uid`. Free while nothing consumes the envelope; frozen the
  moment Largata ships error handling against it.

Schema: migration `V5` — `reports.screen VARCHAR(200) NULL`; `reporter_name` /
`reporter_uid` drop `NOT NULL`. Statuses, idempotency, screenshots, both auth schemes:
untouched. The intake tests remain the contract tests and pin all of the above.

### Team notes + inbox clarity (2026-08-29)

Grilled 2026-08-29; the schema change (new `report_notes` table) was signed off by the
developer in-session (the stop-rule ask). Trigger: real triage usage — statuses record
*where* a report stands, but the decisions behind the moves (`discuss` outcomes,
`dismissed` rationale) were evaporating into chat; and on the detail screen the reporter's
words sat visually subordinate to the metadata around them. **The wire contract is
untouched and stays v1.1** — everything here is worklog-team-facing; no Largata
coordination. (This amendment is deliberately *not* numbered v1.2: amendment numbers here
have tracked the intake contract, and the next contract bump — device context, below — is
the real v1.2.) Recorded as **ADR-012**; story/ticket: Story 20 /
[ticket 08](issues/08-report-notes-and-inbox-clarity.md).

- **Note — new entity.** Team-authored prose on a Report. The log is **append-only** — a
  Note is never deleted, matching "Reports are permanent" — but each Note's **text is
  editable by its author** (revised the same day, see below), always with a visible
  edited-at stamp (a silent edit would let a decision quietly become a different one).
  Author/editor and timestamps stamped server-side from the JWT, never the body. Body
  1–2000 chars (mirrors description). Any Member may *write* on any report in any status;
  free-floating from status changes; oldest-first. Convention, not code: a *changed
  decision* gets a new Note — edits are for typos and clarity.
  **Revised 2026-08-29 (same day, pre-merge): editing is author-only.** This first read
  "editable by any Member, safeguarded by an edited-by stamp". On first sight of the built
  screens the developer reversed it: the ledger renders as attributed per-person entries
  rather than the single shared notes field they had pictured, which makes a Note read as
  **signed testimony** — nobody writes under someone else's name. A non-author's edit is
  `403`, the same answer INV-2 gives for another Member's time entry, so the app has one
  ownership rule instead of two. Cost accepted: a typo in a teammate's note needs them, or
  a follow-up Note.
  **Rejected:** a single mutable notes field (last-writer-wins destroys attribution — the
  point was that decisions survive); strict immutability (developer call: typo friction
  outweighs tamper-evidence among four founders); any-Member editing with an edited-by
  stamp (the original call, reversed above); a unified status+notes activity log
  (needs status *history* the schema doesn't keep — a visible non-decision).
  **Comments stay excluded:** no threads, no replies, and no Note is ever
  reporter-visible.
- **Team API (bearer-JWT, standard conventions).** `GET /api/reports` embeds each
  report's notes oldest-first (id, body, author name, `createdAt`, editor name +
  `editedAt` when edited). `POST /api/reports/{id}/notes` `{ "body": "..." }` → `201`
  created Note; `PUT /api/reports/{id}/notes/{noteId}` `{ "body": "..." }` → `200`
  updated Note (**author-only**; another Member's note → `403`). **No DELETE endpoint
  exists.** Unknown ids → `404`; empty/oversized body → `400`, envelope key `body`.
  The intake route learns nothing about notes.
- **Schema: migration V6** — `report_notes`: server-generated UUID PK · report FK ·
  author FK `users(id)` NOT NULL · body ≤2000 · `created_at` · `edited_by` FK
  `users(id)` NULL · `edited_at` NULL. No change to `reports`.
- **Inbox freshness: focused polling.** ~60s refetch while the Reports tab is focused,
  on top of the existing focus/foreground refetch. Client-only. **Rejected:** SSE (React
  Native has no native EventSource; browser EventSource can't send the bearer header;
  Railway sleep severs long-lived connections) and WebSockets (the largest backend
  surface for one-way data). Push latency is theater downstream of a store-and-forward
  intake that lags by Largata's retry backoff — the delivery lag itself is
  [ticket 09](issues/09-ingest-lag-measure-then-tune.md), and no inbox feature fixes it.
- **Screen rework (same theme, zero new tokens):** the detail screen elevates the
  reporter's words to the primary block (testimony scale), surfaces the status pill in
  the top row, and adds the Notes section; the list row gains a two-line snippet and a
  note count. Full design direction lives in ticket 08.
- **Open filter defaults to New** (added 2026-08-29 from the developer's review of the
  live build). Landing on the full open list made the unselected chip row look like
  nothing was filtering when in fact it was showing a superset. The inbox now opens on
  **New** — what the inbox is *for*: untriaged feedback that still needs someone — and
  returning to the Open segment resets to New. The chips keep their toggle behaviour, so
  clearing the active chip still widens to every open report; that view is now something
  you choose rather than where you land. Client-only; the badge still counts `new` from
  the data, not from the filter.
  **Rejected:** a dedicated "All open" chip with strict single-select (built first, then
  removed at the developer's call — an extra chip to name a state that untoggling already
  reaches, on a row that only has three).
- **Picking a status is acknowledged instantly** (same review). The sheet used to show
  only a spinner while the write was in flight, so the tap itself looked unregistered:
  now the chosen row highlights, its dot springs, and a tick fades in the moment you
  press — the spinner that follows means "still saving", not "did that register?".
  A failed move also surfaces **in the sheet**: the screen's error line sits behind the
  modal, so before this a failed status change was invisible.
- **Deferred, explicitly:** device context on reports — wanted for problem triage, but it
  moves the wire contract (**that** is the reserved v1.2) and needs a Largata-side
  session. Single record, with the scoping notes:
  [07 — epic map](../../design/07-epic-map.md), "Unscheduled Epic 3 candidates".
