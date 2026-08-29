# 08 — Team notes + inbox clarity

**What to build:** Reports gain a team-facing **Notes** log — the log is append-only (a Note
is never deleted), each Note's text is editable by its author with a visible edited-when
stamp — so triage decisions stop evaporating into chat. Alongside it, the detail screen is
reworked so the reporter's words are the unmistakable primary block, and the inbox gains
~60s focused polling. Worklog-team-facing only: **the wire contract stays v1.1, the intake
surface is untouched, nothing here needs the Largata repo.** Spec: `../spec.md`,
"Amendments → Team notes + inbox clarity"; ADR-012. Demo: open a report → add a note → it
appears in the log with your name; edit it → the edited stamp appears; refetch → both
persist; a teammate's note has no Edit control at all, and a `PUT` at it gets a `403`.

**Blocked by:** None — amends the shipped Epic 3 surface (01–07).

**Status:** done — grilled and signed off by the developer 2026-08-29 (the
schema stop-rule ask, in-session). Design direction below was **approved on rendered
mockups 2026-08-29** (design canvas "Reports Inbox Rework",
https://claude.ai/code/artifact/07443364-3673-446e-9e47-7e534d854c99) and is binding, not
a proposal. Full synthesized spec: `docs/plans/story-20-report-notes-inbox-clarity.md`.

Implemented 2026-08-29 across tickets [10](10-notes-tracer-bullet.md)–[13](13-inbox-focused-polling.md)
on `feature/story-20-report-notes-inbox-clarity`, and verified the same day by the developer
against the single-origin gate served over LAN. The one item left open is the ship-time docs
pass: the BUILD_STATUS row is written, but it can only name the squash SHA once that commit
exists. **Verified locally, not deployed.**

**Editing was narrowed to the note's author** mid-implementation, at the developer's call on
seeing the built screens — ADR-012 carries the reversal, and the criteria below are written to
the revised rule.

- [x] Migration V6: `report_notes` — server-generated UUID PK · `report_id` FK
      `reports(id)` NOT NULL · `author_id` FK `users(id)` NOT NULL · `body` ≤2000 NOT NULL
      · `created_at` NOT NULL · `edited_by` FK `users(id)` NULL · `edited_at` NULL.
      No change to `reports`. (Live `bootRun` + `\dt`/`\d report_notes` check — the
      silent-Flyway lesson — not just the test suite.)
- [x] `POST /api/reports/{id}/notes` `{ "body": "..." }` → `201` created Note;
      `PUT /api/reports/{id}/notes/{noteId}` `{ "body": "..." }` → `200` updated Note
      (**author-only**; another Member's note → `403`); **no DELETE route exists**. Author and
      editor identity from the JWT, never the body. Unknown report/note → `404`;
      empty/oversized body → `400` envelope key `body`.
- [x] `GET /api/reports` embeds each report's notes **oldest-first**: id, body, author
      name, `createdAt`, and (when edited) editor name + `editedAt` — resolved names, same
      pattern as `statusChangedByName`.
- [x] Security tests (mandatory 06b depth, `*EndpointTest` style): both write routes with
      no/invalid JWT → `401`, nothing persisted; the intake secret grants no access to
      notes routes (the two auth schemes still don't bleed); editor stamp comes from the
      JWT even when the body smuggles identity fields.
- [x] Behavior tests: create → embed round-trip; edit → body + stamp change, `createdAt`
      and author untouched; a second Member's edit is refused with the note byte-identical
      afterwards and still visible to them; ordering stable oldest-first; 1-char and
      2000-char bodies accepted, 0 and 2001 rejected.
- [x] Client provider (`useReports`): notes on the report type; `addNote` / `editNote`
      merge the returned Note into the cached list (no full refetch needed for the
      writer's own view).
- [x] Detail screen rework per **Design direction** below.
- [x] List row: snippet `numberOfLines={2}`; meta line gains `· N note`/`notes` when > 0.
- [x] Polling: while the Reports tab is focused, refetch every ~60s on top of the existing
      focus refetch; interval torn down on blur. Client-only; no backend change.
- [x] Manual UI checklist (standing no-automated-e2e decision): add a note → appears at
      the log's bottom with your name; edit it → "Edited" stamp; a teammate's note shows no
      Edit control and a curl `PUT` at it returns `403`; a ~2000-char description
      renders at the reduced testimony scale; notes empty state shows; a report with notes
      shows its count in the list row; leave the tab open ≥60s → a curl-injected report
      appears without touching the screen. Developer live-check per the standing deploy
      rule — automated checks alone are not "done".
- [ ] Docs at ship: BUILD_STATUS story row (+ squash SHA); re-check spec Amendments,
      ADR-012, glossary Note entry, and epic-map Story 20 row still match what shipped.

## Design direction (frontend-design pass, 2026-08-29 — same theme, no new tokens)

**Thesis: two voices, one screen.** A report page carries foreign testimony (the
reporter's verbatim words) and the team's working apparatus (metadata, status, notes).
Today the apparatus visually outweighs the testimony; the rework inverts that, using only
the existing Largata language (Plus Jakarta Sans, Largata Red chrome, paper cards,
eyebrows, person hues).

- **Testimony block (the signature).** The description opens the content, unboxed — no
  card, words as material, not apparatus: `fonts.medium`, full ink, **19/28**; for long
  reports (>280 chars) it steps to **17/26** so a 2000-char wall stays a document, not a
  poster. Scale contrast does the emphasis — no quote rule, no background wash.
- **Status pill joins the top row.** The type glyph + eyebrow row gains the report's
  status pill (same component as the list rows), right-aligned — where a report stands is
  visible on arrival, not four blocks down.
- **Screen order:** type row + pill → testimony → screenshots → meta card (unchanged) →
  status block (rail + dismiss, unchanged) → **Notes**.
- **Notes = the team's ledger.** `Eyebrow: Notes`, then a flush Card with hairline
  dividers (the inbox list's own log language), oldest-first. Each entry: a small
  `colorForName` initial dot + author name + time as the caption line, body text under it
  (`type.body`). Person hues are the app's rule — "people are the only colourful thing" —
  so team voices get their colour and the foreign reporter never does. Edited notes append
  a quiet caption stamp: ~~`Edited · <editor> · <when>`~~ → **`Edited · <when>`** (the
  editor is always the author under the revised rule, so naming them only repeats the line
  above). A caption-weight `Edit` control sits in the meta line **of your own notes**
  (no hidden gestures — the long-press lesson; absent, not disabled, on a teammate's);
  editing happens inline with Save / Cancel.
- **Composer at the bottom:** multiline input on a hairline-bordered surface, placeholder
  "Write a note — decisions, context, next steps", button **Add note** (disabled while
  empty; character counter appears only near the 2000 limit). Empty state: "No notes yet —
  record the decision here so it isn't lost to chat."
- **Motion:** existing `FadeInView` stagger only; a newly added note fades in. Nothing new.
