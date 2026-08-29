# Story 20 spec — Team notes + inbox clarity

Synthesized 2026-08-29 from the grilling session, ADR-012, the spec amendment
("Amendments → Team notes + inbox clarity" in `docs/tickets/reports-inbox/spec.md`), and
the **developer-approved mockups** (design canvas "Reports Inbox Rework",
https://claude.ai/code/artifact/07443364-3673-446e-9e47-7e534d854c99 — approved
2026-08-29, which freezes the design direction from "proposal" to "binding").
Implementation ticket: `docs/tickets/reports-inbox/issues/08-report-notes-and-inbox-clarity.md`
(`ready-for-agent`).

## Problem Statement

The team triages Largata feedback through statuses, but a status only records *where* a
report stands — never *why*. Decisions made in triage (why a report was dismissed, what
the founders agreed in a `discuss` conversation) evaporate into chat and memory; weeks
later nobody can reconstruct them, even though the Reports themselves are kept forever.
On top of that, the detail screen buries the one thing that matters — the reporter's own
words — beneath visually heavier metadata and status apparatus, and the inbox only
refreshes when refocused, so a team member sitting on the tab stares at a stale list.

## Solution

Give every Report a **Notes** ledger: team-authored, append-only (a Note is never
deleted), each Note's text editable by its author with a visible edited-when stamp —
the decision record lives on the Report it's about, forever, next to the status it
explains. Rework the detail screen so the reporter's words lead at testimony scale with
the status visible on arrival, and let the inbox refresh itself every ~60 seconds while
it's open. Everything is worklog-team-facing: the wire contract stays v1.1 and no Note is
ever visible to a Reporter.

## User Stories

1. As a worklog Member, I want to write a Note on a report, so that the decision we made
   about it is recorded where the report lives, not lost in chat.
2. As a worklog Member, I want Notes to be permanent (never deletable), so that the
   decision record has the same "kept forever" guarantee as the Reports themselves.
3. As a worklog Member, I want to edit a Note's text, so that a typo or unclear sentence
   doesn't stand forever as the official record.
4. As a worklog Member, I want every edit visibly stamped with who edited and when, so
   that a rewritten Note can never silently masquerade as the original.
5. As a worklog Member, I want each Note to show its author and creation time, so that
   "who said this, when" never needs asking.
6. As a worklog Member, I want to read a report's Notes oldest-first, so that the record
   reads as a narrative — discussed, decided, shipped — top to bottom.
7. As a worklog Member, I want to add a Note to a report in any status (including done
   and dismissed), so that late context — "this resurfaced", "reporter was right after
   all" — has somewhere to live.
8. As a worklog Member, I want a dismissed report's Note to carry the dismissal
   rationale, so that "won't act" is never an unexplained verdict in permanent data.
9. As a worklog Member reading the inbox list, I want rows to show how many Notes a
   report has, so that I can tell worked-on reports from untouched ones at a glance.
10. As a worklog Member reading the inbox list, I want two lines of the reporter's words
    per row, so that I can triage without opening every report.
11. As a worklog Member opening a report, I want the reporter's words to be the visually
    dominant element, so that I read the actual feedback before the apparatus around it.
12. As a worklog Member opening a report, I want its current status visible at the top,
    so that I know where it stands without scrolling to the rail.
13. As a worklog Member reading a very long report, I want the text scaled down a step,
    so that a 2000-character description reads as a document rather than a poster.
14. As a worklog Member sitting on the inbox during a triage session, I want the list to
    refresh itself every minute, so that newly relayed reports appear without me leaving
    and returning.
15. As a worklog Member, I want the Notes composer to state what Notes are for
    ("decisions, context, next steps"), so that the habit forms without a process doc.
16. As a worklog Member, I want Note edits reachable through a visible control (not a
    hidden gesture), so that editing is discoverable — the press-and-hold lesson applied.
17. As the developer, I want Note author and editor identity taken from the JWT and never
    the request body, so that the record can't be forged by a buggy or mischievous client.
18. As the developer, I want no DELETE route for Notes to exist at all, so that
    "append-only" is enforced by the API surface, not by client politeness.
19. As the developer, I want Notes embedded in the existing reports read, so that the
    client keeps living off one fetch and no new loading state exists.
20. As the developer, I want the intake surface untouched, so that the Largata relay and
    the v1.1 wire contract need no coordination for this story.
21. As a Largata Reporter, I never see worklog's Notes in any form, so that the team can
    write candidly ("user error, but the flow invited it") without a feedback loop the
    system never promised.

## Implementation Decisions

- **Vocabulary (glossary, ADR-012):** the entity is a **Note** — "status says *where* a
  Report stands, a Note says *why*". **Comment** (threaded, reporter-visible) remains an
  excluded concept. The log is append-only; an entry is editable **only by its author**,
  with a visible stamp. Convention, not code: a changed decision gets a new Note; edits
  are for typos and clarity.
- **Schema:** new migration — `report_notes` table: server-generated UUID key, report
  reference, author reference (worklog user, NOT NULL), body ≤2000 chars, created-at,
  nullable editor reference + edited-at. No change to the `reports` table.
- **API (bearer-JWT, standard conventions):** the reports collection read embeds each
  report's notes oldest-first (id, body, author name, createdAt, editor name + editedAt
  when edited — names resolved server-side like the status-changed-by name). Creating a
  note: POST to the report's notes collection, body `{ "body": "..." }` → `201` with the
  created Note. Editing: PUT to the individual note → `200` with the updated Note,
  **author-only** — another Member's note is a `403`, the same ownership answer INV-2
  gives on a time entry. No DELETE route. Unknown ids → `404`;
  empty/oversized body → `400` with envelope key `body`. Author/editor identity and all
  timestamps stamped server-side from the JWT security context.
