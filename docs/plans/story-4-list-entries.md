# Story 4 — List entries (shared read, filterable)

**Status.** Planned, not yet built. Immutable once implementation lands — see
[BUILD_STATUS.md](../../BUILD_STATUS.md) for live status.

**Source.** [07-epic-map.md](../design/07-epic-map.md) Story 4, elaborated through a
`/grilling` session on 2026-07-13. This file records *why* each choice was made; the
epic map records *what* is required.

---

## Scope

`GET /api/entries?from=&to=&userId=` → all team members' entries in range (INV-2 read
side — any Member may read any entry), optionally filtered to one author, each entry
carrying `authorName`. Never `404` on an empty result.

## Decisions made this session (and why)

1. **`from` and `to` are both optional and independent.** Omitting both returns every
   entry (no pagination exists per [05](../design/05-api-conventions.md), and volume is
   trivial at 4 users); omitting one leaves that side of the range open. Rejected:
   requiring both together — that would force every caller (including Story 7's client
   and Story 8's team feed) to invent a synthetic wide range just to ask for "everything,"
   for a constraint the epic map never states.

2. **`from > to` (an inverted range) is not rejected — it silently matches zero rows and
   returns `200 []`.** Read as covered by AC-2's literal wording ("empty range → `200 []`,
   never `404`"): an inverted range is just one way to construct a range with no matches.
   Adding a `400` for this invents a rejection rule the epic map doesn't ask for, on a
   read endpoint where over-validating buys nothing (worst case the caller gets `[]` back).
   Matches Story 3 decision #7's restraint precedent (no invented range constraints).

3. **`userId` is a plain filter, not existence-checked against `users`.** A `userId` with
   no matching user naturally yields `200 []` — indistinguishable from "valid user, no
   entries in range yet," so an existence lookup (`404`/extra query) buys no observable
   benefit. Same reasoning as decision #2: a filter that matches nothing is an empty
   result, not an error.

4. **Malformed query params (`from=not-a-date`, `userId=abc`) → `400 VALIDATION_FAILED`**,
   via a new `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` added to the
   existing `GlobalExceptionHandler`, not manual string-parsing in the service. Spring
   MVC's default behavior on a failed `@RequestParam` type bind is an uncaught exception
   that falls through to the catch-all `Exception → 500` handler — the wrong code for
   ordinary bad client input. A global handler is a two-line, fully reusable fix (covers
   every current and future endpoint's query/path params), versus manual parsing per
   endpoint, which would manufacture logic around a framework capability that already
   exists (P9) for a case that isn't domain logic worth a unit test (06b's thin-unit dial).

5. **Author name is attached via two queries zipped in the service, not a SQL join.**
   `TimeEntryRepository` fetches filtered entries; `UserRepository.findAllById(distinct
   userIds)` fetches the relevant authors; `EntryService` zips them via a `Map<Long,
   String>` lookup into `EntryResponse.of(entry, authorName)`. Story 3 decision #4 deferred
   exactly this fork ("Story 4's list endpoint will need a different, join-shaped query
   for all authors anyway") without picking a mechanism. A join baked into
   `TimeEntryRepository` would reach into `users`' table shape from the `entries` module —
   sharper coupling than [04-architecture.md](../design/04-architecture.md)'s stated
   boundary ("share only the `User` identity, not an object graph"). At 4 users and
   trivial volume, two `IN`-shaped round-trips cost nothing observable, and this avoids
   re-opening the fetch-strategy/N+1 question Story 3 deliberately sidestepped.

6. **Optional filters are expressed as one `@Query` JPQL method with null-coalescing
   conditions** (`(:from IS NULL OR e.entryDate >= :from) AND (:to IS NULL OR e.entryDate
   <= :to) AND (:userId IS NULL OR e.userId = :userId)`), not 8 derived-method
   combinations and not the Specification/Criteria API. Three optional filters don't earn
   dynamic-query-builder machinery — matches 06b's "floor" dial for this module.

7. **Results are ordered `entryDate ASC, id ASC`.** The epic map doesn't specify an order;
   leaving it undefined makes response order nondeterministic (test-flaky) and Story 8's
   day-grouping team feed will want chronological order regardless, so this is the
   natural default rather than an arbitrary choice.

8. **No new response DTO — reuses the existing `EntryResponse`.** Story 3 decision #3
   already put `authorName` on `EntryResponse` in anticipation of this story; Story 4
   doesn't need to widen or duplicate the shape.

9. **No new unit test for `EntryService.list()`.** Per 06b's test-depth table, filtering
   is conditional SQL, not business logic — there is no real domain rule here to isolate
   (unlike Story 3's `durationMin > 0` check, which was unit-test-worthy per 06b's
   explicit naming). Coverage is integration-only (see Deliverables), matching "don't
   manufacture unit tests for anemic code."

## Mechanical facts (settled by existing precedent, not re-decided)

- `GET /api/entries` requires no new Spring Security wiring — `SecurityConfig`'s
  `anyRequest().authenticated()` already covers it (only `/api/health`,
  `/api/auth/login`, `/error` are `permitAll`), so a missing/invalid token already
  produces `401` via the existing `JwtAuthenticationFilter`/`JwtAuthenticationEntryPoint`.
- Package stays `com.largatadev.timesheet.entries`, extending the existing
  `EntryController`/`EntryService`/`TimeEntryRepository` classes (no new module).
- `200` status with a bare JSON array body, per
  [05-api-conventions.md](../design/05-api-conventions.md)'s GET-collection rule.

## Deliverables

- `TimeEntryRepository`: add `findByFilters(LocalDate from, LocalDate to, Long userId)`
  via `@Query`, ordered `entryDate ASC, id ASC`.
- `EntryService`: add `list(LocalDate from, LocalDate to, Long userId)` →
  `List<EntryResponse>`. Fetches filtered entries, collects distinct `userId`s, fetches
  those `User`s in one `findAllById` call, builds a `Map<Long, String>` of id→name, maps
  each entry to `EntryResponse.of(entry, nameMap.get(entry.getUserId()))`.
- `EntryController`: add `@GetMapping` handler with
  `@RequestParam(required = false) LocalDate from`,
  `@RequestParam(required = false) LocalDate to`,
  `@RequestParam(required = false) Long userId` → `200` + `List<EntryResponse>`.
- `GlobalExceptionHandler`: add `@ExceptionHandler(MethodArgumentTypeMismatchException.class)`
  → `400 VALIDATION_FAILED`.
- Integration test (`@SpringBootTest` + `MockMvc` + Testcontainers, matching existing
  pattern): entries from multiple seeded users all appear regardless of caller (AC-1);
  an out-of-range/inverted date range and a non-matching `userId` → `200 []` (AC-2);
  `from`/`to` bound by `entry_date`, `userId` filters to one author (AC-3); malformed
  `from`/`userId` → `400 VALIDATION_FAILED`; no token → `401`.

## Explicitly out of scope (do not build)

Aggregation/grouping by day or member (Story 8), pagination, a SQL join between
`time_entries` and `users`, a Specification/Criteria-API query builder, existence
validation of `userId` against `users`, rejecting inverted date ranges.
