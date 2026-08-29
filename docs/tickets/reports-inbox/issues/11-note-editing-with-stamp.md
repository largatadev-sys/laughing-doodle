# 11 — Note editing with the visible stamp

**What to build:** A Member rewords their own Note, and the ledger says so: an
"Edited · <when>" stamp that can't be forged or omitted. Someone else's Note is not theirs
to reword — a Note is signed testimony (ADR-012 as revised 2026-08-29).
The append-only guarantee stays structural — no delete exists anywhere. Spec:
`../../../plans/story-20-report-notes-inbox-clarity.md`;
parent: [ticket 08](08-report-notes-and-inbox-clarity.md).

**Blocked by:** 10 — Notes land end-to-end.

**Status:** done — implemented 2026-08-29 on `feature/story-20-report-notes-inbox-clarity`; backend suite green, client typechecks and lints clean, and the developer confirmed their own live check against the single-origin gate over LAN the same day.

- [x] `PUT /api/reports/{id}/notes/{noteId}` `{ "body": "..." }` → `200` updated Note,
      **author-only** (ADR-012 as revised); another Member's note → `403`. Editor and
      edited-at stamped server-side from the JWT, never the body. Unknown report/note →
      `404`; empty/oversized body → `400`, envelope key `body`.
- [x] **No DELETE route exists — asserted, not assumed:** a DELETE to the note path gets
      the standard method-not-allowed/404 treatment and the row survives.
- [x] Behavior tests: an edit changes body + stamp while author and created-at stay
      fixed; a re-edit replaces the stamp, never accumulates history.
- [x] Ownership tests: another Member's edit → `403` with the note byte-identical
      afterwards; the refusal is `403`, **not** `404` — every Member reads every note, so
      "not found" would be a lie — and the same caller still sees it on the inbox read.
- [x] Security tests: edit with no/invalid JWT → `401`, note unchanged; the intake
      secret cannot edit; the editor stamp ignores identity fields in the body.
- [x] Embedded notes in `GET /api/reports` carry the edit stamp when edited, absent
      otherwise.
- [x] Client: a quiet caption-weight Edit control on your **own** entries' meta line (a
      visible control, not a hidden gesture — the press-and-hold lesson); absent, not
      disabled, on a teammate's note; editing happens inline (the body becomes an input)
      with Save / Cancel; the stamp renders as "Edited · <when>" under the body.
- [x] Demo: edit your own note → the stamp appears; try a teammate's note via curl → `403`
      and the note is untouched after refetch.

## Comments

**2026-08-29 — implemented.** Every code criterion above is done and covered by the automated
suite. The unticked items are the ones an agent cannot honestly tick: they need a real browser
and the developer's own eyes (standing rule — automated checks narrow what can go wrong, they
do not substitute for a live check). The API half of the demos *was* driven live against
`bootRun` + the compose Postgres: intake `201`, note created with the author resolved from the
token, a second member's edit stamping them, `DELETE` on the note path `404`, and an intake
replay returning `"notes":[]`.

**2026-08-29 — scope revised mid-review, by the developer.** This ticket originally specified
editing open to any Member with an edited-by stamp. On first sight of the built screens the
developer reversed it: "only the member can be able to edit his notes... I thought the
implementation is just a general note for everyone, didn't know that it will be like this. but
I like this better." The attributed ledger made a Note read as signed testimony rather than a
shared scratchpad. Criteria above are rewritten to match; ADR-012, the spec amendment, the plan
spec, and the glossary all carry the revision. The `edited_by` column and the wire field stay
(they now always equal the author) — recording who acted beats assuming it.

**2026-08-29 — verified by the developer.** The manual and demo items above are now ticked on
the developer's own live check: the branch was served over LAN as the single-origin parity
image (`docker compose --profile fullstack`, `scripts/smoke.sh` 12/12 against the LAN address)
and exercised in a real browser. The inbox was seeded with the shapes those items call for — a
1479-char testimony, a signed-out report, reports with and without notes, notes by three
different authors including an edited one. Recording the developer's overall confirmation, not
an item-by-item observation of mine. **Local verification only: nothing here is deployed.**
