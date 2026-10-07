# Story 28 spec — Report Handoff

**Status:** built — split into tickets 01–05 (2026-10-07), implemented on
`feature/28-report-handoff`, amended during the developer's LAN check (see Further Notes).
Ticket 04 was dropped.

Synthesized 2026-10-07 from a grilling session with the developer (design confirmed the same
day). Epic 3 (Reports inbox). Vocabulary: **Report**, **Note**, **Member**, **Traveler** and
**Handoff** as defined in `docs/design/02-domain-model.md` (Handoff added, and `in progress`
given its meaning, in this session). Related: ADR-012 (Notes), the reports-inbox spec
(`docs/tickets/reports-inbox/spec.md`) for the Report fields.

## Problem Statement

Triage happens in worklog, but fixing happens elsewhere — typically a Claude Code session in
the Largata repo. Today there is no way to get a set of triaged Reports out of worklog in one
piece: the developer would have to open each Report and copy its description, context and
Notes by hand. Worse, once Reports have been passed on, nothing records that it happened, so
"what did I hand over on Tuesday, and exactly what did it say?" has no answer — and a Report's
Notes may have changed since, so even rebuilding it from today's data would not be the truth.

## Solution

On the Open tab, a Member enters a selection mode, ticks the Reports to pass on, and presses
**Hand off**. Worklog records a **Handoff** — who, when, which Reports, and the exact text
handed over, frozen — and opens it: the text as raw Markdown with a **Copy** button. Every
Handoff stays reachable from a **Handoffs** list on the Reports tab, so its text can be
re-opened and copied again at any time. *(Each Report showing when it was handed off was
dropped 2026-10-07 — see Further Notes.)* The text
is a faithful, readable rendering of the Reports — no prompt, no links, no screenshots — and
what the developer does with it afterwards is up to them.

## User Stories

1. As a Member, I want to select several open Reports at once, so that I can pass them on
   together instead of one at a time.
2. As a Member, I want selection to start from a visible "Hand off" control, so that the
   feature is discoverable without a hidden gesture. *(Amended 2026-10-07: the control is the
   nav bar's centre disc, which shows a send glyph on the Open tab instead of "log time" — see
   Further Notes.)*
3. As a Member, I want the inbox to look exactly as it does today until I start selecting, so
   that day-to-day triage stays uncluttered.
4. As a Member, I want to select any open Report — new, For discussion or in progress — so
   that I decide what goes out, not a fixed status rule.
5. As a Member, I want a bottom bar showing how many Reports I've selected ("Hand off (3)"),
   so that I know what I'm about to hand off. *(Amended 2026-10-07: the nav bar itself turns
   into that bar.)*
6. As a Member, I want to leave selection mode without handing anything off, so that starting
   a selection is never a commitment.
7. As a Member, I want pressing Hand off to record the Handoff immediately, without a
   confirmation dialog, so that the deliberate act of selecting is the only step.
8. As a Member, I want the new Handoff to open straight away showing its text, so that I can
   copy it in the same motion.
9. As a Member, I want the text shown raw (unrendered Markdown, monospace), so that what I see
   is exactly what I'll paste.
10. As a Member, I want a Copy button that puts the whole text on the clipboard and confirms
    it, so that I don't have to select a long block by hand.
11. As a Member, I want to still be able to select the text by hand if copying fails, so that
    a browser refusing clipboard access never blocks me — the Handoff is already recorded.
12. As a Member, I want a Handoffs list on the Reports tab, newest first, showing when, who
    and how many Reports, so that I can find what was handed off and when.
13. As a Member, I want to open any past Handoff and see the exact text that was handed over,
    with Copy available again, so that I can go back to it any time.
14. As a Member, I want a Handoff's text to stay exactly as it was, even after its Reports'
    Notes or statuses change, so that the record is the truth about what was sent.
15. As a Member, I want every Handoff kept forever and visible to every Member, so that it
    behaves like every other record in the Inbox.
