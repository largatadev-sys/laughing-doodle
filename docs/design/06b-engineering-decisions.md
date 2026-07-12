# 06b — Engineering Decisions  `[PER-SYSTEM — decided fresh]`

The choices the reused principles ([06a-engineering-principles.md](06a-engineering-principles.md),
P1–P10) leave open, decided as hard requirements for this MVP. **No decision passes by
omission** — test depth especially. The reused core is ratified **as-is** (no amendments)
for this system.

---

**Stack.**
- Backend: **Java 21 + Spring Boot 3.x**, Gradle. Spring Web, Spring Security, Spring Data
  JPA, Flyway (migrations), PostgreSQL driver.
- DB: **PostgreSQL** (containerised locally; managed in prod).
- Client: **Expo (React Native)**, TypeScript, targeting web (real tool) + native (practice).

**Dial (from mode).** Product-internal + Learning → **Floor everywhere, except the two
Learning subsystems which go one notch up:** auth (Spring Security/JWT) and the
authorization enforcement path. Everything else stays at Floor — this is a side tool.

**Error taxonomy & codes** (feeds the [05](05-api-conventions.md) envelope):
- `VALIDATION_FAILED` → 400 (bad/missing fields; `details` carries per-field messages)
- `UNAUTHENTICATED` → 401 (missing/invalid/expired token)
- `FORBIDDEN` → 403 (authenticated but not the owner — INV-2 violation)
- `NOT_FOUND` → 404
- `CONFLICT` → 409 (e.g. duplicate email on user create)
- `INTERNAL` → 500 (unexpected; message generic, real cause logged once server-side)

**Exception model.** A small typed hierarchy (`AppException` → `ValidationException`,
`ForbiddenException`, `NotFoundException`, `ConflictException`), each mapped to a code +
status by the single `@RestControllerAdvice`. No log-and-throw (P2).

**Tenancy.** Single-tenant → [03](03-tenancy-model.md). No `team_id` anywhere.

**Auth model.** Per [04](04-architecture.md) ADR-002: BCrypt password hashing; JWT
(HS256, secret from env), **TTL ~7 days**; Spring Security filter validates the bearer
token and populates the security context; identity read from context, never from the body.
No self-service reset, no lockout.

**Config.** Env vars: `DATABASE_URL`, `JWT_SECRET`, `JWT_TTL_DAYS`. `.env` gitignored;
`.env.example` committed with placeholders.

---

## Test depth  *(a deliberate choice — not inherited; dials P8 test-ownership)*

The playbook singles this out because thin testing must be *chosen*, never defaulted.
This sets the **dial for P8 (test ownership: one concern, one layer)** — each concern
tested once, at the layer that owns it, never duplicated across layers. Choice for this build:

| Layer | Depth | What it covers | Why this depth |
|---|---|---|---|
| **Integration (API)** | **Primary — the layer that earns its keep** | `@SpringBootTest` + `MockMvc` against a test Postgres (Testcontainers or a disposable DB) for each endpoint. **Mandatory coverage of the authorization/ownership paths (INV-2): a user cannot edit or delete another user's entry → expect 403; identity is taken from the token, not the body.** Plus happy-path CRUD + the error envelope shape. | This is the one real security surface (IDOR) and it's a Learning subsystem. If one thing is tested, it's this. |
| **Unit** | **Thin — only where real logic exists** | JWT issue/verify/expiry; duration validation (INV-3); BCrypt verify wiring. | Little pure domain logic exists (it's mostly CRUD); don't manufacture unit tests for anemic code. |
| **End-to-end / UI** | **None for MVP** | — | The Expo UI is the fastest-churning two-way door; automated e2e now would test a moving target for little value. Revisit post-validation if the UI stabilises. |

**Explicit statement so it can't be misread as an accident:** testing is deliberately
concentrated at the **API integration layer, focused on the authorization invariants**,
thin on units, and absent at the UI. This is a choice made because the security boundary
is the thing most costly to get wrong and the UI is the thing most likely to change.
