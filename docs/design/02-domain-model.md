# 02 — Domain Model  `[PRODUCTION DEPTH]`

The most important artifact. An agent can infer a schema; it cannot infer these
invariants — so they are written here, once, authoritatively.

---

## Glossary (ubiquitous language)

- **Member** — a person on the team who logs their own time and reads everyone's.
- **Admin** — a Member who can additionally manage accounts. *Not* an approver.
- **TimeEntry** — one logged chunk of work: an author, a day, a duration, a description.
- **Duration** — length of work in **whole minutes** (never a float; see below).
- **Entry date** — the day the work was *done* (distinct from when the row was written).
- **Shared visibility** — every Member can read every TimeEntry; writes are author-only.

## High-level flow (the journey — narrative, not screens)

**Member:** `log in → create a TimeEntry (date, duration, description) → it appears in
my list → view the whole team's entries for a period → edit or delete my own entries.`

**Admin:** `everything a Member does + create a new user account / reset a password.`

There is no submit → review → approve path — that is an explicit non-goal. The journey
ends at "recorded and visible," not "approved."

## Entities

- **User** — `id · name · username (unique, = login id) · password_hash (BCrypt) ·
  role (member|admin) · created_at`. Purpose: an authenticatable person and the author
  of entries.
- **TimeEntry** — `id · user_id →User · entry_date · duration_min (int, >0) ·
  description · created_at · updated_at`. Purpose: one unit of logged work.
  *(Future, additive: nullable `project_id →Project`.)*

## Aggregates

- **User** is its own aggregate root. Consistency boundary = the single user row.
- **TimeEntry** is its own aggregate root. It is created and modified **independently**
  (no batch/timesheet-submission object wraps it in v1), so its consistency boundary is
  the single entry. Its only cross-aggregate link is the `user_id` reference to its
  author — a reference, not containment.
- **No `Team` aggregate.** Single-tenant: "the team" = all rows in `users`. Modelling a
  one-row Team would be encoding a constant as data. (See [03](03-tenancy-model.md).)

## Invariants

- **INV-1** Every TimeEntry has exactly one author (`user_id NOT NULL`), stamped from the
  authenticated identity — the server **ignores any client-supplied user id**.
- **INV-2** Only the author may create/update/delete their own entry. Any Member may read
  any entry. *(This is the system's one real security surface — see [04](04-architecture.md)
  ADR-002 and the enforcement note.)*
- **INV-3** `duration_min > 0` (a zero/negative chunk of work is meaningless).
- **INV-4** `entry_date` and `created_at` are independent. People log Friday's work on
  Monday; conflating them corrupts the shared feed's grouping.
- **INV-5** `username` is unique across users, case-insensitively (it is the login
  identifier).

## State machines

- **TimeEntry — no lifecycle state machine in v1.** Because approvals are out of scope,
  an entry has no draft/submitted/approved states. It is a **mutable record** that exists
  → may be edited → may be deleted. That absence is a *decision*, not an oversight: adding
  a `status` field later (if approvals are ever wanted) is additive and does not disturb
  existing data.
- **User — no lifecycle in v1** (always active). A future `active|deactivated` flag is a
  two-way door; deferred.
