# 06 — Ship it: environments, smoke, bookkeeping

**What to build:** The feature goes live and the record stays honest. The intake secret is
wired through every environment's configuration, the smoke script learns to probe the new
surface safely, the curated docs (epic map, BUILD_STATUS, deploy runbook) reflect the new
epic, and the feature deploys to prod — closing with automated checks green and the
explicit "needs your live check" handoff the standing deploy rule requires. Kept as its
own small ticket so the release act is visible work, not an afterthought.

**Blocked by:** 05 — Screenshots in the inbox (transitively: everything, 01–04).

**Status:** in progress — file work done; the promotion + deploy await the developer.

- [x] Intake-secret placeholder in `.env.example`, compose wiring documented, and the
      Railway variables table in the deploy runbook gains the new variable.
- [x] Smoke script probes intake **safely**: asserts a bad/missing secret → `401`; no
      happy-path probe against prod (it would write junk reports into the live inbox).
      Also asserts the two auth schemes don't bleed (the secret does not open `/api/reports`).
- [x] Verify the epic map's Reports-inbox section and BUILD_STATUS's Epic 3 story table
      are fully current (both were seeded at planning time; each story updates its own
      row + squash SHA as it lands — this ticket audits, it doesn't backfill).
- [ ] Deployed to prod (dev → main promotion per the git workflow); post-deploy smoke
      green against the live domain. **Held for the developer:** the promotion and deploy
      are outward-facing, and `REPORTS_INTAKE_SECRET` must be set in Railway first.
- [x] Close-out explicitly reports "automated checks pass; needs your live check" — never
      "done" — and notes that real end-to-end traffic starts only once the Largata-side
      half ships in that repo.
