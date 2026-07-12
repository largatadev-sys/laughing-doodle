# Story 2 — Auth: login + JWT filter + seeded users

**Status.** Planned, not yet built. Immutable once implementation lands — see
[BUILD_STATUS.md](../../BUILD_STATUS.md) for live status.

**Source.** [07-epic-map.md](../design/07-epic-map.md) Story 2, elaborated through a
`/grilling` session on 2026-07-13. This file records *why* each choice was made; the
epic map records *what* is required.

---

## Scope

`POST /api/auth/login` verifies username + BCrypt password → returns a signed JWT + user;
a Spring Security filter validates the bearer token on all other routes and populates the
security context. Seed 4 users (one admin) via migration.

## Decisions made this session (and why)

1. **JPA (`spring-boot-starter-data-jpa`), not plain JdbcTemplate, for `User`.** Story 1
   deliberately left the JPA-vs-JDBC choice open ("Stories 2/3 are better positioned to
   make against real requirements"). The architecture doc's stated intent
   ("repository: Spring Data JPA, persistence only") is honored now rather than deferred
   again. This adds a second new framework surface to a story that's already introducing
   Spring Security — accepted as a deliberate developer call, not the lower-incidental-
   complexity path.

2. **Username lowercased in `AuthService` before querying; `UserRepository.findByUsername`
   is an exact match, not `findByUsernameIgnoreCase`.** One canonical normalization point,
   trusting Story 1's stored-lowercase invariant (decision #4 of the Story 1 plan; INV-5)
   rather than duplicating the normalization at the SQL layer too.

3. **jjwt (`io.jsonwebtoken:jjwt-api`/`-impl`/`-jackson`) for JWT issue/verify**, not
   Nimbus JOSE+JWT. ADR-002 specifies a single shared HS256 secret with no OAuth2/JWK/
   external-IdP involvement, so Nimbus's extra JOSE surface buys nothing. jjwt's small,
   explicit builder API is the better fit for the Learning sub-objective in
   [01](../design/01-intent-and-constraints.md) — learning JWT mechanics directly rather
   than through a heavier abstraction.

4. **`GET /api/health` stays permit-all.** It's a deployment/liveness probe, not a
   user-facing resource. Locking it behind a JWT would break the exact "is the app up"
   check Story 1 built it for, and no Story 2 AC calls for changing it — doing so would be
   scope creep past this story's boundary. `/error` is also permit-all (standard Spring
   Boot practice — otherwise Spring Security intercepts framework error dispatches before
   the `@RestControllerAdvice` gets a clean shot at them).

5. **Seed users via Flyway `V2__seed_users.sql`**, not an application-level
   `CommandLineRunner` seeder. Consistent with Story 1's Flyway-only pattern; no new
   seeding machinery/bean introduced. **Passwords are a single obvious placeholder**
   (pre-computed BCrypt hash), never anything resembling a real credential — committing a
   hash of an actual password 4 people will use would violate CLAUDE.md's "never commit
   secrets" backstop even though BCrypt hashes are expensive to reverse. Real passwords
   are set later via direct DB update — the same admin-reset mechanism ADR-002 already
   establishes, so this isn't new machinery, just its first use.

6. **Seed identities use placeholder names/usernames** (not real teammates' names),
   swapped in manually by the developer before this ever touches a shared/prod database —
   keeps real people's names out of a file an AI session is committing.

7. **Login failure message text is identical for both "unknown username" and "wrong
   password"** — not just the same status/code (AC-2 already collapses those to one
   `401`), but the same `message` string too ("Invalid username or password"). A
   differing message would reopen the username-enumeration vector AC-2's shared status
   code was meant to close. Floor-level security practice, not a judgment call.

8. **JWT claims: `sub` (user id) + `role` only** (plus standard `iat`/`exp`) — no
   `username` claim. Nothing downstream of login needs username: INV-2's ownership check
   is by `user_id`; the login response's `user.username` comes from the already-fetched
   `User` row, not the token. Keeps the token minimal and avoids a stale username sitting
   in an already-issued token if it's ever changed within the 7-day TTL.

