# 01: Tracer bullet — hand off and see the text

**What to build:** A Member on the web app's Open tab presses "Hand off", ticks a few open
Reports, and presses "Hand off (N)". Worklog records a Handoff (who, when, which Reports, the
exact text) and opens it: the raw text with a Copy button. This is the narrowest complete path
through every layer: new tables, the new `handoffs` module, the create and read-one routes, the
selection mode, and the Handoff screen. The text is deliberately minimal here — the header
plus each Report's short id, type and quoted description; ticket 02 completes the format.
Spec: [../spec.md](../spec.md).

**Blocked by:** None (can start immediately).

**Status:** done — implemented 2026-10-07 on `feature/28-report-handoff` via `/implement-spec` (backend suite green, client typechecks and lints clean, three-axis code review applied); the manual demo is the developer's, in ticket 05.

- [x] Migration V9 adds the `handoffs` and `handoff_reports` tables as the spec describes
      (schema change signed off by the developer, 2026-10-07); nothing alters an existing table.
- [x] A new `handoffs` feature module, laid out package-by-role like `dashboard/`, with an
      architecture test pinning who may touch its repository and domain packages.
- [x] `POST /api/handoffs` takes the ordered Report ids and the text, and returns 201 with the
      full Handoff. The creator is the caller's JWT identity; a creator in the body is ignored.
- [x] `POST` answers 400 `VALIDATION_FAILED` (error envelope per 05) for: missing or empty id
      list, a duplicate id, an unknown Report id (details name it), missing or blank text, more
      than 200 Reports, or text over 1,000,000 characters. 401 without a token.
- [x] `GET /api/handoffs/{id}` returns the Handoff in full (summary fields, text byte for byte,
      ordered Report ids); 404 when unknown. No PUT, PATCH or DELETE route exists.
- [x] API integration tests cover every criterion above, in the style of the Note endpoint tests.
- [x] Web only: the Open tab header shows a "Hand off" button that enters selection mode; rows
      gain checkboxes; a bottom bar shows "Hand off (N)" (disabled at zero) and Cancel. The
      button does not render on native.
- [x] Pressing "Hand off (N)" builds the text with a pure formatter function (no React
      dependency), posts it, leaves selection mode, and opens the new Handoff's screen. A failed
      post keeps the selection and shows the error.
- [x] Handoff screen: header line (when, who, how many) and the raw text, monospace and
      scrollable, with Copy. Success says "Copied"; failure says so and leaves the text
      selectable.
- [x] Minimal text: `# Handoff · <UTC time> · N reports`, `By <name>`, then per Report a
      `## <n>. <short id> · <type>` heading and its description quoted verbatim, line by line.
- [x] Backend suite green; client typechecks and lints clean.
- [ ] Demo (manual, standing no-e2e decision): on the local gate, select two Reports, hand
      off, copy, paste into a plain editor; the pasted text matches the screen.

## Comments

- 2026-10-07 (LAN check): superseded on the developer's call — the header "Hand off" button and
  the separate bottom bar this ticket built were replaced by the nav bar's centre disc (send
  glyph on the Open segment) and the nav bar morphing into "✕ · Hand off (N)". See the spec's
  Further Notes. The criteria above are left as built, as a record.
