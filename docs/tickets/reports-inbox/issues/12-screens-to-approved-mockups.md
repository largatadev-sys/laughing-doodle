# 12 — The screens, to the approved mockups

**What to build:** The binding mockups made real (design canvas "Reports Inbox Rework",
approved 2026-08-29 — linked from [ticket 08](08-report-notes-and-inbox-clarity.md)).
The detail screen leads with the reporter's words at testimony scale and shows status on
arrival; the Notes ledger and composer take their final form; inbox rows grow a two-line
snippet and a note count. Same theme throughout — zero new tokens, zero new typefaces,
existing motion language only. Spec:
`../../../plans/story-20-report-notes-inbox-clarity.md`.

**Blocked by:** 10 — Notes land end-to-end · 11 — Note editing with the stamp
(all three touch the same detail screen; serialized on purpose).

**Status:** done — implemented 2026-08-29 on `feature/story-20-report-notes-inbox-clarity`; backend suite green, client typechecks and lints clean, and the developer confirmed their own live check against the single-origin gate over LAN the same day.

- [x] Small prefactor first: lift the list row's inline status-pill styles into one
      shared pill component, so the row and the detail top row render the same pill.
- [x] Detail top row: type glyph + type eyebrow + the status pill, right-aligned.
- [x] Testimony block: the description opens the content, unboxed, medium weight at
      19/28; descriptions over 280 characters step down to 17/26.
- [x] Screen order: type row → testimony → screenshots → metadata card (unchanged) →
      status rail + dismiss (unchanged) → Notes.
- [x] Notes section final form: "Notes" eyebrow; flush card with hairline dividers;
      entry meta line (person-hue initial dot — team voices only, the foreign Reporter
      never gets one — author, time, Edit control) above the body; edited stamp as a
      caption. Composer at the bottom; character counter appears only near the 2000
      limit; empty state: "No notes yet — record the decision here so it isn't lost to
      chat."
- [x] Inbox rows: snippet at two lines; meta line gains "· N note/notes" when a report
      has any; edge, pill, and hint line untouched.
- [x] Open filter (added 2026-08-29 from the live review): the inbox lands on **New** and
      resets to New whenever the Open segment is re-entered; the chips keep their toggle
      behaviour, so clearing the active one still shows every open report.
- [x] Status sheet responsiveness (same review): the picked row highlights, its dot
      springs, and a tick fades in immediately on press, ahead of the write landing;
      reduced-motion skips the animation and lands on the end state. A failed move shows
      its error **in the sheet** — the screen's error line is behind the modal, so it was
      previously unreachable.
- [x] Long-press no longer paints a browser text selection on web (`noTextSelect` beside
      `cursor: 'pointer'` on the Reports controls); prose stays selectable.
- [x] Manual UI checklist (standing no-automated-e2e decision): side-by-side with the
      canvas the screens match; a ~2000-char description renders at the reduced scale; a
      signed-out report still renders honestly in both surfaces; the accessibility labels
      still describe each row (including the note count). Developer live-check per the
      standing deploy rule.
- [x] Demo: open the report from the mockup's scenario next to the canvas — a founder
      can't tell which is which at arm's length.

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
