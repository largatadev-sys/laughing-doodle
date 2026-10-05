# Hand-off — the Largata usage Events contract (for the sender in the Largata repo)

Rewritten 2026-10-05 as a **contract-only** hand-off: what worklog's receiver accepts, answers
and does with each Event, as built and tested on worklog branch
`feature/largata-dashboard-planning`, and checked claim by claim against that code. It
prescribes nothing about Largata's internals (which events exist, where to hook, which
tables) — those are the sender's design calls; section 7's vocabulary is only a suggestion.
Paste everything below the rule into a Claude Code session in the Largata repo, or use it as
your own reference. Section 9's commands run in a worklog checkout, not in the Largata repo.

---

# Worklog Events intake — contract for the Largata sender

You are building the **sender** of Largata usage Events to **worklog**, the team's internal
app. Worklog's receiver is built and contract-tested; it renders a live Dashboard of Largata
usage (Travelers active today, a running total per counter, counts per day / month / year).
Largata decides *what* to send; worklog validates the envelope, never the vocabulary.

## 1. Endpoint

`POST {worklog base URL}/api/intake/events`

| Worklog | Base URL | Has the route? |
| --- | --- | --- |
| Local full image | `http://localhost:8080` (`APP_PORT` moves it; or the LAN address of the machine running it) | yes, when the image was built (`docker compose --profile fullstack up --build`) from the current head of `feature/largata-dashboard-planning`. The gate runs whatever image was last built, so rebuild after pulling. |
| Local `bootRun` (API only) | `http://localhost:8080` (`PORT` moves it) | yes, from that branch |
| Railway dev | `https://largata-ts-dev.up.railway.app` | **not yet** — with dev's secret it answers 404 until worklog Story 27 lands on worklog's `dev` branch (dev auto-deploys from it); with any other secret, 401 |
| Railway prod | `https://worklog.largata.com` | **not yet** — the same, until worklog's `dev` is promoted to `main` |

- Join base URL and path without doubling the slash, and without a path prefix in front of
  `/api` (section 4 explains why a wrong URL can look like "not deployed").
- No trailing slash: `/api/intake/events/` answers 404 with the right secret (401 with a wrong one).
- Same door as Reports: the reports relay already posts to `/api/intake/reports` on the same
  base URL.

## 2. Authentication

- Header **`X-Intake-Secret: <secret>`** — the same shared secret the reports relay already
  sends. There is no new secret. It must match exactly: case-sensitive, with no trimming or
  normalising by worklog.
- Each worklog environment has its own value, so a sender pointed at the wrong environment
  gets 401. Locally, worklog uses whatever `REPORTS_INTAKE_SECRET` is in its own process
  environment: the full image takes it from worklog's `.env`, or uses the placeholder
  `dev-only-placeholder-intake-secret-change-me` when `.env` doesn't set it; `bootRun` uses
  the placeholder only when the variable isn't set at all.
- A wrong or missing secret, or a worklog login token instead of the secret, answers 401:
  `{"error":{"code":"UNAUTHENTICATED","message":"Authentication required","details":{}}}`.
  So does a path under `/api/` that isn't under `/api/intake/` (e.g. a doubled
  `/api/api/intake/events`). Treat a 401 as a configuration error, never as a bad batch.

## 3. Request

Headers: `Content-Type: application/json`, and `Accept: application/json` (or `*/*`, or no
Accept header). Send the body as UTF-8 JSON. Worklog reads the body as raw bytes and doesn't
check Content-Type, but a form-encoded body comes back as a 400 and a multipart or malformed
Content-Type as a 500 — send exactly these headers. An Accept that rules out JSON (e.g.
`text/plain`) fails only when the response is written, after the batch was judged and stored:
you get an error instead of verdicts, and a resend answers `duplicate`. **Unknown fields are
ignored without a verdict**, so a misspelled optional field (e.g. `subjectId` for `subject`)
silently drops that data.

```json
{
  "events": [
    { "eventId": "4f0c2a8e-6c1d-4f7e-9a51-2b7d8e3c9f10",
      "kind": "postcard.created",
      "subject": "0b6f9a2c-1e3d-4c5b-8a7f-9d2e1c3b4a56",
      "occurredAt": "2026-10-05T03:14:15Z" },
    { "eventId": "9a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
      "kind": "snapshot",
      "occurredAt": "2026-10-05T03:00:00Z",
      "totals": { "traveler": 42, "trip": 27, "itinerary": 10, "diary": 19, "postcard": 63 } }
  ]
}
```

