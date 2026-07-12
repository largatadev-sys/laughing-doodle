# 03 — Tenancy & Isolation Model  `[COLLAPSED — single-tenant]`

Per the playbook: *"If the system is genuinely single-tenant, this collapses to a
one-line ADR — do not force it."* It does. This is that ADR.

---

**ADR-004 — Single-tenant, no isolation boundary.**

- **Context.** One team of ~4 uses this. Everyone is meant to see everyone's entries
  (shared visibility is the whole point). There is no second organisation to isolate from.
- **Decision.** **Single global dataset. No tenant concept, no `team_id`, no row-level
  isolation.** The only scoping in the system is *authorship*, and it governs **writes,
  not reads**: reads return all entries; writes are constrained to the author (INV-2,
  enforced at the service layer — see [04](04-architecture.md)).
- **Alternatives rejected.** Shared-schema + `team_id` scoping, schema-per-tenant,
  db-per-tenant — all reintroduce multi-tenant complexity (and its #1 failure mode: a
  leaky query crossing tenants) to solve a problem that does not exist for one team.
- **Assumption that makes this right.** There is, and will be, exactly one team.
- **What would invalidate it.** A second, *separate* team/org needing its data walled off
  from the first. If that day comes, the migration is additive-but-invasive: add
  `team_id` to `users` and `time_entries`, backfill the existing team, and scope every
  read/write by the authenticated user's team. Named here so it is a conscious future
  cost, not a surprise.
