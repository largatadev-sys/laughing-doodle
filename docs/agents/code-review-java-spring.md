# Code review: the Java / Spring axis

A third axis for `/code-review`, beside **Standards** and **Spec**. It checks the backend part of
a diff against general Java / Spring practice. It was adopted 2026-10-07 from a generic
checklist, then fitted to this repo: items that contradict a documented decision were changed or
dropped, and the reason is given next to them.

**Precedence.** This repo's documents always win: CLAUDE.md, [05](../design/05-api-conventions.md),
[06a](../design/06a-engineering-principles.md), [06b](../design/06b-engineering-decisions.md), and
the ADRs in [04](../design/04-architecture.md). Where they allow something this checklist would
flag, don't flag it. This axis is about general practice; repo rules belong to the Standards axis,
so don't repeat its findings here.

**Scope.** Backend files only (`backend/`, including migrations). A diff with no backend changes
skips this axis and says so.

## How to report

Start every finding with a prefix:

| Prefix      | Meaning                                                           |
| ----------- | ----------------------------------------------------------------- |
| `blocker:`  | Must fix before merge: correctness, security, data loss           |
| `should:`   | Fix before merge unless there's a stated reason not to            |
| `nit:`      | Optional: style or preference                                     |
| `question:` | Need to understand it before deciding                             |

Cite the file and line, and the checklist section. Keep the report under 400 words. Lead with
blockers. Say what's good as well, briefly.

## 0. Automated gates

What this repo has: `cd backend && ./gradlew test` (Testcontainers integration tests and the
ArchUnit boundary tests), client typecheck and lint, and the secrets scan before each commit
(CLAUDE.md). Confirm these are green.

Not set up here: formatter, static analysis, coverage, dependency scanning, gitleaks. **Don't
flag their absence on each diff.** If you think one is worth adding, raise it as one `question:`.

## 1. Scope & intent

- The change does what its ticket says. No unrelated refactors, reformatting or drive-by changes.
- Migrations, config changes and rollout steps are called out. Any schema change needs the
  developer's sign-off (CLAUDE.md stop rules): `blocker:` if there's no record of it.
- *Dropped: the ~400-line size rule.* A story lands as one squash commit here, so story diffs
  are larger by design.

## 2. Correctness

- Edge cases: null or empty input, boundaries, duplicates, concurrent callers. Times are stored
  and compared in UTC.
- Failure paths: exceptions, partial failures. No swallowed exceptions.
- `==` on strings or boxed types, off-by-one, overflow.

## 3. Layering

- Controller (HTTP and validation) → service (logic and INV-2) → repository. No business logic
  in controllers; controllers don't call repositories.
- Entities never cross the API boundary: request and response DTOs (records) only.
- Constructor injection with `final` fields. Beans are stateless. No circular service
  dependencies (`@Lazy` is a smell).
- New abstractions need a real second use (YAGNI, P-principles in 06a).

## 4. Module boundaries (fitted to this repo)

This is **not** a Spring Modulith codebase. Boundaries are package-by-feature-then-role plus one
ArchUnit test per split module (CLAUDE.md; `dashboard/` is the reference).

- A new feature module follows the `dashboard/` layout and has an ArchUnit test pinning who may
  touch its `repository/` and `domain/`.
- Another module reaches it only through what its ArchUnit test allows, normally a public
  service. Don't loosen an ArchUnit rule to make a change fit: `blocker:` unless the change
  explains why.
- *Dropped:* domain events and eventual consistency between modules, per-module migration
  folders, "no cross-module foreign keys", `@ApplicationModuleTest`, per-module READMEs, a
  shared kernel, a bootstrap module. One app, one Postgres schema, one Flyway sequence
  (`V<n>__…`). Foreign keys across feature tables are fine.

## 5. Transactions & persistence

The repo uses both JPA and JDBC repositories.

- `@Transactional` on the service layer; `readOnly = true` for reads. No self-invocation of
  `@Transactional` methods.
- No N+1: no query inside a loop over rows.
- No `FetchType.EAGER` on new associations. `equals`/`hashCode` on entities use the id only.
- Indexes exist for new query predicates and foreign keys used in lookups.
- A migration must not break the version that's currently deployed. Migrations are append-only:
  never edit an applied `V<n>` file.
- **Unbounded queries:** these are `question:` at most. The reports read deliberately loads
  everything in one fetch, and some specs (such as Story 28's Handoffs list) choose no
  pagination. Flag only where the spec didn't decide.
- `spring.jpa.open-in-view` is not set (Spring's default is on). Mention it at most once as a
  `question:`, not on every diff.

## 6. API design

- Status codes and the error envelope follow [05](../design/05-api-conventions.md), **not** RFC
  9457 `ProblemDetail`.
- Request DTOs are validated. Malformed input gets 400, never 500.
- Existing fields are not removed or renamed (the native app may be on an older version). The
  Largata intake contract is frozen unless the spec says otherwise.
- *Dropped:* OpenAPI docs (the repo has none).

## 7. Security

- INV-2 is the one security surface: author-only writes, no IDOR. Identity comes from the JWT,
  never from the request body. Any weakening is a `blocker:`.
- Authorization paths have API integration tests (mandatory, 06b).
- SQL and JPQL parameters are bound, never concatenated. Input is size-bounded.
- No secrets, PII or stack traces in code, logs or error responses (P2/P3).
- No `permitAll` or `*` CORS added for convenience. New dependencies come from reputable sources.

## 8. Concurrency & resilience

- Shared mutable state is thread-safe or removed.
- Outbound calls have timeouts. Resources are closed with try-with-resources.

## 9. Java idioms

- Names say what things are. Methods are short; nesting is three levels deep at most.
- `Optional` only as a return type. Immutable by default: records, `final`, unmodifiable
  collections.
- No raw types or unjustified unchecked casts. Streams stay readable.
- Java 25 features (records, sealed types, switch expressions, text blocks) are welcome where
  they help.
- Magic numbers and strings get named constants. Comments say *why*. No commented-out code, and
  no `TODO` without a ticket.
- *Dropped:* Lombok rules (the repo doesn't use Lombok).

## 10. Configuration

- Environment differences come from env vars or profiles, never `if (prod)`. Required
  properties fail fast at startup.
- Versions come from the Spring Boot BOM; any pinned override has a reason.
- A new starter-shaped dependency needs a live `bootRun` check (CLAUDE.md gotcha: missing Flyway
  autoconfig fails silently).

## 11. Logging

- SLF4J with parameterized messages. No `System.out`.
- Levels make sense. Never log and then throw; log an exception once, where it's handled (P2).
- *Dropped:* metrics, tracing and correlation ids (not in use).

## 12. Tests

- How deep tests go is set by [06b](../design/06b-engineering-decisions.md). The house style is
  HTTP-seam integration tests with Testcontainers Postgres. Don't ask for slice tests or mocks
  instead.
- Tests cover the changed behaviour, including failure paths. They assert behaviour, not
  implementation.
- No `Thread.sleep`, wall-clock dependence or order dependence.
- Existing tests are not deleted or weakened to make the build pass.
- *Not a finding:* missing client tests. The client has no test runner by decision (06b,
  2026-10-05).

## 13. Performance

- No database or HTTP calls inside loops. No obvious O(n²) on data that grows.

## 14. Docs & operability

- Decisions that are hard to reverse get an ADR in [04](../design/04-architecture.md).
- BUILD_STATUS is updated (CLAUDE.md, non-negotiable).
- New env vars are in `.env.example`.
