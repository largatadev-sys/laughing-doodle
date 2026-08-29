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

### Incoming feedback (Reports) — `[SETTLED 2026-08-13 · AMENDED 2026-08-28 · 2026-08-29]`

_Design closed 2026-08-13 (grilling → spec: `docs/tickets/reports-inbox/spec.md`;
architecture: ADR-010). A Report is **about the Largata trip-planning app** (the sibling
product), never about worklog itself._

- **Report** — one piece of feedback from a Largata user: a `type` (**problem** — "something's
  wrong" — or **idea** — "I have a suggestion"), free text, optional screenshot(s), and the
  reporter's identity carried as **data**.
- **Reporter** — the Largata user who filed a Report. **Foreign to worklog:** never a worklog
  User, never authenticates here — their name/UID travel as fields on the Report. Keeps the
  two user bases fully separate. **May be absent** (2026-08-28, contract v1.1): a Report
  filed from a signed-out Largata screen carries no identity at all, and the Inbox says so
  ("Signed out") — never a synthesized stand-in identity, which would be indistinguishable
  from a real Reporter in permanent data.
- **Screen context** — where the Reporter was **when they opened the report flow** (not
  where the bug happened — no client can know that), carried as an opaque Largata-minted
  string (v1.1). Optional forever; worklog never validates it against Largata's screens.
- **Device context** — what the Reporter was running when they filed: operating system,
  browser (browser Reports only — native-app-vs-browser itself is already the platform
  fact), and device model, each an opaque Largata-minted string. Optional forever;
  worklog never parses a user-agent and never keeps a vocabulary of browsers or OSes.
  _(Contract v1.2 — scoped 2026-08-29, **not yet built**; the live contract is v1.1.
  Story 21: `docs/tickets/story-21-device-context/`.)_
- **Inbox** — the worklog surface where Reports land and get worked: a fifth tab (bug icon)
  in the pill. Unlike TimeEntries, a Report has **no owner**: every Member reads and updates
  any Report equally.
- **Report status** — `new` (arrived, untouched) · `discuss` (UI "For discussion" — parked
  for a founders' decision) · `in progress` · `done` · `dismissed` (won't act). Free
  movement between statuses, any Member; who-last-changed + when is recorded. No
  assignment, no deletion — Reports are kept forever. (2026-08-29: the original
  "no comments" rule is superseded by **Notes**, below; comments in the threaded,
  reporter-visible sense stay excluded.)
- **Note** — team-authored prose attached to a Report (2026-08-29, ADR-012): status says
  *where* a Report stands, a Note says *why* — decisions, rationale, follow-ups. The log
  is **append-only** (a Note is never deleted; Reports and their Notes are kept forever),
  and a Note's text is **editable only by its author** (revised 2026-08-29 — a Note is
  signed testimony, so the same ownership rule INV-2 gives a time entry applies), always
  with a visible edited-at stamp. Never seen by the Reporter, never threaded — a Note is
  the team talking to its future self, not a conversation.
- **Screenshots** — optional, ≤3 per Report; the **bytes travel with the Report** through
  the relay (Largata's backend sanitizes/downsizes first) and **worklog owns its copy** —
  rendering the Inbox never depends on Largata being up.
- **The relay** — a Report reaches worklog only via Largata's backend, server-to-server,
  authenticated by a shared secret. Reporters are fire-and-forget: no status ever flows
  back to them in v1.

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
