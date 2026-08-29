# BUILD_STATUS — Team Timesheet

**What this is.** The live map of what's built and the first thing a cold session reads.
Derived from the epic map ([07](docs/design/07-epic-map.md)); maintained live during the build.

**Source-of-truth index.**

- Design artifacts (what the system _is_ & why) → [docs/design/](docs/design/) — current, curated.
- Story plans (why each piece was built that way) → `docs/plans/` _(created per story; immutable, point-in-time)_.
- Commits (what literally changed) → git history — complete but unranked; consult only when the curated record has a hole.

**On session start:** read this, then verify against the code — **code wins**; flag mismatches.

---

## Story table — MVP epic: _Log & see team time_

Key: ⬜ not started · 🔄 in progress · ✅ done · ⚠ blocked

| #   | Story                                           | Status | Plan                                                    |
| --- | ----------------------------------------------- | ------ | ------------------------------------------------------- |
| 1   | Walking-skeleton base: schema + health          | ✅     | [plan](docs/plans/story-1-walking-skeleton.md)          |
| 2   | Auth: login + JWT filter + seeded users         | ✅     | [plan](docs/plans/story-2-auth.md)                      |
| 3   | Create a time entry                             | ✅     | [plan](docs/plans/story-3-create-time-entry.md)         |
| 4   | List entries (shared read, filterable)          | ✅     | [plan](docs/plans/story-4-list-entries.md)              |
| 5   | Edit own entry (ownership enforced)             | ✅     | [plan](docs/plans/story-5-edit-own-entry.md)            |
| 6   | Delete own entry (ownership enforced)           | ✅     | [plan](docs/plans/story-6-delete-own-entry.md)          |
| 7a  | Expo client: scaffold + login + my-entries list | ✅     | [plan](docs/plans/story-7a-expo-scaffold-login-list.md) |
| 7b  | Expo client: create/edit/delete my entries      | ✅     | [plan](docs/plans/story-7b-expo-create-edit-delete.md)  |
| 8   | Team feed (shared visibility view)              | ✅     | [plan](docs/plans/story-8-team-feed.md)                 |
| 9   | Deploy to prod (skeleton goes live)             | ✅     | [plan](docs/plans/story-9-deploy.md)                    |

_(Deploy story 9 may be pulled forward after story 3 for an early thin skeleton — see 07.)_

