# 04 — Architecture & ADRs  `[PRODUCTION DEPTH for the bones; ADRs accrete]`

The map that keeps agent sessions converging. Every significant decision below is an ADR
that names **the assumption that makes it right** and **what would invalidate it**.

---

## Architecture overview

```
┌─────────────────────────────┐
│   Expo app (React Native)   │
│  web (the real tool)        │   ONE codebase → web + native (React Native Web)
│  native (practice, local)   │
└──────────────┬──────────────┘
               │  HTTPS · REST/JSON · JWT bearer
┌──────────────▼──────────────┐
│         Spring Boot         │   layered:
│  controller → service →     │   - controller: HTTP, validation, maps to/from DTOs
│  repository → persistence   │   - service:    business rules, INV-2 ownership check
│                             │   - repository: Spring Data JPA, persistence only
└──────────────┬──────────────┘
               │
┌──────────────▼──────────────┐
│         PostgreSQL          │
└─────────────────────────────┘
```

Module boundaries: `auth` (login, JWT, security filter) · `users` · `entries`. Each is a
controller+service+repository vertical; they share only the `User` identity.

## Cross-cutting

- **Auth.** Stateless JWT. A Spring Security filter validates the `Authorization: Bearer`
  token on every request except `POST /auth/login`, and puts the authenticated user id +
  role into the security context. Downstream code reads identity from there — **never**
  from the request body. (Details: ADR-002, [06b](06b-engineering-decisions.md).)
- **Errors & logging.** One global `@RestControllerAdvice` maps exceptions to the error
  envelope ([05](05-api-conventions.md)). **Never log-and-throw** (log once, at the
  boundary that handles). **Never log secrets/PII** (no passwords, no tokens in logs).
- **Config.** Environment variables only. `.env` is gitignored; the repo carries
  `.env.example` with placeholders. DB URL, JWT secret, JWT TTL are all env-provided.
- **Integrations.** None in v1.

## Deployment & environments