**1 to 500 Events per call.**

| Field | Rule |
| --- | --- |
| `eventId` | Required. A UUID string in the canonical 36-character `8-4-4-4-12` hex form, with nothing around it (no braces, no `urn:uuid:`, no whitespace); any version. Hex case isn't significant (uppercase is the same id), but send lowercase (`UUID.toString()`), because the verdict echoes the string exactly as sent. Mint it once, store it with the outbox row, and resend the same id on every retry — it is worklog's idempotency key. |
| `kind` | Required, a JSON string: 1–100 UTF-16 code units (Java `String.length()`; an emoji counts as 2), not blank (empty or whitespace-only counts as missing), no control character (U+0000–U+001F or U+007F). Stored exactly as sent — no trimming, case-sensitive, so `"Snapshot"` or `"snapshot "` is an ordinary kind, not a Snapshot. Opaque to worklog except for the reserved `snapshot` and the suffixes `.created` / `.deleted` (section 6). |
| `subject` | Optional. **The Traveler who did this** — Largata's Traveler UUID, the same identity a Report carries as the reporter's uid. Omit it, or send null, when no Traveler acted. If present it must be a JSON string with no control character (a tab or newline rejects the Event); an empty or spaces-only string is stored as "no Traveler"; otherwise at most 200 UTF-16 code units. Stored exactly as sent, with no trimming or case-folding, so send one consistent form (lowercase canonical) or one Traveler counts as two. Never send an email, a name or a Firebase uid. |
| `occurredAt` | Required, a JSON string: `yyyy-MM-ddTHH:mm[:ss[.fraction]]` (up to 9 fractional digits) plus an offset, `Z` or `±hh:mm` (the colon is required, so `+0100` is rejected). Also rejected: no offset, a date only, a space instead of `T`, a trailing zone name (Java `ZonedDateTime.toString()`'s `…Z[UTC]`), an epoch number. `Instant.toString()` produces a valid value. **It must fall within [2020-01-01T00:00:00Z, worklog's clock + 1 hour], both ends inclusive** (worklog's server UTC clock, read once per batch). Send the moment the change happened, from a correct clock — never a default or sentinel date. |
| `totals` | Only on `kind: "snapshot"` (exact, case-sensitive), where it is required: a JSON object of counter → a plain JSON integer from 0 to 9223372036854775807. Rejected: decimals (`1.0`), exponent forms (`1e3`), numeric strings (`"42"`), null, booleans, negatives, anything larger — serialize counts as plain numbers. Counter names must be non-blank with no control character (no length limit). `{}` is a valid Snapshot. On any other kind `totals` is ignored: neither validated nor stored. |

## 4. Responses

**200 — a verdict for every Event, in request order.**

```json
{ "results": [
    { "eventId": "4f0c2a8e-6c1d-4f7e-9a51-2b7d8e3c9f10", "status": "accepted" },
    { "eventId": "9a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", "status": "duplicate" },
    { "eventId": "not-a-uuid", "status": "rejected", "reason": "eventId.invalid" } ] }
```

- Match verdicts to your rows **by position**, not by `eventId`. `eventId` echoes the raw value
  you sent, and is `null` when it wasn't a string. Accept the response only if `results` has
  exactly as many entries as you sent.
- **`accepted`:** stored.
- **`duplicate`:** this `eventId` is already stored and nothing changed — a replay is safe, so
  treat it like `accepted`. The match is on `eventId` alone and the content is never compared:
  resending a stored id with different content answers `duplicate` and keeps the original, so a
  correction needs a new Event, never a reused id. The same `eventId` twice in one batch answers
  `accepted`, then `duplicate`.
- **`rejected`:** not stored. Every reason depends only on the Event itself, so a retry gets the
  same answer — except an `occurredAt` more than an hour ahead of worklog's clock, which signals
  a clock or code bug on Largata's side, not something to wait out. Log it and mark it dead.

Rejection reasons, from the first failing check (order: eventId, kind, subject, occurredAt,
totals). Log the reason as an opaque string, since the list may grow.

| Reason | When |
| --- | --- |
| `eventId.invalid` | missing, null, not a string, not a canonical UUID, or the element isn't a JSON object |
| `kind.missing` | absent, null, empty or whitespace-only |
| `kind.invalid` | not a string, or contains a control character |
| `kind.tooLong` | over 100 UTF-16 code units |
| `subject.invalid` | present and not a string, or contains a control character |
| `subject.tooLong` | over 200 UTF-16 code units |
| `occurredAt.missing` | absent or null |
| `occurredAt.invalid` | not in the accepted format (section 3), or outside [2020-01-01T00:00:00Z, worklog's clock + 1 h] |
| `totals.invalid` | a Snapshot whose `totals` is missing, null or not a JSON object; or has any value that isn't a plain JSON integer from 0 to 9223372036854775807; or a blank or control-character counter name |

**Other answers, and what the sender should do:**

| Status | Body | Meaning | Sender |
| --- | --- | --- | --- |
| 400 | `{"error":{"code":"VALIDATION_FAILED","message":"Invalid events batch","details":{...}}}` with details `body`: "must be valid JSON" / "must be a JSON object", or `events`: "is required" (missing, null, or not a JSON array) / "must not be empty" / "must be at most 500" | The batch itself is malformed — a sender bug | Dead-letter the batch, log at ERROR. A 400 **without** that envelope is a URL problem: treat it as configuration. |
| 401 | `{"error":{"code":"UNAUTHENTICATED","message":"Authentication required","details":{}}}` | Missing or wrong `X-Intake-Secret`, or a path under `/api/` that isn't under `/api/intake/` | Keep the rows, back off, log at ERROR on the first one, alert after a few |
| 404 | `{"error":{"code":"NOT_FOUND","message":"Resource not found.","details":{}}}` | The route isn't deployed on that worklog yet — **or the URL is wrong**: a trailing slash, a typo under `/api/intake/`, a base URL that drops `/api` or puts a path in front of it, or a method other than POST | **Retry with backoff — never dead-letter.** Today dev and prod answer this. Log at ERROR with the full URL: on an environment where the route is live, a 404 means the URL is wrong. |
| 500 | `{"error":{"code":"INTERNAL","message":"An unexpected error occurred.","details":{}}}` | Usually a database failure: the whole batch rolled back and nothing was stored. An off-contract header (multipart or malformed `Content-Type`) also lands here | Retry the same batch with backoff; the replay answers correctly. If 500s persist, check your headers against section 3, then tell worklog's developer. |
| 502 / 503 / timeout | Railway's own response, not JSON | Redeploy or cold start | Retry the same batch with backoff |

Never parse an error body as verdicts. Worklog never answers 405 (a wrong method is a 404) or
415 (the body is parsed as JSON whatever its `Content-Type`). Error bodies never echo the
request; the only thing echoed back is each Event's `eventId`, in the 200's verdicts.

## 5. Readiness check (writes nothing)

`POST {base}/api/intake/events` with the secret and the body `{"events":[]}`:

- **400 with details `events: "must not be empty"`:** the route is live and the secret is right.
  The probe never reaches the database, so it doesn't prove Events can be stored: if the first
  real batch answers 500, keep retrying (section 4) and tell worklog's developer.
- **404:** the route isn't deployed on that worklog yet, **or** the URL is wrong (trailing
  slash, typo under `/api/intake/`, `/api` missing, or a path in front of `/api`). Check the URL
  against section 1 before concluding it isn't deployed.
- **401:** wrong or missing secret, or a path under `/api/` that isn't under `/api/intake/`.

Use it before switching a Largata environment on, **never sample data**: worklog's Events log is
append-only, with no delete route and no pruning, so every Event sent to a deployed worklog is
permanent. (Worklog's own smoke test posts the same empty body *without* the secret and checks
only for the 401 — a secret-boundary check, not a readiness check.)

## 6. What worklog does with your Events

- **`<counter>.created` / `<counter>.deleted`** add one to / take one away from the running
  total of `<counter>` (e.g. `postcard.created` → the `postcard` tile).
- **`snapshot`** (reserved), sent **hourly**, carries `totals` keyed by counter name. Each
  counter's total = its value in the latest Snapshot that carries it + `.created` − `.deleted`
  dated **strictly after** that Snapshot. So a Snapshot re-bases the totals: a missed or extra
  Event skews a counter the Snapshot carries only until the next Snapshot that carries it
  (about an hour). **A counter no Snapshot carries never self-corrects — put every counter you
  emit `.created` / `.deleted` for into the Snapshot.**
  - An Event dated at or before its counter's baseline Snapshot is treated as already inside
    that Snapshot's number, so late delivery never double-counts the total. A late `.created`
    still shows in the day / month / year chart, in the bucket of its `occurredAt` (not its
    arrival). The charts show `.created` only; `.deleted` moves totals, never a chart.
  - A counter seen only in Snapshots (e.g. `ongoing_trip`) shows at the latest carrying
    Snapshot's value — how to publish a state count with no create/delete Events. It gets a tile
    but no history: its chart reads "Nothing created yet".
  - A counter seen only in Events counts created − deleted since Events began and is not
    re-based until a Snapshot starts carrying it. Until then, deleting things created before
    your sender went live drives it negative, and a lost or doubled Event stays in it. Use this
    only for a counter that truly starts at zero.
  - **The order of keys in the latest Snapshot's `totals` (latest by `occurredAt`) is the order of
    the Dashboard's tiles.** Counters it doesn't list follow alphabetically (events-only
    counters, and counters an earlier Snapshot carried, which keep that old baseline). A tile
    never disappears once seen. The first two tiles are the ones on the Reports-tab strip, so
    keep your two headline counters first in every Snapshot. Build `totals` with an
    insertion-ordered map; if it passes through your database, store it as JSON or text, not
    JSONB (which re-sorts keys). Design order: `traveler`, `trip`, `itinerary`, `diary`,
    `postcard`.
  - **When the newest Snapshot's `occurredAt` is more than two hours behind worklog's clock, the
    Dashboard says "Largata silent since …"** (the Reports strip: "Silent since …"). It's
    measured from `occurredAt`, not arrival, so a Snapshot whose `occurredAt` is already over two
    hours old doesn't clear it. Before the first Snapshot ever, it reads "Largata silent — no
    Snapshot received yet". The Snapshot is the heartbeat; ordinary Events don't count as one.
- **`subject`** = the Traveler who acted. "Active" = distinct subjects across **all** kinds in
  the **UTC** calendar day — the Dashboard's hero number and its active history. The Dashboard
  counts every "today" and every day / month / year bucket in UTC, for every viewer, so an
  Event's UTC `occurredAt` decides its day. So attach a
  subject only to the actor, never to someone acted on. An explicit activity ping (e.g.
  `traveler.active`, debounced per Traveler) is one more kind with a subject.
- **Any other kind** (e.g. `trip.started`) is stored and counts toward "active" when it has a
  subject. Worklog doesn't chart it on its own today, but nothing is lost if a later view wants it.
- **Wording.** Each tile shows the total with the counter's name ("42 travelers"). On the chart,
  `traveler` is the one counter worded specially: its `.created` bars read as sign-ups
  ("traveler signups", "3 signed up"), so emit `traveler.created` only for a real sign-up. Every
  other counter's chart reads "… created". Names are shown with `.` / `_` / `-` turned into
  spaces, case kept, and the last word pluralised by simple English rules (s / es / y→ies)
  unless the value is exactly 1 — `postcard` → "postcards", `diary` → "diaries". So use
  lowercase words and end each name with a regular noun (`ongoing_trip` → "ongoing trips", not
  `trip_ongoing` → "trip ongoings").

## 7. Suggested vocabulary (your call — worklog enforces none of it)

`traveler.created` (sign-up), `trip.created` / `trip.deleted`, `itinerary.created` /
`itinerary.deleted`, `diary.created` / `diary.deleted`, `postcard.created` /
`postcard.deleted`, `traveler.active` (debounced activity ping), and an hourly `snapshot` with
`{ "traveler", "trip", "itinerary", "diary", "postcard" }` — keys exactly equal to the counter
names in the `.created` / `.deleted` kinds.

## 8. Traps that would make the numbers wrong

These follow from the contract, whatever Largata's internals are. A Snapshot re-bases only the
running totals. Every `.created` / `.deleted` also lands permanently in the day / month / year
history, and every subject in the active counts — worklog never corrects or deletes them, so a
double count stays in the history for good.

- **One change, one Event.** If two code paths both emit "created" for one thing (e.g. a copy
  or fork that also triggers the plain create), it counts twice.
- **Cascades.** Deleting a parent without a `.deleted` for each child it removes leaves the
  children's totals too high until the next Snapshot that carries those counters (for a
  counter no Snapshot carries, for good).