**🟢 Live (prod):** <https://worklog.largata.com> (custom domain, CNAME → Railway; the
underlying <https://largata-ts.up.railway.app> still answers) — Railway, single-origin
bundled image (Expo web + Spring API) + managed Postgres, HTTPS (ADR-008). MVP epic complete
end-to-end. **Prod tracks the repo's `main` branch** (Railway service connected to GitHub
`main`) — promoting `dev` → `main` and pushing is the release act, so the pre-deploy ritual
(local fullstack gate + `scripts/smoke.sh`) belongs **before the push to `main`**, and the
post-deploy smoke + live check after it. **A `dev` Railway environment also exists**
(<https://largata-ts-dev.up.railway.app>, auto-deploys from the `dev` branch) — added after the
runbook was written, which still says there is no staging. Smoke `dev` before promoting.
Deploy runbook:
[docs/deploy/railway.md](docs/deploy/railway.md); pre/post-deploy check: `scripts/smoke.sh`.
**In active team use since ~mid-July 2026** (developer-confirmed 2026-08-13).

---

## Story table — Epic 2: _Largata UX & mobile visual identity_

Post-MVP presentation-layer epic (see [07](docs/design/07-epic-map.md)) — home → social-style
team feed, team view → Outlook-style calendar, all in the Largata brand on a mobile-native,
phone-portrait target. **No schema / auth / INV-2 change.**

Key: ⬜ not started · 🔄 in progress · ✅ done · ⚠ blocked

| #   | Story                                                        | Status | Plan |
| --- | ------------------------------------------------------------ | ------ | ---- |
| 10  | Largata redesign: brand system + tab shell + feed + calendar | ✅     | —    |
| 11  | Self-service account: change name + password                 | ✅     | —    |
| 12  | Entry activity label: "logged / edited · when" + created-sort | ✅     | [plan](docs/plans/story-12-entry-activity-label.md) |

Brand tokens live in `client/src/theme/`; work is on `feature/10-largata-ux-redesign`. Story 10
also now carries: a **calendar date picker** (replaces free-text date), **motion** (tally bars
grow via `scaleX`; staggered `FadeInView` entrances; hover/press states — all `prefers-reduced-motion`
aware), **web optimisation** (centered phone-column `WebFrame`, pointer/hover affordances), a
a shared **`AppHeader`** with a header **Log out** (the user's name shows in the greeting), and the
**RN-web scroll fix** + a thin on-brand web scrollbar. Verification + current status: see the note
below.

**Story 11** (self-service **change name + password**) was developer-approved despite the auth
stop-rule; it adds only authenticated endpoints (`PUT /api/auth/password`, `PUT /api/auth/name`) —
no SecurityConfig/JWT/INV-2 change — covered by `ChangePasswordEndpointTest` + `UpdateNameEndpointTest`
(passing). A rename returns the updated profile so the client refreshes the session/header in place.

**Story 12** (entry **activity label**) — ✅ _done, verified live on the local parity image_. The card
caption reads `logged · <when>` / `edited · <when>`: "edited" (keyed to `updated_at`) once an entry is
changed, else "logged" (`created_at`); same-viewer-day shows relative time, any other day shows the
**date** (never "days ago"); list order stays by `created_at`. **Client only — no schema/API/auth/INV-2
change** (`EntryResponse` already returns both timestamps). `relativeTime` → `activityLabel`
([datetime.ts](client/src/lib/datetime.ts)); [EntryCard](client/src/components/EntryCard.tsx) renders
`{verb} · {when}`. Verified by a `+10`-TZ Node repro across the spec table **and** by the developer live
against `docker compose --profile fullstack` (confirmed the served bundle carries the new logic). This
**supersedes** the interim `relativeTime` fallback tweak from the timezone work (that edit is gone —
`relativeTime` removed); the **seed-data timestamp fixes stand** (separate `chore(scripts)` commit).
Plan + [ticket](docs/tickets/story-12-entry-activity-label/issues/01-activity-label-and-created-sort.md).

**Web scroll fix (Story 10, RN-web):** react-native-web's `ScrollView` doesn't bound inside these
nested-flex screens (its scroller keeps a content-sized min-height and grows instead of scrolling),
so nothing scrolled in a browser. Replaced with a `Scroll` component: real `ScrollView` on native;
on web an absolutely-positioned overflow region inside a `flex:1` wrapper (zero in-flow content →
bounds correctly). Diagnosed + verified by driving the live app with a headless browser
(`puppeteer-core`), not just screenshots.

All of the above was **verified live** — by a headless-browser drive (login, scroll, calendar, date
picker, change-name, change-password) **and** by the developer in a real browser against the local
single-origin image. **Merged to `dev`, promoted to `main` (both at `311c100`), and deployed to
prod** — confirmed 2026-08-13 by probing the served JS bundle (Epic 2 markers `activityLabel` /
`TabTransition` present) **and, decisively, by the developer: the team has been using the app in
real day-to-day work and it holds up.** That real-usage confirmation satisfies the
deploy-verification standing rule. **Validation signal: fired** — the backlog epics in
[07](docs/design/07-epic-map.md) are no longer gated on "post-validation".
_(Still to confirm in this record: whether `scripts/rename-users.sql` and the seeded-password
rotation were run against prod — see the railway.md runbook step 6.)_

## Story table — Epic 3: _Reports inbox (Largata feedback → worklog)_

Users of the sibling product **Largata** report problems/ideas in-app; Reports relay into a
worklog Inbox (fifth tab) for team triage. Design closed 2026-08-13 — spec, wire contract,
and tickets: [docs/tickets/reports-inbox/](docs/tickets/reports-inbox/); ADR-010. Worklog's
half only (the Largata-side relay is that repo's work). **New schema + a shared-secret
intake surface — both stop-rules explicitly signed off by the developer 2026-08-13.**

Key: ⬜ not started · 🔄 in progress · ✅ done · ⚠ blocked

| #   | Story (= ticket)                                             | Status | Ticket |
| --- | ------------------------------------------------------------ | ------ | ------ |
| 13  | Intake skeleton: text-only Report lands + team list          | ✅     | [01](docs/tickets/reports-inbox/issues/01-intake-skeleton.md) |
| 14  | Inbox tab: fifth tab, badge, list                            | ✅     | [02](docs/tickets/reports-inbox/issues/02-inbox-tab.md) |
| 15  | Triage: status lifecycle + detail screen                     | ✅     | [03](docs/tickets/reports-inbox/issues/03-status-lifecycle.md) |
| 16  | Screenshots: intake, storage, serving                        | ✅     | [04](docs/tickets/reports-inbox/issues/04-screenshots-pipeline.md) |
| 17  | Screenshots in the inbox UI                                  | ✅     | [05](docs/tickets/reports-inbox/issues/05-screenshots-inbox-ui.md) |
| 18  | Ship: environments, smoke, deploy                            | ✅     | [06](docs/tickets/reports-inbox/issues/06-ship-and-bookkeeping.md) |
| 19  | Intake contract v1.1: screen context + signed-out reporters  | ✅     | [07](docs/tickets/reports-inbox/issues/07-intake-contract-v1-1.md) |
| 20  | Team notes + inbox clarity                                   | ✅     | [08](docs/tickets/reports-inbox/issues/08-report-notes-and-inbox-clarity.md) |
| 21  | Intake contract v1.2: device context (os, browser, deviceModel) | ✅  | [01](docs/tickets/story-21-device-context/issues/01-intake-contract-v1-2-device-context.md) scoping · [02](docs/tickets/story-21-device-context/issues/02-device-context-tracer-bullet.md) tracer bullet (`de36d94`) · [03](docs/tickets/story-21-device-context/issues/03-ship-v1-2-freeze-deploy-handoff.md) ship |

**Stories 13–17 (2026-08-13/14):** built on `feature/reports-inbox-planning` over 20 commits —
the six tickets, then a second pass implementing the Claude Design package, then UI bug fixes —
and **squashed into `dev` at `76f1e24`**. Not yet promoted to `main`. Backend suite green
(42 reports tests among them); client typechecks and lints clean.

- **Backend.** New `reports` module beside `auth`/`entries`/`users`, same layering. Migrations
  `V3__reports` (client-minted UUID PK = the idempotency key) and `V4__report_screenshots`
  (bytes in `bytea`, keyed by report+ordinal). `POST /api/intake/reports` authenticates the
  relay with `X-Intake-Secret` in **its own `@Order(1)` filter chain** — physically separate
  from the JWT chain, so a Member's token cannot open intake and the secret opens nothing
  else (both tested). **INV-2's enforcement code untouched.** Team routes: `GET /api/reports`
  (newest-first by `submittedAt`, `?status=` filter), `PUT /api/reports/{id}/status`
  (attribution from the JWT, never the body), `GET .../screenshots/{ordinal}`.
- **Client.** Fifth tab with a `new`-count badge fed by a `ReportsProvider` mounted above the
  tabs, so the badge is live everywhere and clears only as reports are triaged — never by
  opening the tab. Screenshots are fetched with the bearer header (a plain `img src` can't
  carry one), so `useAuthedImage` blobs them on web and passes headers to `Image` on native.
- **Design pass (2026-08-14).** A Claude Design package (project `94d38f76` — "Reports Inbox
  Designs" + "Reports Inbox Spec") arrived after the tickets were built; the developer chose
  direction **1b** for the list and **1d** for the detail. The card list became **dense rows**
  (status as a coloured left edge, segmented Open/Done/Dismissed, sub-chips within Open), and
  the flat status pills became a **lifecycle rail** with Dismiss set apart. Also closed four
  spec gaps the written spec named: skeleton loading (not a spinner), an error state that keeps
  the cached list under a retry card, a lightbox pager, and a fade+scale badge pop.
  **Documented deviation from 1b:** its swipe-to-triage rows are not built — see the input note
  below.
- **Input note — the app serves PC and mobile equally.** Row triage is tap-to-read,
  **press-and-hold**-to-triage, because `onLongPress` fires for a mouse hold as well as a
  touch; 1b's swipe rows were tried twice and abandoned (they cannot work with a mouse, and the
  lifecycle already has the sheet and the detail rail). Photo navigation **does** swipe on both:
  touch scrolls the pager natively, and a mouse-drag path routes through the same `goTo()` as
  the arrows and ←/→ keys.
- **Verified live locally, not in prod:** `bootRun` against the compose Postgres (both
  migrations confirmed applied — the standing silent-Flyway trap), reports injected through
  the real intake endpoint (201 → idempotent 200 → 401 on a bad secret), a byte-identical
  screenshot round-trip, and a headless-browser drive of login → inbox → detail → lightbox →
  triage, watching the badge go 2 → 1. **A developer live check in a real browser is still
  the closing step.**
- **Code review caught one real bug** (`4587602`): an oversized screenshot part returned
  **500, not the contract's 400** — the servlet container rejects a >5MB part before any
  controller runs, and nothing handled `MaxUploadSizeExceededException`, so the service's own
  size check was dead code for exactly its own case. Worse than a wrong status: Largata's
  relay retries until a 2xx, so a permanently-oversized payload would have been retried
  forever. Reproduced live, fixed in `GlobalExceptionHandler`, re-verified live with a 6MB
  part. The same commit de-duplicates the 401 envelope into an `ErrorResponseWriter` shared
  by both auth schemes.
- **Three UI bugs the developer found by using it** (`64f1255`, `7706a82`, `4f4f4b2`), all one
  root cause worth naming: **an interaction verified only the way it was written passes while
  the real input fails.** (1) Swipe rows snapped shut because the row derived its open state
  from the parent and the propagation delay closed it — surfaced only by a real drag, since the
  first check used synthetic click events. (2) The lightbox showed nothing: the images loaded
  fine (`complete: true`, 860×1864 natural) and rendered **0px tall**, because a percentage
  height resolved against a horizontal `ScrollView` child that sizes to content — every "did
  it load?" signal said yes. (3) Multi-screenshot reports could not be paged with a mouse at
  all, since `react-native-web` only wires touch events into scrolling. **Lesson for the next
  UI story: check the rendered box and drive the real input, not the code path.**
- **Story 18 — shipped 2026-08-14.** `dev` squashed at `76f1e24`, promoted to `main`
  (fast-forward, both at `631cbb4`), deployed to **dev and prod**. `REPORTS_INTAKE_SECRET` is
  set in both Railway environments with **different values per environment**, so a
  misconfigured Largata dev build cannot write into the prod inbox (it just 401s) — worth
  keeping, since Reports are permanent and there is no delete endpoint.
  - **A Railway dev environment now exists** (`largata-ts-dev.up.railway.app`, auto-deploys
    from the `dev` branch) — new since the runbook was written, which still says there is no
    staging. It earned its keep immediately: the whole feature was smoked there before prod.
  - `scripts/smoke.sh` passes **11/11 on the local gate, on dev, and on prod**, including the
    CORS-behind-TLS-proxy check — the Story 9 failure mode, and the first time it has been
    exercised for this feature against real HTTPS rather than simulated headers.
  - The developer confirmed the **Reports tab renders on dev** — the only probe that proves
    V3/V4 actually ran, since every unauthenticated route 401s before touching the database
    (the silent-Flyway trap: routes answering 401 rather than 404 proves wiring, not schema).
- **Story 19 — done (2026-08-28), squashed into `dev` at `00bc10a`.** Contract
  v1.1, grilled and signed off in-session ahead of the Largata-side relay build: Largata's
  tracker entry point goes globally visible (signed-out screens included) and reports carry
  the screen the reporter was on. `context.screen` (optional, ≤200 chars, opaque — never
  validated against Largata's routes) + optional per-field `reporter` (inbox shows "Signed
  out") + dotted `context.*` envelope keys. Migration **V5** (nullable reporter columns +
  `screen`); ADR-011; spec amended in place ("Amendments → v1.1") and stays the Largata
  hand-off artifact. No new env vars. Tests 45/45 (reports suite); verified live locally
  2026-08-28 — real Flyway V5 run + the developer's browser check of all three report
  shapes (v1.1 / signed-out / pre-v1.1). **Promoted to `main` (fast-forward, both at
  `edda09f`) and deployed to dev and prod the same day.** `scripts/smoke.sh` ALL PASS on
  both environments, including the CORS-behind-TLS-proxy check; the served JS bundle on
  both carries the v1.1 "Signed out" marker (same bundle hash), proving the new build is
  live — and a booted Spring means Flyway applied V5 to each database. Automated checks
  pass; **the developer's live check (browser login → Reports tab) on prod is the
  remaining verification** per the standing deploy rule. Real v1.1 traffic starts when
  the Largata-side relay ships (starting input: the amended spec).
- **Story 20 — done (2026-08-29), squashed into `dev` at `9bc03ae`.** Team **Notes** on
  Reports (ADR-012, reversing the spec's original
  "no comments"): the log is append-only — no delete route exists anywhere, and a test
  asserts that rather than assuming it — while each note's text is editable **by its
  author**, stamped `Edited · when` from the JWT. Migration **V6** (`report_notes`,
  editor columns included so the schema lands once); `POST`/`PUT /api/reports/{id}/notes`;
  notes embedded oldest-first in `GET /api/reports` **and** in the status-change response
  (the client swaps that response into its cache, so dropping them there would blank the log
  on screen) — but **never** on the intake surface, replay included. Detail screen reworked
  to the approved mockups: testimony-scale description leading the page, status pill in the
  type row, Notes ledger + composer last; list rows gain a two-line snippet and a note count;
  the inbox polls every ~60s while focused and foregrounded, silent-stale on failure.
  Tickets [10](docs/tickets/reports-inbox/issues/10-notes-tracer-bullet.md)–[13](docs/tickets/reports-inbox/issues/13-inbox-focused-polling.md).
  Wire contract stays **v1.1** — nothing here needs the Largata repo.
  - **Full backend suite green** (159 tests, 31 of them new note tests; 76 in the reports
    package); client typechecks and lints clean.
  - **Verified live locally, not in prod:** `bootRun` against the compose Postgres applied
    V6 (`Successfully applied 1 migration … now at version v6`) and `\d report_notes` matches
    the ticket's column list; then a real round-trip against the running app — intake `201`,
    note created with the author resolved from the token, a **second** member's edit stamping
    *them* while authorship held, `DELETE` on the note path `404`, and an intake replay
    returning `"notes":[]`. One live-check report + note is left sitting in the local dev
    database (a "Live check: the map never loads." problem report); delete it whenever.
  - **Editing was reversed to author-only the same day, before merge**, after the developer
    saw the built screens on the LAN gate: they had pictured one shared notes field, and the
    attributed per-person ledger made a Note read as signed testimony. A non-author's edit is
    now `403` — the same ownership answer INV-2 gives on a time entry, so the app has one
    ownership rule rather than two. ADR-012 carries the revision (its own *Invalidates it*
    clause had named author-gating as the likely revisit); spec amendment, plan spec,
    glossary, and tickets 08/11 all updated. `edited_by` stays in schema and wire though it
    now always equals the author. **Lesson worth keeping: this decision survived a full
    grilling round and an approved mockup, and still only became obvious once it was
    rendered and clickable. Build the thin slice before trusting a settled decision.**
  - **Three fixes came straight out of the developer's live check on the LAN gate**, which
    is the standing deploy rule earning its keep: (1) long-pressing a row painted a browser
    text selection that then ran into the status sheet as it mounted — `noTextSelect` now
    sits beside `cursor: 'pointer'` on every Reports control, while prose stays selectable;
    (2) the inbox landed on the full open list, which made the unselected chip row look like
    nothing was filtering, so it now defaults to **New** (the chips keep toggling — clearing
    one still shows everything open, but that view is chosen rather than landed on; a
    dedicated "All open" chip was built first and removed at the developer's call);
    (3) picking a status in the sheet showed only
    a spinner, so the tap read as unregistered — the row now highlights, its dot springs and
    a tick fades in on press. That third fix also uncovered a live bug: **a failed status
    change was invisible**, because the screen's error line renders behind the modal that
    stays open on failure. The sheet now shows it.
  - **Promoted and deployed 2026-08-29.** `dev` squashed at `9bc03ae`, promoted to `main`
    (fast-forward, both at `cc86c4a`), deployed to **dev and prod**. The served JS bundle on
    both environments carries the Story 20 marker and is the *same* hash
    (`entry-0d7a504e…`), replacing `entry-8bae4d92…` — so the new build really is live, on
    both. `scripts/smoke.sh` **ALL PASS** on the local gate, on dev, on
    `worklog.largata.com`, and on the underlying `largata-ts.up.railway.app` — including the
    CORS-behind-TLS-proxy check, the Story 9 failure mode. No new env vars; V6 is additive
    only (`CREATE TABLE` + `CREATE INDEX`, no `ALTER` of an existing table).
  - **What is NOT directly observed: that V6 ran on the dev and prod databases.** The seeded
    password is rotated on dev, so the authenticated probe (`GET /api/reports` returning
    embedded `notes`) could not be run from here. The inference is sound but is an inference:
    `spring-boot-flyway` is wired on these deployments (V5 demonstrably ran on both for Story
    19) and a failed migration is fatal to startup, so a booting app implies V6 applied —
    which is a materially stronger position than the original silent-Flyway trap, where the
    autoconfiguration was absent and *nothing* ran. **The confirming probe is the developer's
    login: open Reports on prod and add a note.**
  - **The developer's live check is done for the LOCAL build (2026-08-29); the deploy still
    needs one.** The branch was
    served over LAN as the single-origin parity image (`docker compose --profile fullstack`;
    `scripts/smoke.sh` 12/12 against the LAN address, CORS-behind-proxy check included) and
    driven in a real browser, against an inbox seeded with the shapes the checklist needs: a
    1479-char testimony, a signed-out report, reports with and without notes, and notes by
    three authors including an edited one. Tickets 10–13 are marked done on that basis.
    **Still to do: the developer's live check against the deploy** — the standing
    rule is about the deploy, and automated checks alone are never "done".
  - Off-epic side-effect, ledgered below: unmapped HTTP methods on `/api/**` used to 500.
- **Story 21 — done (2026-08-29), tracer bullet squashed into `dev` at `de36d94`.** Intake
  contract **v1.2 — device context** (ADR-013): `context.os` / `context.browser` /
  `context.deviceModel` join the wire as optional opaque ≤200-char strings following
  `screen`'s rule verbatim — absent → null, overlong → `400` with the dotted key, and **no
  format or vocabulary validation ever** (worklog parses no user-agent and keeps no list of
  browsers or OSes). `os` carries name *and* version in one field, because for a web
  reporter the OS *name* is the payload. A native report sending `browser` is **stored as
  sent**, never rejected — under store-and-forward every `400` is a silently lost report.
  Migration **V7** (three nullable `VARCHAR(200)` columns, additive only); both intake
  responses and `GET /api/reports` carry the fields; the detail screen renders **one
  combined Device row** (`browser · os · deviceModel`, null parts dropped, row omitted when
  all three are null), list row deliberately unchanged. All three fields landed in one bump
  because **nothing back-fills**: intake is server-to-server, so the `User-Agent` worklog
  sees is Largata's backend, and every report filed before Largata's capture ships stays
  blank on these fields forever.
  - **Scoped, grilled, built, reviewed and shipped in one day.** The design was settled by a
    six-decision grill round (os field shape · flat envelope · field-set completeness ·
    validation stance · rendering · glossary term) before any code existed; the records live
    in `docs/tickets/story-21-device-context/` per the story-per-directory convention
    clarified the same day, while the spec stays in `reports-inbox/` as the single Largata
    hand-off artifact.
  - **Full backend suite green**; client typechecks clean. Contract tests in
    `IntakeEndpointTest` pin every case — all three present · none present (pre-v1.2 shape
    echoes nulls) · each field overlong → `400` with its dotted key · exactly 200 chars
    accepted · a native report sending `browser` stored as sent, proven through the
    idempotent replay (which reads the stored row, not the request).
  - **V7 confirmed applied against a real Postgres** by a live `bootRun` against the compose
    database (`Migrating schema "public" to version "7 - device context" … Successfully
    applied`), per the standing silent-Flyway lesson — the test suite alone never proves
    autoconfigured migration.
  - **The developer's live check on the LAN parity image passed (2026-08-29)** — the
    single-origin fullstack image served over LAN, `scripts/smoke.sh` ALL PASS against the
    LAN address (CORS-behind-proxy check included), driven from a real phone browser over a
    seeded spread covering every device-context combination: each field alone, each pair,
    all three, a native report carrying `browser`, near-cap and non-ASCII values, one with
    screenshots, one signed-out, and a pre-v1.2 shape as the regression control. Those seed
    reports (`[seed …]`-prefixed, plus one called "probe") are permanent rows in the **local
    dev** database — Reports are undeletable by design; clear them with SQL whenever.
  - **Two code-review rounds** (standards + spec axes, both run twice). Findings fixed: the
    "stored as sent" test asserted only the `201` echo (now replays); the accepted side of
    the 200-char cap was untested; the team list's pre-v1.2 nulls were implied, not
    asserted; and the off-epic ledger line below said "docs only" while riding in the same
    squash as V7 — corrected in place, since a future reader running `git show de36d94`
    would otherwise have been told the opposite of what the commit contains.

## Off-epic ledger

_(Unplanned changes — a line each so small adjustments don't vanish. Starts empty.)_

| Date       | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Why it wasn't a story                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-07-13 | Stack bumped from the plan's Java 21 / Spring Boot 3.x to **Java 25 / Spring Boot 4.1.0 / Testcontainers 2.0.5 / Gradle 9.6.1**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | Local env has only JDK 17 & 25 (no 21); dev chose to align on Java 25 (needed SB 3.5+; landed on 3.5.16 first, since superseded). SB 4.x was then a **deliberate second move** — dev chose to absorb the 3→4 major upgrade now, while the codebase is 2 files, rather than let it compound later. Required: `spring-boot-starter-web`→`-webmvc` (deprecated rename) and `spring-boot-starter-test`→`-webmvc-test` (4.x split MockMvc out of the generic test starter; `@AutoConfigureMockMvc` moved package to `org.springframework.boot.webmvc.test.autoconfigure`). TC 1.x mis-parses Docker Engine 29's `/info` → 2.0.5. All version-compat/deliberate-upgrade, not scope.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| 2026-07-13 | Added missing `spring-boot-starter-flyway` — SB 4.x split Flyway's Spring autoconfiguration into its own module; without it, migrations silently never ran (app boots, `/api/health` returns 200, zero tables exist, no error anywhere)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Invisible to the test suite (`SchemaMigrationTest` drives Flyway directly; `HealthEndpointTest` never checked for tables) — only caught by manually smoke-testing `bootRun` against the real `docker-compose` Postgres. Bug from the 4.1.0 move, not new scope.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| 2026-07-13 | `docker-compose.yml`: Postgres volume mount `/var/lib/postgresql/data` → `/var/lib/postgresql`; host port made env-overridable (`DATABASE_PORT`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | `postgres:18+` refuses to start against the old mount path; port var lets a dev dodge a local 5432 conflict. Bug found during `docker compose up` verification.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| 2026-07-13 | `TimeEntryRepository.findByFilters`'s optional-filter JPQL rewritten from `CAST(:param AS ...) IS NULL OR ...` to `col OP COALESCE(:param, col)`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | The `CAST` fix (added during this story's code review, committed in `a72a261`) only fixed the query's _first, unprepared_ execution — Postgres's extended protocol can't infer a bare bind parameter's type from `? IS NULL` alone. Smoke-testing the built JAR live (not the test suite — Testcontainers happened not to hit this ordering) surfaced a second failure: once the JDBC driver reuses a server-side prepared statement for this query and `:param` is an actual `null`, it binds that null as `bytea` by default, and `CAST(bytea AS date)` is a cast Postgres refuses (`ERROR: cannot cast type bytea to date`) — reproduced live with a real "no filters" `GET /api/entries` call, which the automated test suite never exercised enough times on one connection to hit. `COALESCE(:param, col)` avoids both failure modes: the parameter's type is always inferred from the column it's coalesced with, and a null parameter degenerates the condition to always-true rather than ever comparing against a bare `? IS NULL`. **Lesson: a green test suite did not catch this — only running the actual app against a real Postgres and hitting the endpoint more than once did.** |
| 2026-07-13 | Added a `CorsConfigurationSource` to `SecurityConfig` (origins from `CORS_ALLOWED_ORIGINS`, default `http://localhost:*`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | While building Story 7a's client, `curl` showed the API working fine, but a real browser refused every request outright — no `Access-Control-Allow-Origin` header, so the CORS preflight fails before any request is sent. Invisible to `MockMvc`/Testcontainers tests, since CORS is a browser-enforced mechanism, not something a server-side test client checks. Without it, ADR-001's "web is the real target" is unreachable from an actual browser. Raised with the developer before changing `SecurityConfig.java` (an auth-adjacent file, per CLAUDE.md's stop rule) — approved.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| 2026-07-14 | Ran `client/`'s already-scaffolded `npm run lint` (`expo lint`) for the first time; it self-configured ESLint, adding `eslint`/`eslint-config-expo` to `client/package.json`/`package-lock.json` and generating `client/eslint.config.js`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Not new scope — the `lint` script existed since Story 7a's scaffold but had never actually been run. Invoked during Story 7b's verification pass (per `/implement`'s "run typechecking regularly" instruction); it passed clean with no findings. Flagged here per Story 7b's code-review (Spec axis) as an unplanned housekeeping addition outside that story's declared deliverables.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 2026-07-14 | **Timezone/date coherence fixes (client + dev seeds).** (1) Dev-seed `created_at`/`updated_at` were `17:00 UTC` (year seeds) or defaulted to `now()` (dev seed) — the first rolled to the *next day* when rendered east of UTC (a `+10` browser showed a Jul-2 entry's log time as "Jul 3"); the second clustered every backdated row at "just now". Rewrote all three seed scripts (`seed-dev-data.sql`, `seed-year-data.sql`, `seed-year-data-batched.sql` ×4 blocks) to stamp a plausible local-zone work-hour **on `entry_date`** via `(entry_date + …) AT TIME ZONE 'Australia/Sydney'`, DST-correct, `LEAST(…, now())`-capped, varied per user (so times aren't identical per day). Reseeded local dev (1,270 rows; 0 day-mismatches, 0 future stamps). (2) Interim client fix: `relativeTime`'s >7-day fallback rendered the `created_at` *instant's* local day (the same shift) — pointed it at `entryDate`. **This client edit is superseded by Story 12** (`relativeTime` → `activityLabel`); the seed fixes are independent and stand. | Data-quality + a one-line display fix surfaced while the developer verified the calendar day view on local dev; too small for a story on its own. The seed scripts are DEV/TEST-only and never run in prod. |
| 2026-07-14 | **Client navigation transitions.** Added screen-transition motion to fill the gap between the app's in-content motion (`FadeInView`) and its previously-default/hard-cut navigation. (1) Root auth stack: `fade` (260ms) — sign-in/out is a state change, not a spatial push. (2) `(app)` stack: `day/[date]` drill-in gets `ios_from_right`; modal forms get a consistent 300ms rise. (3) New `components/nav/TabTransition.tsx` wraps the headless `expo-router/ui` `<TabSlot />` (which swaps tabs with a hard cut) in a 220ms crossfade + 8px lateral drift keyed on `usePathname()`, direction following tab order — reuses `useReducedMotion` + `Animated` so it's the same motion language as `FadeInView`, and is skipped whole under reduced motion. All via `react-native-screens` (no Reanimated added). Web export compiles clean; typecheck + lint green. | Small UX polish, not a planned story; native-thread animation feel still needs the developer's live device check (web preview degrades the native `animation` options). |
| 2026-07-14 | **Repo restructured: the Java/Gradle backend moved from the repo root into `backend/`, a peer to `client/`.** `src/`, `build.gradle`, `settings.gradle`, `gradlew*`, `gradle/` → `backend/` (57 `git mv` renames, R100 — no content change). Root now holds two app peers + an orchestration layer (`docker-compose.yml`, `Dockerfile`, `.env*`, `scripts/`, `docs/`). Dockerfile `COPY`s gained a `backend/` prefix (build context stays root); `.gitignore` split (new `backend/.gitignore`, root trimmed to cross-cutting); `.env`/compose stay at root and unchanged. Fulfils the deferred `docs/tickets/repo-restructure-backend-client` ticket. **As of this commit, path references in `docs/plans/` and `docs/tickets/` dated before it are relative to the old root and were intentionally left frozen** (immutable-record convention — updating live docs only: CLAUDE.md, this ledger, `docs/deploy/railway.md`). Rationale + rejected alternative (root Gradle multi-project) recorded as ADR-009. | Cosmetic discoverability/symmetry improvement, no behaviour change — never warranted its own story; deferred 2026-07-13, done now before the codebase grows.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| 2026-08-28 | **Skills package re-synced to upstream.** The installed `mattpocock/skills` set (`.agents/skills/` + `.claude/skills/`, mirrored 1:1 — verified identical diffs) refreshed to current upstream: a prose/wording revision pass across 77 files (em-dash → colon/comma style, small clarifications, e.g. `handoff`'s "suggested skills" now names the Skill tool), plus the matching `computedHash` bumps in `skills-lock.json`. No skill added or removed; no local skill content authored. | Tooling refresh, not scope. The local skill-sync rewrote the files on session start, so every fresh checkout showed 77 modified files until the synced state was committed; committing re-pins the lock hashes to the tracked content. |
| 2026-08-29 | **Intake contract v1.2 (device context) scoped — a docs-only *session*; the live wire contract stays v1.1 until Story 21 ships.** ⚠ The build followed later the same day and was squashed together with these records at `de36d94`, so this line's "docs only" describes the scoping session, **not** that commit — which does carry V7 and the code. Grilled and signed off in-session: `context.os` / `context.browser` / `context.deviceModel` as optional opaque ≤200-char strings exactly like `screen` (one `os` field carrying name+version — for web reporters the OS *name* is the payload; flat envelope, dotted keys; `screen`'s validation rule verbatim, store-as-sent for odd combos; one combined Device row in the detail screen; field set deliberately capped at three — `locale`/viewport/`timeZone` rejected as speculative). Spec gains "Amendments → v1.2 (planned)" + a header pointer warning that Largata must not send early (today's deployment silently drops unknown JSON — values sent early are lost for good); **Device context** added to the domain model (02) marked not-yet-built; the records live as **Story 21** in their own directory per the story-per-directory convention (clarified the same day in docs/agents/issue-tracker.md; the spec stays in `reports-inbox/` as the single hand-off artifact): [ticket 01](docs/tickets/story-21-device-context/issues/01-intake-contract-v1-2-device-context.md) is the scoping record (migration V7 sketch: three nullable columns, additive only), sliced for pickup into [ticket 02](docs/tickets/story-21-device-context/issues/02-device-context-tracer-bullet.md) (tracer bullet — implemented later the same day) → [ticket 03](docs/tickets/story-21-device-context/issues/03-ship-v1-2-freeze-deploy-handoff.md) (ship + Largata hand-off). **Superseded the same day:** both slices ran, the contract went live as **v1.2** (ADR-013), and the story table above carries Story 21 as ✅ — so everything below about "planned", "not yet built" and "stays v1.1" is the state at scoping time only. | Scoping output, not a built story — implementation deliberately deferred by the developer (spec + ticket only). Recorded so the reserved v1.2 has a pickup-ready record. The visible payoff needs a Largata-side capture session after worklog's half ships (intake is server-to-server — nothing can be sniffed or back-filled; every report before then stays blank on these fields forever). |
| 2026-08-29 | **`GlobalExceptionHandler` now maps `HttpRequestMethodNotSupportedException` to a clean `404` envelope.** Any unmapped HTTP method on an `/api/**` path previously fell to the catch-all handler: a `500` plus an `ERROR` log line, for what is really "there is no such route". Reported as `404 NOT_FOUND` rather than `405` deliberately — the error vocabulary stays the five codes 05-api-conventions documents, and for this API an unmapped method *is* an absent resource. | Surfaced by Story 20's "no DELETE route exists" test, which asserts the append-only guarantee at the routing table: `DELETE /api/reports/{id}/notes/{noteId}` returned `500`. One handler, no new error code, no conventions change — too small for a story, but it changes the response of every wrong-method call in the API, so it is recorded rather than folded silently into the story. |
