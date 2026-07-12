# 01 — Intent & Constraints  `[ALWAYS FIRST — declares the build mode]`

The "no" machine. If a proposed feature doesn't serve what's below, it's rejected.

---

**Build mode.** **Product (internal tool)** — primary — **with a Learning sub-objective.**

- *Why Product, not Learning, is primary:* the web app is a **real tool 4 people will
  actually use** to track work for a bigger project. That means the domain model and the
  authorization boundary must be *correct*, not throwaway — which is Product-mode depth,
  not Learning-mode "cut everything not serving the lesson." So the playbook's default
  depth map holds (00–02, 04 production; 03 collapsed; 05–07 thin).
- *The Learning sub-objective* explains one otherwise-unjustified choice — building a
  native mobile client at all. See success condition below.

**Why this exists (success condition).**

- **Product (primary).** Hypothesis: *We believe the 4 of us will consistently log our
  work in this tool — instead of not tracking, or a scattered spreadsheet — because it's
  low-friction and gives the team shared visibility.*
  Done (for the tool) = the team adopts it and keeps using it through the bigger project.
  **Kill criteria** committed at the validation gate (see [07-epic-map.md](07-epic-map.md)):
  if, after ~2 weeks of the bigger project, fewer than half the team log regularly, the
  tool isn't earning its place — simplify or drop it.

- **Learning (sub).** *I am building the client in Expo/React Native to understand (a)
  the single-codebase RN-Web model — one codebase serving web + native — and (b) Spring
  Security with BCrypt + JWT end to end.* Done when I can explain the RN-Web renderer
  split and the JWT issue/verify/expiry tradeoff. **The native mobile app is a practice
  target that runs locally only; the web export is the tool the team uses.** When web and
  mobile conflict, **web wins** — it has the real users.

**Actors.** Member · Admin. (See [00](00-requirements-brief.md) for definitions.)

**Non-goals.** Approvals · reporting beyond the shared feed · Projects (deferred) ·
billing/payroll · activity monitoring · offline/sync · multi-tenancy · integrations ·
self-service password reset · lockout · native app distribution (v1).

--- Product mode ---

**Who pays / primary user.** No payer — internal tool. Primary user = a team Member.

**Scale assumptions.** ~4 users, growing to maybe ~10 at most. Handful of entries per
person per day. Data volume trivial for years. Orders of magnitude: 10¹ users, 10³–10⁴ rows.

**NFRs.**
- Availability — best-effort (a small internal tool; brief downtime is fine).
- Latency — non-critical; anything sub-second is ample at this volume.
- Consistency — **strong**, trivially: single Postgres, single node, no replication.
- Compliance — none (no external users, no regulated data; treat emails/passwords with
  ordinary care — hashing + HTTPS).
