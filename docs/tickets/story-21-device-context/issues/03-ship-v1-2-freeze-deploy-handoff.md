# 03 — Ship v1.2: freeze the contract, deploy, hand off

**What to build:** The contract stops being "planned". The spec's wire-contract sections
(`../../reports-inbox/spec.md` — the spec stays in `reports-inbox/` as the single
hand-off artifact) are edited in place to v1.2 and the amendment marked implemented; the
build reaches dev and prod; the amended spec goes to the Largata-side session as its
starting input. **Only after this ticket may Largata start sending the fields** —
deployments before it silently drop unknown JSON, so device context sent early is lost
for good.

**Blocked by:** [02 — Device context tracer bullet](02-device-context-tracer-bullet.md).

**Status:** ready-for-human — docs flipped and deployed to both environments 2026-08-29
(smoke ALL PASS everywhere; `dev` and `main` both at `714dc70`, all three URLs serving
`entry-7bbdce74…`). Remaining: the developer's live check on the deploy, then the
Largata-side hand-off.

- [x] Docs flip to "implemented": spec wire-contract sections edited in place to v1.2 +
      the "planned" amendment marked implemented (and the header's "Planned" pointer
      retired); **ADR-013** recorded in 04; domain model (02) drops the
      Device-context "not yet built" marker; epic map retires the unscheduled-candidate
      pointer (Story 21 now sits with the other Epic 3 stories); BUILD_STATUS Story 21 row.
- [x] Ticket 01's checklist reconciled — it stays the scoping record; this ticket and 02
      are its implementation slices.
- [x] Deploy in the standing sequence: local fullstack gate + smoke first, smoke the dev
      environment, then promote `dev` → `main` and deploy prod; smoke ALL PASS on both
      (CORS-behind-TLS-proxy check included). Done 2026-08-29 — gate, dev,
      `worklog.largata.com` and `largata-ts.up.railway.app` all ALL PASS; the new bundle
      `entry-7bbdce74…` is served on every one of them, replacing `entry-0d7a504e…`.
      V7 applying on the two managed databases is **inferred** (booting app + Flyway wired
      + failed migrations are fatal), not directly observed — no environment credentials
      here.
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