9. **Security-context principal is a custom `AuthenticatedUser` record**
   (`record AuthenticatedUser(Long userId, Role role)`), not Spring Security's
   `UserDetails`. Reconsidered mid-session: `UserDetails` requires implementing
   `getUsername()` (nothing to back it, per decision #8) and four lifecycle flags
   (`isAccountNonExpired`, `isAccountNonLocked`, `isCredentialsNonExpired`, `isEnabled`)
   that would all be hardcoded `true` forever — no lockout (ADR-002), no expiry/disable
   concept (02-domain-model.md: "User — no lifecycle in v1, always active"). Login also
   bypasses Spring's `AuthenticationManager`/`UserDetailsService` provider chain entirely
   (BCrypt check happens directly in `AuthService`) — only the filter-chain/
   `SecurityContext` half of Spring Security is in play, so adopting `UserDetails` would
   mean satisfying an interface shaped for a flow this build doesn't use (P9 restraint).

10. **AC-3 ("protected route with no/invalid/expired token → 401") is tested against a
    dedicated test-only protected endpoint**, since no real protected endpoint exists yet
    (`/api/health` is permit-all per decision #4; Stories 3+ haven't landed). Without
    *some* protected route, AC-3 as written has nothing to point at.

11. **That test endpoint lives entirely under `src/test`**
    (`com.largatadev.timesheet.support.TestProtectedController` or similar), not
    `src/main` behind a runtime guard. Gradle's source-set separation makes it
    structurally impossible to ship in `bootJar` — no discipline required later, no
    cleanup step for Story 9 to remember.

12. **Package split: `users/` + `auth/`**, matching 04-architecture.md's stated module
    boundaries. `users/` holds `User` (`@Entity`), `Role` (enum), `UserRepository`,
    `UserSummary` (DTO). `auth/` holds `AuthController`, `AuthService`, `JwtService`,
    `JwtAuthenticationFilter`, `SecurityConfig`, `AuthenticatedUser`. `User` is shared
    identity consumed by `auth` (and later `entries`), not owned by `auth`.

13. **Full exception hierarchy + `@RestControllerAdvice` built now**, covering all 6
    codes from [06b](../design/06b-engineering-decisions.md)
    (`VALIDATION_FAILED`/400, `UNAUTHENTICATED`/401, `FORBIDDEN`/403, `NOT_FOUND`/404,
    `CONFLICT`/409, `INTERNAL`/500) even though Story 2's own ACs only exercise
    `UNAUTHENTICATED`. Story 2 is the first story that needs the error envelope on the
    wire at all (Story 1 had nothing to translate). Building the full taxonomy once now
    means Stories 3–6 add exception subtypes as needed rather than rebuilding the
    mechanism — satisfies P2's "exactly one translation boundary" in one pass instead of
    incrementally patching it across stories.

14. **Exception scaffolding lives in `com.largatadev.timesheet.error`** — a small,
    narrowly-named package for `AppException` + subtypes + `GlobalExceptionHandler` + the
    error-envelope record. Not `common`/`shared`, which tend to become unscoped dumping
    grounds over time (P9 restraint).

15. **`UserSummary(Long id, String name, String username, Role role)` defined now, in
    `users/`, reused by Story 4.** Justified per P9 ("second caller already exists," not
    speculative) — Story 4's own AC in [07-epic-map.md](../design/07-epic-map.md)
    explicitly names "each with author name" as a requirement, so the shape isn't
    hypothetical future scope, it's already-known reuse. Excludes `password_hash`,
    satisfying AC-4 (hashes never returned in any response).

16. **No field-level validation (`@Valid`/`@NotBlank`) on the login request DTO.** A
    blank/missing username or password falls through to the same generic `401` as any
    other bad credential, per decision #7's reasoning: a `400`-vs-`401` split here would
    leak "field was empty" vs "credential was wrong," a distinction with no legitimate
    client use-case. Matches the epic map's AC list exactly — only a `401` case is
    specified for this endpoint; `VALIDATION_FAILED` is first genuinely exercised in
    Story 3.

17. **JWT config (`jwt.secret`, `jwt.ttl-days`) via plain `@Value` injection** in
    `JwtService`, not a `@ConfigurationProperties` record. Two properties, one consumer —
    the typed-properties pattern is structure P9 says to earn through actual pain
    (multiple properties, multiple consumers, validation needs), not adopt preemptively.

18. **`Role` enum lives in `users/`**, mapped via `@Enumerated(EnumType.STRING)` on
    `User` (per Story 1 plan decision #5, which already anticipated this). Consumed by
    `auth` (JWT claim, `AuthenticatedUser`) but owned by `users` — same "auth and entries
    share only the User identity" principle from 04-architecture.md.

## Deliverables

- `build.gradle` — add `spring-boot-starter-data-jpa`, `io.jsonwebtoken:jjwt-api`,
  `io.jsonwebtoken:jjwt-impl` (runtime), `io.jsonwebtoken:jjwt-jackson` (runtime).
- `src/main/resources/db/migration/V2__seed_users.sql` — 4 users (1 admin, 3 members),
  placeholder names/usernames, one shared placeholder-password BCrypt hash.
- `com.largatadev.timesheet.users` — `User` (`@Entity`), `Role` (enum), `UserRepository`
  (Spring Data JPA), `UserSummary` (record DTO).
- `com.largatadev.timesheet.auth` — `AuthController` (`POST /api/auth/login`),
  `AuthService` (credential verification, lowercasing, orchestrates JWT issue),
  `JwtService` (issue/verify/expiry via jjwt, `@Value`-injected secret/TTL),
  `JwtAuthenticationFilter` (bearer-token validation, populates `SecurityContext`),
  `SecurityConfig` (`SecurityFilterChain` bean — permit-all on `/api/auth/login`,
  `/api/health`, `/error`; authenticated on everything else), `AuthenticatedUser`
  (custom principal record), `LoginRequest`/`LoginResponse` DTOs.
- `com.largatadev.timesheet.error` — `AppException` hierarchy (`ValidationException`,
  `UnauthenticatedException`, `ForbiddenException`, `NotFoundException`,
  `ConflictException`), `GlobalExceptionHandler` (`@RestControllerAdvice`), error-envelope
  record matching [05](../design/05-api-conventions.md).
- `src/test/java/.../support/` — test-only protected controller used solely to exercise
  the JWT filter chain for AC-3; never compiled into `bootJar`.
- Integration tests (`@SpringBootTest` + Testcontainers, per Story 1's established
  pattern): valid login → 200 + token + user; bad password → 401 generic message;
  unknown username → 401 identical generic message; no/invalid/expired token against the
  test-only protected route → 401; password hash never appears in any response body or
  log output.
- Unit tests (per [06b](../design/06b-engineering-decisions.md)'s thin-unit-layer dial):
  `JwtService` issue/verify/expiry; BCrypt verify wiring in `AuthService`.

## Explicitly out of scope (do not build)

Entry endpoints, self-service password reset, account lockout, `UserDetails`/
`UserDetailsService`/`AuthenticationManager` provider-chain machinery, `@ConfigurationProperties`
for JWT config, request-body validation on the login endpoint, real (non-placeholder)
user credentials committed to any file.
