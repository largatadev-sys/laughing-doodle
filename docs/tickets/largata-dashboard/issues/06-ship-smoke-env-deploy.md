# 06: Ship — smoke probe, env note, tracker rows, deploy, live check

Story 27 · Epic 4 · spec: [../spec.md](../spec.md) ("Environments & ops")

**What to build:** the Dashboard is live on dev and prod, verified by the standing
deploy ritual, and the Largata session can point its sender at a real receiver.

**Blocked by:** 05 (series).

**Status:** in progress — smoke probes, `.env.example` line and tracker rows done; squash to `dev`, deploy and the developer's live check on dev and prod pending

- [x] `scripts/smoke.sh` gains one probe: `POST /api/intake/events` without the secret →
      `401` (proves the route is wired), run against the local gate, dev and prod.
- [x] `.env.example`'s intake comment says the one secret now guards both intake routes.
      **No new environment variable** on either Railway environment.
- [ ] BUILD_STATUS and the epic map carry the Story 22–27 rows with their commits; ADR-014
      and the glossary are amended, dated, only if the build changed a decision.
- [ ] Squash into `dev`; smoke `dev`; promote to `main` (fast-forward); smoke prod including
      the CORS-behind-TLS-proxy check.
- [ ] The developer's live check on dev **and** prod: real browser login → Reports → strip
      → Dashboard renders — the only probe that proves the migration ran on that database;
      one ticket-02 script batch against dev shows verdicts and the strip updates.
- [ ] Reported as "automated checks pass; needs your live check" until the developer
      confirms — never as done on the automated checks alone.
- [x] BUILD_STATUS notes that real Events start when Largata's sender ships, and points the
      Largata session at the spec and the hand-off prompt beside it.
