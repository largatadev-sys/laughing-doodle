# Story 3 — Create a time entry

**Status.** Planned, not yet built. Immutable once implementation lands — see
[BUILD_STATUS.md](../../BUILD_STATUS.md) for live status.

**Source.** [07-epic-map.md](../design/07-epic-map.md) Story 3, elaborated through a
`/grilling` session on 2026-07-13. This file records *why* each choice was made; the
epic map records *what* is required.

---

## Scope

`POST /api/entries {entryDate, durationMin, description}` → creates an entry **authored
by the token's user** → `201` with the entry. Identity comes from `AuthenticatedUser` in
the security context (INV-1); any client-supplied user id is ignored.

## Decisions made this session (and why)

1. **Manual validation in the service, not Bean Validation.** No `spring-boot-starter-validation`
   dependency exists yet (Story 2 deliberately deferred field validation to this story —
   `LoginRequest` has none). Adding a validation framework for three checks
   (`durationMin > 0`, non-blank `description`, present `entryDate`) is more machinery than
   the requirement earns (P9 restraint), and 06b's test-depth table already names "duration
   validation (INV-3)" as unit-test-worthy real logic — that only makes sense if the check
   *is* app code, not a declarative annotation Spring evaluates for you. Matches the
   existing convention of `AuthService` throwing typed exceptions directly rather than
   using framework validation.

2. **`CreateEntryRequest(LocalDate entryDate, Integer durationMin, String description)` has
   no `userId` field.** AC-1 requires that a client-supplied `userId` in the body is
   ignored; Spring Boot's default Jackson config (no override in `application.properties`)
   already ignores unknown JSON properties, so omitting the field achieves "ignored" with
   zero code. Explicitly declaring-and-discarding a `userId` field would leave a
   parsed-but-unused field in the DTO — the kind of dead-looking code P9 flags on review.

3. **`EntryResponse` includes `authorName`**, even though Story 3 only ever returns your
   own just-created entry to yourself. Developer's explicit call, overriding the
   session's initial recommendation (which favored a minimal per-story DTO, deferring
   `authorName` to Story 4's list endpoint) — the team-visibility requirement means every
   entry is eventually read by others, and the developer chose to carry the author's name
   on the resource from the first story that returns one, rather than widen the shape later.

