# 03 — Ship v1.2: freeze the contract, deploy, hand off

**What to build:** The contract stops being "planned". The spec's wire-contract sections
(`../../reports-inbox/spec.md` — the spec stays in `reports-inbox/` as the single
hand-off artifact) are edited in place to v1.2 and the amendment marked implemented; the
build reaches dev and prod; the amended spec goes to the Largata-side session as its
starting input. **Only after this ticket may Largata start sending the fields** —
deployments before it silently drop unknown JSON, so device context sent early is lost
for good.

**Blocked by:** [02 — Device context tracer bullet](02-device-context-tracer-bullet.md).

**Status:** ready-for-agent

- [ ] Docs flip to "implemented": spec wire-contract sections edited in place to v1.2 +
      the "planned" amendment marked implemented (and the header's "Planned" pointer
      retired); ADR (next free number) recorded in 04; domain model (02) drops the
      Device-context "not yet built" marker; epic map retires the unscheduled-candidate
      pointer; BUILD_STATUS Story 21 row.
- [ ] Ticket 01's checklist reconciled — it stays the scoping record; this ticket and 02
      are its implementation slices.
- [ ] Deploy in the standing sequence: local fullstack gate + smoke first, smoke the dev
      environment, then promote `dev` → `main` and deploy prod; smoke ALL PASS on both
      (CORS-behind-TLS-proxy check included).
- [ ] The developer's live check against the deploy (standing rule: automated checks
      alone are never "done") — open Reports on prod in a real browser; once a v1.2
      report exists, confirm its Device row renders.
- [ ] Hand-off: the amended spec to the Largata-side session (capture os / browser /
      deviceModel on the reporter's device at report time — that repo's build reads the
      field-semantics item in ticket 01). Until it ships, every arriving report stays
      blank on these fields — expected, and blanks are permanent (nothing back-fills).

Note at slicing time (2026-08-29): the Stories 19/20 prod live check was itself still
outstanding — do it before stacking this deploy on top, so two deploys never sit
unverified at once.
