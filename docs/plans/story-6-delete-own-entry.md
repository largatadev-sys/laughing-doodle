# Story 6 — Delete own entry (ownership enforced)

**Status.** Planned, not yet built. Immutable once implementation lands — see
[BUILD_STATUS.md](../../BUILD_STATUS.md) for live status.

**Source.** [07-epic-map.md](../design/07-epic-map.md) Story 6, elaborated through a
`/grilling` session on 2026-07-13. This file records *why* each choice was made; the
epic map records *what* is required.

---

## Scope

`DELETE /api/entries/{id}` → deletes the entry **only if the token's user is its author**
(INV-2 write side, same security surface as Story 5), else `403`. Idempotent: an
unknown or already-deleted id still returns `204`.

## Decisions made this session (and why)

1. **Idempotent `204` for an unknown/already-absent id — no `404` path in this flow at
   all.** The epic map's AC-3 explicitly picks "idempotent 204" per
   [05-api-conventions.md](../design/05-api-conventions.md)'s DELETE rule ("deleting an
   already-absent id still `204`"). This is a real divergence from Story 5's `update()`,
   which throws `NotFoundException` → `404` on a missing id: `delete()` never throws for
   "not found," it just no-ops. The one accepted side effect: a *different user's* id
   still leaks existence via `403` vs. the no-op `204` for a truly absent id — the same
   leak Story 5 already accepts for `PUT`, so this introduces nothing new.

2. **No admin bypass on ownership**, matching Story 5's decision #1. The ownership check
   ignores `role` entirely — an Admin has no more delete access to another user's entry
   than any other Member. INV-2 states only the author may delete their own entry with no
   role-based exception, and carving one out here (an *addition* to the authorization
   surface) would need the same explicit ask CLAUDE.md's stop rules require for touching
   INV-2 — asked, and the developer confirmed no bypass.

3. **Hard delete — no soft-delete/tombstone column.** [02-domain-model.md](../design/02-domain-model.md)
   describes `TimeEntry` as having no lifecycle state machine: "exists → may be edited →
   may be deleted," with no `status` field and no mention of retaining deleted rows for
   audit. Adding a `deleted_at` flag now would be an unasked-for schema change (also a
   CLAUDE.md stop-rule item) with no story or ADR calling for it. Confirmed explicitly
   given CLAUDE.md flags "deleting data" as a stop-and-ask item.

## Mechanical facts (settled by existing precedent, not re-decided)

- Order of checks in `EntryService.delete()`: `findById(id)` → if empty, **return
  immediately (no exception, no-op)** → if found and `entry.getUserId()` doesn't equal
  the caller's id (`AuthenticatedUser.userId()`), throw `ForbiddenException` (`403`) →
  else `timeEntryRepository.delete(entry)`.
- Cannot use `JpaRepository.deleteById()` directly — Spring Data's default
  `SimpleJpaRepository` impl throws `EmptyResultDataAccessException` when the row doesn't
  exist, which would break the idempotent-`204` contract decided above. Must load via
  `findById` first (which also doubles as the ownership-check lookup) and call
  `delete(entity)` only when present.
- `ForbiddenException` already exists in `com.largatadev.timesheet.error` (used since
  Story 5) — reused as-is; no new exception types needed. No `NotFoundException` path
  applies to this endpoint.
- A malformed `{id}` path variable (non-numeric) already produces `400 VALIDATION_FAILED`
  for free via the existing `MethodArgumentTypeMismatchException` handler in
  `GlobalExceptionHandler` (added in Story 4) — no new handler code needed.
- No repository changes: `TimeEntryRepository`'s inherited `findById`/`delete` cover this
  story; no new `@Query` needed.
- Response: `204 No Content`, no body, for both the no-op (absent id) and actual-delete
  paths — the caller cannot distinguish the two, per [05-api-conventions.md](../design/05-api-conventions.md)'s
  DELETE rule.
- Identity read via `@AuthenticationPrincipal AuthenticatedUser`, same as Stories 3/4/5 —
  no new security wiring; `SecurityConfig`'s `anyRequest().authenticated()` already
  covers this route, so a missing/invalid token already produces `401`.
- No cascade concerns: nothing else in the schema references `time_entries` by FK, so a
  plain row delete is sufficient (per the epic map's scope boundary — "no cascade beyond
  the single entry").

## Deliverables

- `com.largatadev.timesheet.entries` —
  - `EntryService`: add `delete(Long id, Long callerId)` (`void`). Loads via
    `timeEntryRepository.findById(id)`; returns immediately if empty; throws
    `ForbiddenException` if `entry.getUserId()` doesn't equal `callerId`; otherwise calls
    `timeEntryRepository.delete(entry)`.
  - `EntryController`: add `@DeleteMapping("/{id}")` handler with
    `@AuthenticationPrincipal AuthenticatedUser`, `@PathVariable Long id` → calls
    `entryService.delete(id, authenticatedUser.userId())` → `ResponseEntity.noContent().build()`
    (`204`, no body) always.
- Unit tests (`EntryServiceTest` additions, mirroring the existing `create`/`update`
  cases against a mocked `TimeEntryRepository`): author deletes own entry →
  `timeEntryRepository.delete(...)` invoked with the loaded entity; different user →
  `ForbiddenException` thrown, `.delete()` never invoked; unknown id → no exception,
  `.delete()` never invoked.
- Integration test (`DeleteEntryEndpointTest`, `@SpringBootTest` + `MockMvc` +
  Testcontainers, matching `EditEntryEndpointTest`'s setup/seed pattern): author deletes
  own entry → `204`, row confirmed gone via `timeEntryRepository.findById(...)` (AC-1); a
  different authenticated user deletes it → `403 FORBIDDEN`, row confirmed still present
  and unchanged (AC-2); unknown/already-deleted id → `204`, no side effects (AC-3); no
  token → `401`.

## Explicitly out of scope (do not build)

Admin override of ownership, soft-delete/tombstone/recovery, cascade deletes beyond the
single entry, bulk delete, any change to `update()`/`create()`/`list()`.