- **Two-step removal.** If something can be removed in two steps (e.g. unpublish, then
  destroy), emit `.deleted` once, at the step that makes it stop counting.
- **Retries must reuse the `eventId`.** A fresh id per attempt turns every retry into a double
  count.
- **Emit after commit.** An Event must never describe a change that rolled back.
- **The Snapshot's `occurredAt`** is the instant the counts were taken. Take it at the start of
  the count, don't reuse one instant for two Snapshots, and don't let a backlog delay the
  Snapshot past two hours, or the Dashboard reads "silent".
- **A 404 is not a bad batch** while worklog's route isn't deployed. Dead-lettering on it loses
  Events.

## 9. Testing against worklog (in a worklog checkout)

- **The contract tests are executable examples:**
  `backend/src/test/java/com/largatadev/timesheet/dashboard/EventIntakeEndpointTest.java` (the
  wire contract), plus `DashboardSummaryEndpointTest`, `DashboardSeriesEndpointTest` and
  `DashboardActiveEndpointTest` (what the numbers become). Run them with
  `cd backend && ./gradlew test --tests 'com.largatadev.timesheet.dashboard.*'`. This needs
  Docker (Testcontainers) and a local JDK 25; the build pins a Java 25 toolchain and won't
  download one.
- **Run worklog locally** (needs Docker):
  - **Full image (API + Dashboard):** `docker compose --profile fullstack up --build` at the
    worklog root, then `http://localhost:8080`.
  - **API only:** `docker compose up postgres`, then `cd backend && ./gradlew bootRun` (needs a
    local JDK 25). With no `.env`, both sides use the same default database password and the
    placeholder secret. If worklog has a `.env` (a filled-in copy of `.env.example`), export it
    first so the database password matches (e.g. `set -a; . ../.env; set +a`) — which also sets
    the secret to the `.env` value.
  - **Ports:** `PORT` for `bootRun`, `APP_PORT` for the full image, `DATABASE_PORT` for Postgres
    (keep `.env`'s `DATABASE_URL` in step).
  - **From inside a container,** reach the host as `host.docker.internal` (out of the box on
    Docker Desktop; on Linux add `--add-host=host.docker.internal:host-gateway`).
- **A sample batch — to see a healthy exchange, not a shape to copy:**
  `scripts/send-sample-events.sh <base-url> [--snapshot-age-hours N] [--spread-days N]`. It
  sends one Snapshot (dated now by default; `traveler 40 · trip 25 · postcard 60 · diary 18 ·
  itinerary 9`) and 18 `.created` / `.deleted` / `traveler.active` Events after it, then prints
  the status, one verdict per line, and the counts. Against an empty log the Dashboard then reads
  active 5 · traveler 42 · trip 27 · postcard 63 · diary 19 · itinerary 10. `--replay` (base URL
  first) resends the last batch — all `duplicate` against the same worklog. The secret is
  `$REPORTS_INTAKE_SECRET`, defaulting to the placeholder. Don't copy three things from it: its
  subjects are placeholder strings, not Traveler UUIDs; its Snapshot keys aren't in design
  order; its Events are dated up to a minute after the send. Run it against a local worklog
  only — its Events are permanent.
- **The Dashboard is the end-to-end check.** Use the full image (`bootRun` has no Dashboard
  screen), open `http://localhost:8080` and log in as a worklog user (ask worklog's developer
  for the local credentials). The intake secret does not open the Dashboard. There's no
  per-Event lookup: a postcard you create in Largata shows as the `postcard` tile ticking up
  within about a minute, and a replay changes nothing on screen.

## 10. Out of scope for the sender

Nothing on worklog's side changes for this: no new secret or variable, no alerts, no retention,
no per-kind count endpoint, no changes to the reports relay or its envelope. Worklog sends no
alerts when you go quiet, so the "silent" state on the Dashboard and your own logs are the only
alarms.