16. ~~As a Member opening a Report, I want to see "Handed off 7 Oct" for every Handoff it was
    part of, so that I know whether it has already gone out.~~ *Dropped 2026-10-07 by the
    developer: not needed.*
17. As a Member, I want to be able to hand off a Report that was handed off before, so that a
    fix that didn't stick can be sent again with no workaround.
18. As a Member, I want handing off to leave every Report's status untouched, so that status
    stays my manual triage call.
19. As the reader of a Handoff's text, I want a header naming it a Handoff with its time, its
    Report count, and who made it, so that the text identifies itself wherever it is pasted.
20. As the reader, I want an index of the Reports grouped by screen and numbered, so that
    related Reports sit together and I can refer to them by number.
21. As the reader, I want every Report section to stand on its own, so that one section can
    be read or passed on without the rest.
22. As the reader, I want each Report's description quoted verbatim, so that the reporter's
    own words reach the fix unaltered and can't break the text's structure.
23. As the reader, I want each Report's status, screen, submission time, Traveler uid and
    device, so that I can investigate and look the Traveler up in Largata.
24. As the reader, I want fields the Report doesn't have left out rather than shown as
    "unknown", so that the text carries only real information.
25. As the reader, I want each Report's Notes with author and date, and edits marked, so that
    the triager's reproduction steps and decisions travel with the Report.
26. As the reader, I want a Report with no Notes to say "Notes: none", so that a missing
    triage decision is visible rather than silent.
27. As a Traveler, I never want my name to leave worklog in a Handoff, so that my identity
    travels only as the uid Largata already holds.
28. As the developer, I want who made a Handoff taken from the login, never from the request,
    so that the record can't be forged by a buggy client.
29. As the developer, I want no delete or edit route for Handoffs to exist at all, so that
    "permanent" is enforced by the API surface.
30. ~~As the developer, I want each Report's Handoffs to arrive on the existing reports read, so
    that the inbox still loads in one fetch with no new loading state.~~ *Dropped 2026-10-07
    with story 16: nothing displays it, so the reports read carries no Handoffs.*
31. As the developer, I want the intake surface and the Largata wire contract untouched, so
    that nothing needs coordinating with the Largata repo.

## Implementation Decisions

**Domain**

- A **Handoff** is a new aggregate in worklog's own data: a server-minted id, the creating
  Member, the creation time, the frozen text, and the ordered list of Reports it covers. It
  has no owner (any Member reads any Handoff), no status, and no lifecycle after creation: it
  is never edited or deleted.
- A Report may appear in any number of Handoffs. Creating a Handoff never changes a Report.

