# 02: The full text format

**What to build:** A Handoff's text grows from ticket 01's minimal version to the agreed
format, so each Report arrives with everything a fix needs: an index grouped by screen,
numbered sections, the Report's context lines, and the team's Notes. Client only — the
formatter is the one place the text is made. Spec: [../spec.md](../spec.md), "Text format".

**Blocked by:** 01 — Tracer bullet: hand off and see the text.

**Status:** done — implemented 2026-10-07 on `feature/28-report-handoff` via `/implement-spec` (backend suite green, client typechecks and lints clean, three-axis code review applied); the manual demo is the developer's, in ticket 05.

- [x] An index follows the header: Reports grouped by screen alphabetically, a final
      "(no screen)" group, oldest submission first within a group, numbered 1..N.
- [x] Each index line shows number, short id, type, and the description's first line, trimmed
      and cut at 80 characters with "…". Section headings reuse the same numbers.
- [x] Each section lists Status (the UI's label), Screen (only when present), Submitted (UTC),
      Traveler uid ("none (signed out)" when absent), and Device (whichever of platform, app
      version, OS, browser and device model are present). Empty fields are left out, never
      printed as "unknown".
- [x] Notes follow, oldest first, as "date · author: body", multi-line bodies continued on
      indented lines, "(edited date)" when edited; "Notes: none" when there are none.
- [x] The description is quoted verbatim with every line prefixed `> ` and blank lines kept as
      `>`; a reporter's own `#`, `|` or backticks stay inside the quote.
- [x] Every section is self-contained: no "see above", no cross-references beyond its number.
- [x] The reporter's name, screenshots or their count, worklog links, and any prompt never
      appear in the text.
- [x] The formatter stays a pure function with no React dependency.
- [x] Client typechecks and lints clean.
- [ ] Demo (manual — the formatter's only check, so every shape must be seeded): multi-line
      and Markdown-hostile descriptions, multi-line and edited Notes, a Report with no Notes,
      a signed-out reporter with a distinctive name, a pre-device-context Report, a Report with
      no screen, and an idea. Hand off, copy, paste into a plain editor, and check it byte for
      byte against the spec's rules; the reporter's name appears nowhere.

## Comments

- 2026-10-07 (implement-spec exploration): "Status" prints the UI's label exactly as the app shows
  it (e.g. "In progress"); where the spec's example differs, the UI label wins, per this ticket's
  criterion. The status-label module imports React, so the formatter must not import it — keep
  its own plain label map (or move the plain map somewhere React-free) so it stays pure.
- 2026-10-07 (code review, accepted readings): (a) line endings inside a quoted description are
  normalised to `\n` on purpose — the text is pasted into another tool, and mixed line endings there
  would be worse than not being byte-identical to the reporter's input. (b) The index summary uses
  the description's first non-blank line, so a description that opens with blank lines still gets a
  summary.