- **Revised 2026-08-29 (same day, before merge): editing is author-only.** The decision
  above originally opened editing to any Member, with a visible edited-by stamp as the
  safeguard, at the developer's explicit call during grilling. Seeing the built screens
  reversed it: the ledger renders as attributed, per-person entries rather than the single
  shared notes field the developer had pictured, which makes a Note read as **signed
  testimony** — nobody writes under someone else's name. Gained: one ownership rule across
  the app instead of two, and an edit stamp that no longer has to answer "who". Cost
  accepted: a typo in a teammate's Note needs them, or a follow-up Note. `edited_by` stays
  in the schema and the response though it now always equals the author — recording who
  actually acted beats assuming it — while the UI renders just "Edited · when".
- **Backend shape:** extends the existing reports module, same layering
  (controller → service → repository). INV-2 and the intake secret filter untouched; the
  intake route learns nothing about notes.
- **Client data flow:** the reports provider carries notes on each report; add/edit
  merge the returned Note into the cached list. Polling: ~60s refetch while the Reports
  tab is focused, torn down on blur — on top of the existing focus/foreground refetch.
- **Detail screen (approved mockup, binding):** order — type row (glyph + type eyebrow +
  **status pill**, right-aligned, same pill component as the rows) → **testimony block**
  (the description, unboxed, medium weight at 19/28; >280 chars steps to 17/26) →
  screenshots → metadata card (unchanged) → status rail + dismiss (unchanged) → **Notes**.
- **Notes section (approved mockup, binding):** "Notes" eyebrow; flush card ledger with
  hairline dividers; each entry a meta line (person-hue initial dot via the existing
  deterministic name-colour, author name, time, quiet caption-weight **Edit** control)
  above the body; edited entries append a caption stamp "Edited · <who> · <when>".
  Editing happens inline (composer replaces the body) with Save/Cancel. Composer at the
  bottom: multiline field, placeholder "Write a note — decisions, context, next steps",
  **Add note** button disabled while empty, character counter only near the 2000 limit.
  Empty state: "No notes yet — record the decision here so it isn't lost to chat."
  Person hues are team-only: the foreign Reporter never gets one.
- **Inbox rows (approved mockup, binding):** snippet grows to two lines; the meta line
  gains the note count when > 0. Edge, pill, hint line unchanged.
- **Theme discipline:** zero new tokens, zero new typefaces; existing type scale,
  spacing, radii, card anatomy, motion (existing fade-in language only).

## Testing Decisions

- **Seam: the existing backend API integration layer** — HTTP-level tests with MockMvc +
  Testcontainers against real Postgres, in the established `*EndpointTest` style (prior
  art: the four reports endpoint test classes, `CreateEntryEndpointTest`,
  `JwtFilterChainTest`). No new seams; the ideal count here is the existing one. Tests
  assert external behavior only: status codes, envelope shapes, persisted effects —
  never internals.
- **Mandatory security coverage (the standing 06b test-depth rule):** both write routes
  with no/invalid JWT → `401`, nothing persisted; the intake secret grants no access to
  notes routes (the two auth schemes don't bleed — extending the existing test pattern);
  author/editor stamps come from the JWT even when the request body smuggles identity
  fields.
- **Behavior coverage:** create → embedded in the collection read; edit → body and stamp
  change while author and created-at stay fixed; a second user's edit stamps *them*;
  ordering stable oldest-first; boundary bodies (1 and 2000 accepted; 0 and 2001
  rejected); unknown report/note ids → `404`; migration applies on a clean database
  every test run.
- **Client:** no automated e2e (standing 06b decision) — the manual UI checklist in
  ticket 08, exercised by the developer live per the deploy-verification standing rule.
  Polling is verified manually (tab open ≥60s, curl-injected report appears).

## Out of Scope

- Comments in any form: threads, replies, mentions, reporter visibility.
- Deleting Notes (no endpoint exists), Note versioning/history, retracting a Note.
- Editing someone else's Note: **now out of scope by decision**, not by omission — see
  the revision note under Implementation Decisions.
- A unified status+notes activity log, or Notes attached to status transitions —
  requires status history the schema doesn't keep; a recorded non-decision.
- Device context on reports — a wire-contract change needing a Largata-side session.
  Single record: [07 — epic map](../design/07-epic-map.md), "Unscheduled Epic 3
  candidates".
- SSE / WebSockets for inbox freshness — rejected in the amendment (RN EventSource gap,
  JWT-in-query, Railway sleep severing long-lived connections; push is theater
  downstream of retry-lagged intake).
- The ingest delivery lag itself — ticket 09 (measure `receivedAt − submittedAt`, then
  tune at the actual cause), independent of this story.

## Further Notes

- Stop-rule: the schema change was explicitly signed off by the developer 2026-08-29
  in-session; INV-2, the intake secret, and their tests are untouched.
- The design was approved on rendered mockups (canvas above) built from the shipped
  theme tokens — the implementation must match them; deviations go back to the developer.
- Bookkeeping at ship: BUILD_STATUS story row + squash SHA; re-check the spec amendment,
  ADR-012, the glossary Note entry, and the epic-map Story 20 row against what shipped.
- Branch: `feature/story-20-report-notes-inbox-clarity` off `dev`; all of this session's
  doc edits squash in with the implementation (standing git workflow).