**Who writes the text — the client (developer's call, 2026-10-07)**

- The client builds the text from the Reports it already holds and sends it with the Report
  ids. The server stores the text as given; it does not regenerate it or compare it with the
  Reports. Accepted consequences: the formatter has no automated tests (the client has no test
  runner by standing decision, 06b 2026-10-05); the text reflects the client's loaded data,
  kept fresh by the inbox's existing polling.
- The formatter is a pure function of (selected Reports, creating Member's name, now) to a
  string, with no React dependency, so a test runner could cover it later without a refactor.

**Backend — a new `handoffs` feature module**

- Laid out package-by-feature-then-role like `dashboard/` (controller, dto, service,
  repository, domain), with an ArchUnit test pinning who may touch its repository and domain
  packages, per the convention adopted 2026-10-05.
- **Schema (signed off by the developer, 2026-10-07):** migration V9 adds two tables.
  `handoffs`: id, created_by (a worklog user), created_at, text. `handoff_reports`: the
  Handoff, a Report, and its position within the Handoff; one row per Report per Handoff,
  unique on (Handoff, Report). Nothing alters an existing table.
- **Routes** (all Member-authenticated; status codes and error envelope per 05):
  - `POST /api/handoffs` — body: the Report ids in order, and the text. Returns 201 with the
    full Handoff. The creator is the JWT identity; a creator field in the body is ignored.
    400 `VALIDATION_FAILED` when the id list is missing or empty, contains a duplicate, or
    names a Report that doesn't exist (details name the offending ids); when the text is
    missing or blank; or when a limit below is exceeded.
  - `GET /api/handoffs` — every Handoff, newest first, as summaries: id, created at, creator
    id and name, Report count. Never the text. `200 []` when there are none.
  - `GET /api/handoffs/{id}` — one Handoff in full: the summary fields plus the text and the
    ordered Report ids. 404 when unknown.
  - No PUT, PATCH or DELETE exists.
- **Limits (proposed, for review):** at most 200 Reports per Handoff; text at most
  1,000,000 characters. Both are far above any realistic Handoff and exist only so a client
  bug can't write an unbounded row into a permanent table.
- The server does not check that the Reports are open; the client offers only open Reports.

**~~Reports read gains Handoffs~~** — dropped 2026-10-07 (developer's call; see Further
Notes). Built in ticket 04, then removed before the merge with the "Handed off" line it fed:
the reports read, the status-change response and the intake response are unchanged by this
story. Which Reports went out stays recorded on each Handoff.

**Client**

- **Selection mode (web only for now):** on the Open tab the nav bar's centre disc shows a
  send glyph (accessible name "Hand off reports") instead of "log time"; pressing it enters
  selection. Rows gain checkboxes (in the type glyph's slot, so nothing shifts), and the nav
  bar morphs into "✕ · Hand off (N)" (disabled at zero), the disc stretching into that button.
  On native, and on the Done/Dismissed segments, the disc stays "log time". *(Amended
  2026-10-07; originally a header button plus a separate bottom bar.)*
- Pressing Hand off builds the text, posts it, leaves selection mode, and navigates to the new
  Handoff's screen. A failed post keeps the selection and shows the error; nothing is
  recorded.
- **Handoff screen:** the header line (when, who, how many) and the raw text, monospace and
  scrollable, with Copy. Web copies through the browser clipboard; success says "Copied"; a
  failure says so and leaves the text selectable.
- **Handoffs list:** reachable from the Reports tab; newest first; each row shows date, who
  and count, and opens the Handoff screen.
- ~~**Report detail:** one "Handed off <date>" line per Handoff, near the status.~~ Dropped
  2026-10-07; the detail screen is unchanged by this story.

**Text format**

```markdown
# Handoff · 2026-10-07 14:02 UTC · 5 reports
By Ed

## Index
postcard/compose
  1. c3d90b17 · problem · Postcard preview shows `undefined` instead of my caption | happens every…
trip/edit
  2. 4f0c2a8e · problem · Saving a trip with no end date spins forever
(no screen)
  3. …

---

## 2. 4f0c2a8e · problem

> Saving a trip with no end date spins forever. I waited a minute
> and had to close the app. Lost my notes.

- Status: in progress
- Screen: trip/edit
- Submitted: 2026-10-03 09:41 UTC
- Traveler uid: 0b6f9a2c-1e3d-4c5b-8a7f-9d2e1c3b4a56
- Device: ios · app 1.8.2 · iOS 19.1 · iPhone 16

Notes
- 2026-10-04 · Ed: Reproduced on staging. Only when end date is empty.
- 2026-10-05 · Ed: Likely the date validator. (edited 2026-10-05)
```

- Times in UTC, `yyyy-MM-dd HH:mm UTC`. Short id = the first 8 characters of the Report id.
- Ordering: grouped by screen alphabetically with a final "(no screen)" group, oldest
  submission first within a group; numbered 1..N in that order, the same numbers in the index
  and the section headings.
- Index summary: the description's first line, trimmed, cut at 80 characters with "…".
- Description quoted verbatim: every line prefixed `> `, blank lines kept as `>`; nothing
  escaped or altered.
- Status uses the UI's labels ("in progress", "For discussion", …). The Screen line appears
  only when present. The Traveler uid line reads "none (signed out)" when absent. Device joins
  whichever of platform, app version, OS, browser and device model are present.
- Notes oldest first as "date · author: body", multi-line bodies continued on indented lines,
  "(edited date)" when edited; "Notes: none" when there are none.
- Never included: the reporter's name, screenshots or their count, worklog links, any prompt
  or instruction.

## Testing Decisions

- **Good tests here exercise behaviour through the HTTP API** against a real Postgres
  (Testcontainers), asserting status codes, response bodies and the error envelope — never
  repository calls or internal method shapes.
- **One seam: the `handoffs` HTTP API.** Tests cover:
  - create: 201; the creator comes from the JWT even when the body names someone else; ids
    and text round-trip exactly; order is preserved;
  - every 400 case above, and 401 without a token;
  - list: newest first, summaries never carry the text, `[]` when empty;
  - get one: the full text byte for byte; 404 when unknown;
  - no PUT, PATCH or DELETE route, answered per the existing convention for absent routes;
  - handing off never changing a Report's status.
- **Architecture test** for the new module, modelled on `DashboardArchitectureTest`.
- **Prior art:** `CreateReportNoteEndpointTest` and `EditReportNoteEndpointTest` (JWT
  identity, append-only surface), and the dashboard endpoint tests (a package-by-role module).
- **Client:** no automated tests (standing decision); typecheck and lint clean. The live demo
  is the formatter's only check, so it must seed every shape: multi-line and Markdown-hostile
  descriptions, multi-line and edited Notes, a Report with no Notes, a signed-out reporter, a
  pre-device-context Report, a Report with no screen, an idea, and a Report already in an
  earlier Handoff. Then hand off, copy, paste into a plain editor and compare byte for byte;
  then edit a Note, reopen the Handoff from the list, and see the old text unchanged.
- The migration is verified with a live `bootRun` against the compose Postgres (the standing
  silent-Flyway lesson), not only by the test suite.

## Out of Scope

- Native selection, copy and Handoff creation (web only for now).
- Deleting or editing a Handoff; any write-back from the Largata side; any status change
  triggered by a Handoff.
- Screenshots in any form; worklog links; a prompt or instructions in the text.
- Bulk status change or any other bulk action on the selection.
- A marker on already-handed-off rows during selection.
- Relabelling the "In progress" status.
- Server-side generation or verification of the text.

## Further Notes

- **Amendment, 2026-10-07 (developer's call, during the LAN check):** the "Handed off <date>"
  line on a Report's detail screen (user story 16, ticket 04's UI) was removed — not needed —
  and with nothing left to display it, so was the `handoffs` field on the reports read (user
  story 30, ticket 04's backend): an unused query on every inbox load and an unread field in
  the Reports API. Re-adding it later is the small, known shape ticket 04 already built.

- **Amendment, 2026-10-07 (developer's call, during the LAN check):** the "Hand off" entry
  point moved from the Open tab's header into the nav bar — the centre disc becomes the send
  control on the Open tab, and the nav bar itself becomes the selection bar. The header keeps
  only the "Handoffs" list link. Discoverability now rests on the disc's changed glyph (with
  its swap animation as the tab opens) and its accessible name, rather than a text label; the
  "log time" action is one tab-switch away while on Reports.

- The text leaves worklog for another repo, so the reporter's name is deliberately excluded
  (P3). Note authors' names are included: they are worklog Members, and authorship tells
  whose reasoning a Note is.
- This branch also carries an unrelated skills-package re-sync at the developer's request; the
  squash message and BUILD_STATUS's off-epic ledger must name it.
- ADR candidate: client-authored frozen text in a permanent record is surprising without
  context and was a real trade-off against server generation. Worth recording as ADR-015 when
  the tickets are cut.
