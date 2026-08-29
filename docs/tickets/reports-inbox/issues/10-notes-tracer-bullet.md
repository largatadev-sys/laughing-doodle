# 10 — Notes land end-to-end (tracer bullet)

**What to build:** A Member opens a report, writes a Note, and it's there — permanently,
attributed, and still there after a refetch. The narrow-but-complete path through every
layer: the `report_notes` schema (landed once, **including** the editor columns ticket 11
will use), the create endpoint, notes embedded in the reports read, and a minimal ledger +
composer on the detail screen (read + add only — no editing, no visual polish; that's
tickets 11 and 12). Spec: `../../../plans/story-20-report-notes-inbox-clarity.md`;
parent: [ticket 08](08-report-notes-and-inbox-clarity.md).

**Blocked by:** None (can start immediately).

**Status:** done — implemented 2026-08-29 on `feature/story-20-report-notes-inbox-clarity`; backend suite green, client typechecks and lints clean, and the developer confirmed their own live check against the single-origin gate over LAN the same day.

- [x] Migration V6: `report_notes` — server-generated UUID PK · report FK NOT NULL ·
      author FK `users(id)` NOT NULL · body ≤2000 NOT NULL · created_at NOT NULL ·
      editor FK NULL · edited_at NULL. No change to `reports`. Verified with a live
      `bootRun` against real Postgres (the silent-Flyway lesson), not just the test suite.
- [x] `POST /api/reports/{id}/notes` `{ "body": "..." }` → `201` created Note; author and
      created-at stamped server-side from the JWT, never the body. Unknown report →
      `404`; empty/oversized body → `400`, envelope key `body`.
- [x] `GET /api/reports` embeds each report's notes **oldest-first** with resolved author
      names (same pattern as the status-changed-by name).
- [x] Security tests (mandatory 06b depth, `*EndpointTest` style): create with no/invalid
      JWT → `401`, nothing persisted; the intake secret grants no access to notes routes;
      author comes from the JWT even when the body smuggles identity fields.
- [x] Behavior tests: create → embed round-trip; ordering stable oldest-first; boundary
      bodies (1 and 2000 accepted, 0 and 2001 rejected); notes never leak onto the intake
      surface or responses.
- [x] Client: the reports provider carries notes on each report; add-note merges the
      returned Note into the cached list. Detail screen gains a minimal Notes section:
      entries as person-hue dot + author + time above the body, composer with the
      "Write a note — decisions, context, next steps" placeholder and an Add note button
      disabled while empty.
- [x] Demo: add a note in the app → it appears at the log's bottom with your name and
      survives a refetch; add one via curl as a second seeded user → it appears under
      yours after refresh.

## Comments

**2026-08-29 — implemented.** Every code criterion above is done and covered by the automated
suite. The unticked items are the ones an agent cannot honestly tick: they need a real browser
and the developer's own eyes (standing rule — automated checks narrow what can go wrong, they
do not substitute for a live check). The API half of the demos *was* driven live against
`bootRun` + the compose Postgres: intake `201`, note created with the author resolved from the
token, a second member's edit stamping them, `DELETE` on the note path `404`, and an intake
replay returning `"notes":[]`.

**2026-08-29 — verified by the developer.** The manual and demo items above are now ticked on
the developer's own live check: the branch was served over LAN as the single-origin parity
image (`docker compose --profile fullstack`, `scripts/smoke.sh` 12/12 against the LAN address)
and exercised in a real browser. The inbox was seeded with the shapes those items call for — a
1479-char testimony, a signed-out report, reports with and without notes, notes by three
different authors including an edited one. Recording the developer's overall confirmation, not
an item-by-item observation of mine. **Local verification only: nothing here is deployed.**