4. **`TimeEntry.userId` is a plain `Long` column — no JPA `@ManyToOne` to `User`.**
   Keeps `entries` and `users` decoupled at the persistence layer, matching
   [04-architecture.md](../design/04-architecture.md)'s stated module boundary ("share
   only the `User` identity," not an object graph) and the existing style (`User` itself
   has no relationships; `Role` is a converted column, not an association). Avoids
   introducing fetch-strategy/N+1 questions this codebase hasn't needed yet — Story 4's
   list endpoint will need a different, join-shaped query for *all* authors anyway, so a
   `@ManyToOne` here wouldn't even be reused there. `EntryService.create()` calls
   `userRepository.findById(userId)` explicitly to get the name for `authorName` (decision
   #3's consequence).

5. **`createdAt`/`updatedAt` are set by the application**, both from a single
   `OffsetDateTime.now()` call, before `save()` — not left to the DB's `DEFAULT now()`.
   Simpler than marking columns `insertable = false` and re-fetching after insert (which
   would need Hibernate-specific `@Generated` machinery, not part of plain JPA), guarantees
   `createdAt == updatedAt` at the exact same instant, and establishes the convention
   Story 5 (edit) will need anyway when it sets `updatedAt` explicitly on update — one
   pattern for both, rather than "DB sets it on insert, app sets it on update."

6. **Blank/whitespace-only `description` is rejected as `VALIDATION_FAILED`**, same as a
   missing one. The DB's `NOT NULL` constraint alone would accept `""`; AC-2's "missing
   required field" is read as covering "provides no information," not just "field absent"
   — a blank description is as meaningless as `duration_min <= 0` (INV-3's spirit).
   Developer's judgment call, since the epic map only literally states the null/absent case.

7. **No range constraint on `entryDate`** (no "not in the future" / "not too far past"
   rule). INV-4 explicitly decouples `entry_date` from `created_at` ("people log Friday's
   work on Monday") — inventing a bound risks contradicting that invariant's intent, and no
   AC asks for one. Required-only, matching AC-2 as written.

8. **Validation logic lives inline in `EntryService.create()`** — no separate validator
   class. Exactly one caller exists today; extracting a class ahead of a second caller has
   no seam to justify it (P9). The checks stay directly unit-testable by calling
   `EntryService.create()` with a bad DTO and asserting the thrown `ValidationException`.

9. **If `userRepository.findById(userId)` (for `authorName`) finds nothing, `.orElseThrow()`
   with no dedicated exception** — falls through to `GlobalExceptionHandler`'s existing
   catch-all `Exception` handler (logs once, generic `500 INTERNAL`, no detail leaked).
   Under this system's invariants a valid JWT's user cannot be missing (`User` has no
   delete/deactivate lifecycle in v1 — domain model: "always active"), so this is an
   impossible state, not a business case worth a named exception (P4: fail fast on the
   impossible without manufacturing ceremony around it).

## Mechanical facts (settled by existing precedent, not re-decided)

- Package `com.largatadev.timesheet.entries`: `EntryController`, `EntryService`,
  `TimeEntryRepository` (`JpaRepository<TimeEntry, Long>`, no custom query methods yet —
  those are Story 4's job), `TimeEntry` (`@Entity`), `CreateEntryRequest`, `EntryResponse`.
  Matches the `auth`/`users` module-per-package convention from
  [04-architecture.md](../design/04-architecture.md).
- Identity read via `@AuthenticationPrincipal AuthenticatedUser` as a controller method
  parameter — works out of the box (Spring Security's principal-argument resolver matches
  the type `JwtAuthenticationFilter` already places on the `SecurityContext`); no new
  security wiring needed.
- `201` status with the created `EntryResponse` body, per
  [05-api-conventions.md](../design/05-api-conventions.md)'s POST rule.
- No repository-level test (unlike `UserRepositoryTest`, which exists because
  `findByUsername` is a custom query worth verifying against seeded data).
  `TimeEntryRepository` has only the inherited `save()` in this story — nothing custom to
  test at that layer yet.

## Deliverables

- `com.largatadev.timesheet.entries` —
  - `TimeEntry` (`@Entity`, `@Table(name = "time_entries")`): `id`, `userId` (plain
    `Long`), `entryDate` (`LocalDate`), `durationMin` (`Integer`), `description` (`String`),
    `createdAt`/`updatedAt` (`OffsetDateTime`).
  - `TimeEntryRepository extends JpaRepository<TimeEntry, Long>`.
  - `CreateEntryRequest(LocalDate entryDate, Integer durationMin, String description)`.
  - `EntryResponse(Long id, Long userId, String authorName, LocalDate entryDate, Integer durationMin, String description, OffsetDateTime createdAt, OffsetDateTime updatedAt)`,
    with a static `of(TimeEntry, String authorName)` factory (mirroring `UserSummary.of(User)`).
  - `EntryController` (`POST /api/entries`): reads `@AuthenticationPrincipal AuthenticatedUser`,
    delegates to `EntryService.create(userId, request)`, returns `201` + `EntryResponse`.
  - `EntryService`: validates the request inline (throws `ValidationException` for
    `durationMin <= 0`, blank/missing `description`, missing `entryDate`), fetches the
    author's name via `UserRepository.findById(userId).orElseThrow()`, stamps
    `createdAt == updatedAt` from one `OffsetDateTime.now()`, saves, maps to `EntryResponse`.
- Integration test (`@SpringBootTest` + `MockMvc` + Testcontainers, matching
  `LoginEndpointTest`'s pattern): authenticated create → `201`, `userId` is the token's
  user even if the body sends a different one (AC-1); `durationMin <= 0` / missing
  `entryDate` / missing or blank `description` → `400 VALIDATION_FAILED` with per-field
  `details` (AC-2); no token → `401` (AC-3); error envelope shape matches
  [05](../design/05-api-conventions.md).
- Unit test (`EntryServiceTest`, per 06b's thin-unit-layer dial): the inline validation
  logic — `durationMin <= 0` rejected, blank/whitespace `description` rejected, missing
  `entryDate` rejected, valid input passes through.

## Explicitly out of scope (do not build)

Update/delete/list endpoints, projects, pagination, any range constraint on `entryDate`, a
separate validator class, a JPA relationship between `TimeEntry` and `User`, a repository-
level test for `TimeEntryRepository` (no custom queries exist yet to verify).