Mode dial: this is an internal/learning build, so the three-environment pipeline is
**collapsed to dev → prod** (release discipline a pre-validation internal tool doesn't need).

- **Dev** — local machine. Spring app run natively (IDE/Gradle); Postgres in a container.
- **Prod** — **one** container image (the Spring app, which also serves the Expo **web**
  export from `static/`) deployed to a PaaS (Railway); managed Postgres; web + API on a
  **single origin** over HTTPS. *(Supersedes the original split topology — separate static
  host for the web — per ADR-008. The Story-9 acceptance criteria are unchanged.)*
- **Promotion path** — single `main` + short-lived `feature/<story>` branches (see
  CLAUDE.md). The walking skeleton (story 1 + auth + one entry path) deploys to prod early.

## Containerization scope  *(stated explicitly — never inferred)*

**Two-mode, since Story 9 (ADR-008 refines ADR-006):**

- **Daily loop: datastore-only.** Postgres runs in a container; the Spring app runs
  natively (`bootRun`) and Expo runs on the host (web dev server + Expo Go). This is the
  fast edit-run loop ADR-006 protects — unchanged for day-to-day work.
- **Parity gate: full-stack.** `docker compose --profile fullstack up --build` builds and
  runs the **bundled prod image** (Spring serving the Expo web export + the API) next to
  Postgres. It is the *same image* the PaaS runs, so it proves the deploy before it ships.
  Run it before deploying, not on every keystroke.
- **Why now (vs. ADR-006's datastore-only-only):** Story 9 makes "deploy-time surprises"
  imminent — the exact risk ADR-006 named as its invalidator. Rather than absorb them in
  prod, we front-load them into an on-demand local gate. The learning objective is still
  Expo + Spring Security, not a Dockerised dev loop, so daily dev stays native.
- **Prod:** the **same** bundled image + managed DB (single origin). Parity *is* now a
  goal, delivered by the gate running the identical artifact.

---

## ADR log

**ADR-001 — One Expo codebase (React Native Web) for web + native.**
- *Context.* Need a web app (the real tool) and want a native app (practice). Solo dev.
- *Decision.* Single Expo/RN codebase; export web (the tool) and run native locally (practice).
- *Alternatives rejected.* Separate React web + separate RN app — doubles frontends for a side tool.
- *Assumption.* A mobile-shaped UI in the browser is acceptable for internal time-logging.
- *Invalidates it.* Web needs genuinely desktop-specific UX, or a critical native lib has
  no web equivalent → split the web client out.

**ADR-002 — Roll-your-own auth: Spring Security + BCrypt + JWT.**
- *Context.* 4 trusted internal users; developer wants to learn Spring Security; client is
  web + native from one codebase.
- *Decision.* Username/password login → BCrypt-verified → signed JWT (~7-day TTL) in the
  `Authorization` header. **No self-service reset** (admin resets in DB), **no lockout**.
- *Alternatives rejected.* OAuth/identity provider — less code in the abstract, but its
  browser-redirect flow differs per Expo platform (web vs native), and adds an external
  dependency; username/password is one uniform HTTP call across both targets.
- *Assumption.* Few trusted users; instant token revocation isn't needed; long-lived
  token is acceptable.
- *Invalidates it.* User base grows / external users / a need to revoke a token before it
  expires → add refresh tokens + a revocation list, or move to an IdP.

**ADR-003 — REST/JSON API.**
- *Context.* Simple CRUD over two entities.
- *Decision.* Plain REST/JSON over HTTPS.
- *Alternatives rejected.* GraphQL, gRPC — solve problems (complex nested reads, typed
  streaming) this system doesn't have.
- *Assumption.* Read/write shapes stay simple.
- *Invalidates it.* Clients need many divergent nested read shapes → reconsider GraphQL.

**ADR-004 — Single-tenant.** → see [03-tenancy-model.md](03-tenancy-model.md).

**ADR-005 — PostgreSQL.**
- *Context.* Relational data, multi-user concurrent writes, small volume.
- *Decision.* Postgres.
- *Alternatives rejected.* SQLite (weaker for concurrent multi-user + prod hosting);
  a document store (data is relational).
- *Assumption / invalidator.* n/a at this scale.

**ADR-006 — Local containerization = datastore-only.** → rationale under
"Containerization scope" above. *Invalidates it:* deploy-time "works locally, breaks in
prod" bugs recur → move to full-stack local containerization for behavioural parity.
*Refined by ADR-008 (Story 9):* the invalidator effectively fired at deploy time, so
local containerization is now **two-mode** — datastore-only for the daily loop **plus** an
on-demand full-stack parity gate. The fast native daily loop this ADR protects is retained.

**ADR-007 — Projects deferred, added later as a nullable field.**
- *Context.* At 4 users the shared feed is scannable without grouping by project.
- *Decision.* Ship free-text `description` only. Add `Project` + nullable
  `time_entries.project_id` later; old rows stay null.
- *Assumption.* The team stays small enough that an unstructured feed is readable.
- *Invalidates it.* Team/volume grows and "what is everyone working on?" stops being
  answerable by eye → introduce Projects (additive; never scavenge structure from `description`).

**ADR-008 — Full-stack single-origin containerization; the app image serves the web.**
- *Context.* Story 9 (deploy). Developer wants dev/prod parity and one deployable. ADR-006
  had chosen datastore-only local containerization; the deployment section had assumed the
  Expo web export ships to a *separate* static host (→ CORS + a build-time-baked API URL).
- *Decision.* A multi-stage `Dockerfile` bundles the Expo **web** export
  (`expo export`, `web.output: "single"`) into the Spring Boot image's `static/`. **One**
  container serves the web app at `/` and the REST API at `/api/*` — single origin.
  Deploy that one image to a PaaS (Railway) + managed Postgres. Local
  `docker compose --profile fullstack up` runs the *same image* as a pre-deploy parity gate;
  daily dev stays native (ADR-006's fast loop preserved). `web.output` moves `static`→`single`
  (SPA) so one `/*→index.html` fallback serves every route, including dynamic `[id]` ones.
- *Consequences.* No CORS and no baked API URL in prod (client calls a **relative** `/api`).
  One deploy, one origin, PaaS-provided TLS. Frontend+backend deploys are **coupled**
  (fine at solo/4-user scale). `SecurityConfig` makes static assets public while keeping
  `/api/**` authenticated — **INV-2 unchanged**. The Story-9 acceptance criteria are unchanged.
- *Assumption that makes it right.* Solo dev, ~4–10 internal authed users: no need for an
  independent frontend deploy cadence, preview deploys, a CDN/edge, SSR/SEO, or per-tier
  scaling. Parity + one deployable beat deploy decoupling.
- *Invalidates it.* Frontend needs its own deploy cadence / preview deploys; web needs a
  CDN/edge for scale or geography; or SSR/SEO is wanted → split the web back onto a static
  host (toward Story-9-as-originally-written), reintroducing CORS + a baked API URL.
- *Amends.* ADR-006 (local containerization → two-mode) and the deployment section's
  "web on a separate static host."

**Deferred (until validated).** Caching, read replicas, async/queues, rate limiting,
real observability — explicitly **not** decided now; revisit signal-driven post-validation.
