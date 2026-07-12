# 00 — Requirements Brief  `[FRONT DOOR]`

**System:** Team Timesheet (internal work-log tool)
**Date:** 2026-07-13 · **Author:** largata.dev
**Input:** the design conversation that produced these decisions (the "discussion doc"
equivalent lived in chat, outside the repo). This file records its design-relevant
conclusions.

Each area carries a **Resolution**: ⬜ Agreed / Disputed / Undecided.

---

**Problem & why-now.**
A 4-person team is about to start a larger project and wants a lightweight, shared
record of what each person worked on. Today there is no consistent record (ad-hoc /
nothing / a spreadsheet). They want low-friction logging plus *visibility into what
the team is collectively doing* — not payroll, not approvals.
**Resolution: ⬜ Agreed**

**Actors.** *(one-way door → feeds 01, 02, 04)*
- **Member** — logs their own time; reads everyone's.
- **Admin** — a Member who can also manage accounts (create users, reset a password
  in the DB). No approval authority — admin is account management only.
**Resolution: ⬜ Agreed**

**Domain objects & journey.** *(feeds 02)*
- Objects: **User**, **TimeEntry**. (Project is a *deferred* future object — see non-goals.)
- Journey (Member): *log in → record a chunk of work (date + duration + description) →
  see it in my list → see the whole team's entries for the week → edit/delete my own.*
**Resolution: ⬜ Agreed**

**Invariants.** *(feeds 02)*
- **INV-1** A TimeEntry always has exactly one author (`user_id`), set from the
  authenticated identity — never from client input.
- **INV-2** Only the author may create, update, or delete their own entries.
  Everyone may *read* every entry (shared visibility).
- **INV-3** `duration_min > 0`.
- **INV-4** `entry_date` (day worked) is independent of `created_at` (row written).
**Resolution: ⬜ Agreed**

**Tenancy / scale.** *(one-way door → feeds 03)*
- **Single team, single tenant.** ~4 users now, room for a handful more. One global
  dataset; no per-tenant isolation boundary. Low volume — performance is a non-issue.
**Resolution: ⬜ Agreed**

**Constraints & non-goals.**
- Constraints: solo developer; this is a *side tool* supporting a bigger project, so
  minimise surface area. Backend must be Java/Spring; mobile client is Expo/React Native.
- **Non-goals (v1):** approvals/manager review · reporting beyond the shared feed ·
  **Projects/task hierarchy** (deferred, added later as a nullable field) · billing /
  payroll · activity monitoring · **offline & sync** · **multi-tenancy** · third-party
  integrations · self-service password reset · account lockout.
**Resolution: ⬜ Agreed**

**Kill criteria →** committed at the validation gate, not here. See
[07-epic-map.md](07-epic-map.md) / the validation gate note in
[01-intent-and-constraints.md](01-intent-and-constraints.md).

**Handoff gate.** All one-way-door areas (actors, tenancy, invariants) are **Agreed**;
no area is Disputed or Undecided → **cleared to begin design (Artifact 01).**
