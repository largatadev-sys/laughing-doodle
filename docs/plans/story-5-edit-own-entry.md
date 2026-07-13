# Story 5 — Edit own entry (ownership enforced)

**Status.** Planned, not yet built. Immutable once implementation lands — see
[BUILD_STATUS.md](../../BUILD_STATUS.md) for live status.

**Source.** [07-epic-map.md](../design/07-epic-map.md) Story 5, elaborated through a
`/grilling` session on 2026-07-13. This file records *why* each choice was made; the
epic map records *what* is required.

---

## Scope

`PUT /api/entries/{id}` → updates the entry **only if the token's user is its author**
(INV-2 write side — this is the security-critical story), else `403`. Unknown id → `404`.

## Decisions made this session (and why)

1. **No admin bypass on ownership.** The ownership check ignores `role` entirely — an
   Admin has no more write access to another user's entry than any other Member.
   [02-domain-model.md](../design/02-domain-model.md) defines Admin as "a Member who can
   additionally manage accounts. *Not* an approver," and INV-2 states only the author may
   update their own entry with no role-based exception. Carving one out here would be
   exactly the kind of INV-2 weakening CLAUDE.md's stop rules forbid without an explicit
   ask — asked, and the developer confirmed no bypass.

2. **`PUT` is a full replace, not a partial update.** All three fields (`entryDate`,
   `durationMin`, `description`) are required on every edit and validated identically to
   create (missing/invalid → `400 VALIDATION_FAILED`); there is no "omit a field to leave
   it unchanged" semantics. Matches `PUT`'s conventional meaning and
   [05-api-conventions.md](../design/05-api-conventions.md), which documents `PUT` with no
   partial-update behavior anywhere in this codebase. A future `PATCH` would be the natural
   home for partial updates if ever needed.

3. **New `UpdateEntryRequest(LocalDate entryDate, Integer durationMin, String
   description)` record**, distinct from `CreateEntryRequest`, despite currently having
   identical fields. A request DTO describes what one specific endpoint accepts, not a
   data shape in the abstract; binding `CreateEntryRequest` to both `POST` and `PUT` would
   make the type lie about which endpoint it belongs to, and the two endpoints have no
   reason to stay in lockstep going forward (e.g. ADR-007's future `projectId` could land
   on create before edit). Three duplicated fields is well below the threshold where P9
   would call a shared type warranted.

4. **Concurrent edits (lost-update / stale-write races) are explicitly deferred —
   last-write-wins, no `version` column, no schema change.** Because INV-2 already blocks
   cross-user writes, the only realistic race is **the same account writing from two
   sessions at once** — concretely possible here because ADR-002's JWTs are long-lived
   (~7-day TTL, no revocation, no single-session enforcement) and Story 7's Expo client
   targets web *and* native from one account simultaneously. The concrete failure mode: a
   stale tab's `PUT` silently overwrites a newer edit made from another session, with no
   error returned — a real but self-inflicted, low-stakes gap (never cross-user, always
   recoverable by re-editing), and no AC in the epic map asks for a guard against it.
   Deferred deliberately rather than by omission — **revisit before/at Story 7**, once a
   real multi-session client exists and the risk moves from theoretical to plausible;
   retrofitting after Story 7's client contract is built (and possibly deployed, per the
   epic map's note that deploy can be pulled forward right after Story 3) would mean
   reworking an already-shipped edit-form contract instead of adding one field now. `06b`'s
   error taxonomy already reserves `CONFLICT → 409` (currently only used for duplicate
   username), so the wiring wouldn't be foreign to this codebase if/when this is picked up.

## Mechanical facts (settled by existing precedent, not re-decided)

- Order of checks in `EntryService.update()`: load by id (`404` via `NotFoundException` if
  missing) → compare `entry.getUserId()` to the caller's id from
  `AuthenticatedUser.userId()` (`403` via `ForbiddenException` if it doesn't match) →
  validate the request → apply changes → stamp `updatedAt` → save. Directly forced by
  AC-2/AC-3's wording (unknown id is `404` regardless of ownership; ownership is only
  checked once the entry is known to exist).
- `ForbiddenException` and `NotFoundException` already exist in
  `com.largatadev.timesheet.error` (unused so far) — reused as-is; no new exception types.
- A malformed `{id}` path variable (non-numeric) already produces
  `400 VALIDATION_FAILED` for free via the `MethodArgumentTypeMismatchException` handler
  `GlobalExceptionHandler` gained in Story 4 — no new handler code needed.
- `updatedAt` is stamped from a fresh `OffsetDateTime.now()` in the service at save time;
  `createdAt` is left untouched — the exact pattern Story 3 decision #5 pre-committed this
  story to ("one pattern for both," DB-`DEFAULT` on insert, app-set on update).
- The three-field validation block (`durationMin > 0`, non-blank `description`, present
  `entryDate`) is extracted into a small shared private method reused by both `create()`
  and `update()`, rather than duplicated inline a second time — plain DRY, no new
  machinery, no design-doc impact.
- No repository changes: `TimeEntryRepository`'s inherited `findById`/`save` cover this
  story; no new `@Query` needed (unlike Story 4's filtered list).
- Response: `200` with the updated `EntryResponse` (reused as-is), per
  [05-api-conventions.md](../design/05-api-conventions.md)'s `PUT` rule.
- Identity read via `@AuthenticationPrincipal AuthenticatedUser`, same as Stories 3/4 — no
  new security wiring; `SecurityConfig`'s `anyRequest().authenticated()` already covers
  this route, so a missing/invalid token already produces `401`.

## Deliverables

- `com.largatadev.timesheet.entries` —
  - `UpdateEntryRequest(LocalDate entryDate, Integer durationMin, String description)`.
  - `TimeEntry`: add a package-private/entity-level setter path (or a single `applyUpdate(...)`
    method) so `EntryService` can mutate `entryDate`/`durationMin`/`description` on the
    managed entity before save, mirroring the constructor-based creation style used for
    `create()`.
  - `EntryService`: add `update(Long id, Long callerId, UpdateEntryRequest request)` →
    `EntryResponse`. Loads via `timeEntryRepository.findById(id).orElseThrow(() -> new
    NotFoundException(...))`; throws `ForbiddenException` if `entry.getUserId()` doesn't
    equal `callerId`; validates via the shared validation method; applies the three
    fields; stamps `updatedAt = OffsetDateTime.now()`; saves; maps to `EntryResponse` (author
    name looked up the same way `create()` does).
  - `EntryController`: add `@PutMapping("/{id}")` handler with
    `@AuthenticationPrincipal AuthenticatedUser`, `@PathVariable Long id`,
    `@RequestBody UpdateEntryRequest request` → `200` + `EntryResponse`.
- Integration test (`EditEntryEndpointTest`, `@SpringBootTest` + `MockMvc` +
  Testcontainers, matching `CreateEntryEndpointTest`/`ListEntriesEndpointTest`'s pattern):
  author edits own entry → `200`, fields updated, `updatedAt` changed (AC-1); a different
  authenticated user edits it → `403 FORBIDDEN`, entry unchanged in the DB (AC-2); unknown
  id → `404 NOT_FOUND` (AC-3); invalid body (`durationMin <= 0` / missing field) →
  `400 VALIDATION_FAILED`; no token → `401`.

## Explicitly out of scope (do not build)

Delete (Story 6), admin override of ownership, partial-update (`PATCH`-style) semantics,
optimistic concurrency / lost-update protection (`version` column, `If-Match`, or any
`409 CONFLICT` on stale writes — deferred per decision #4), changing an entry's authorship,
reusing `CreateEntryRequest` for the update body.
