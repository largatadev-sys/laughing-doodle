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

**ADR-009 — Monorepo layout: `backend/` + `client/` peers; root is orchestration.**
- *Context.* The Expo client was scaffolded into `client/` (Story 7a), but the Java/Gradle
  backend still sat scattered at the repo root (`src/`, `build.gradle`, `gradlew…`) alongside
  the docs and compose files — asymmetric and harder to navigate as the codebase grows. The
  cleanup was deferred 2026-07-13 (cosmetic; cost lands on the immutable doc record), then done.
- *Decision.* Nest the whole Gradle project under `backend/`, so the root holds two symmetric
  app peers — `backend/`, `client/` — plus an **orchestration layer** that runs them together:
  `docker-compose.yml`, the single-origin `Dockerfile` (its `COPY`s gain a `backend/` prefix;
  build context stays the root because the image needs *both* peers), `.env`/`.env.example`,
  `scripts/`, `docs/`. Each peer owns its own `.gitignore`.
- *Rejected alternative.* A **root Gradle multi-project** (`settings.gradle` at root doing
  `include 'backend'`) — rejected as ceremony for a single JVM module, and it would re-plant a
  Gradle root at the top, contradicting "root = orchestration only." Revisit only if a second
  JVM module appears.
- *Consequences.* Run Gradle from `backend/` (`cd backend && ./gradlew …`). `.env` and compose
  stay at root, unchanged — the backend never read `.env` from disk (it resolves env vars with
  baked-in defaults that match compose's), so nesting the project doesn't disturb that
  relationship. No behaviour change anywhere; it's discoverability only.
- *Immutable-record handling.* Live docs (`CLAUDE.md`, `BUILD_STATUS.md`, `docs/deploy/railway.md`)
  were updated to the new paths; the point-in-time `docs/plans/` and `docs/tickets/` records were
  **left frozen** with their old-root paths, and a single `BUILD_STATUS.md` ledger line dates the
  move so a reader of an old plan knows when its paths stopped being current.
- *Invalidates it.* A second backend service/JVM module, or a genuine need to build both peers
  from one root command, → reconsider the root multi-project (or a proper monorepo tool).

**ADR-010 — Reports intake is relay-only (shared-secret, server-to-server).**
- *Context.* The Reports inbox (spec: `docs/tickets/reports-inbox/`) receives feedback
  from users of the sibling product **Largata** — a different app, different domain, and a
  different auth system (Firebase; identities worklog knows nothing about). Worklog is
  otherwise a private 4-user app whose every endpoint (bar login) sits behind its own JWT.
- *Decision.* Reports enter worklog **only** via Largata's backend: phone → Largata
  (Firebase-authenticated, store-and-forward with retry) → server-to-server
  `POST /api/intake/reports`, authenticated by a shared secret (`REPORTS_INTAKE_SECRET`,
  env var on both sides, constant-time compare, rotate by redeploy). Intake is idempotent
  on a client-minted report UUID so retries are safe. Reporters stay **foreign**: their
  identity travels as data on the Report; they never become worklog users.
- *Alternatives rejected.* (1) The Largata mobile app posting **directly** to a worklog
  endpoint — either the endpoint is open to the public internet (spam/flood into a private
  team app) or worklog must validate Firebase tokens (permanently couples worklog to
  Largata's auth project and SDK). (2) Linking to screenshots hosted in Largata's storage —
  impossible: Largata's media reads are authenticated-proxy-only (its ADR-021), so worklog
  receives and **owns** the screenshot bytes instead.
- *Assumption.* One trusted caller at trivial volume — a static secret is proportionate;
  HMAC request-signing, mTLS, or an API-gateway product would be ceremony.
- *Invalidates it.* A second reporting source, reporter-facing status ("what happened to
  my report?"), or real abuse pressure → revisit toward per-source credentials or a
  queue between the two systems.

**ADR-011 — Intake contract v1.1: optional reporter identity; screen as opaque data.**
- *Context.* 2026-08-28. Largata is making its report entry point globally visible —
  every screen, signed-out ones included — and wants each Report to carry the screen the
  user was on. Both were contract changes to ADR-010's surface, made while the Largata
  relay was still unbuilt (non-breaking). Spec: `docs/tickets/reports-inbox/spec.md`,
  "Amendments → v1.1".
- *Decision.* (1) `reporter` becomes optional, per-field, stored as sent — signed-out
  screens have no identity, and they are where "I can't get in" bugs live; the inbox
  renders the absence honestly ("Signed out"). (2) `context.screen` joins as an optional
  opaque string (≤200 chars), defined as *where the reporter was when they opened the
  report flow*; worklog never validates it against Largata's route table. Guiding rule:
  under store-and-forward, every `400` is a silently lost report, so intake only requires
  what worklog cannot represent the absence of.
- *Alternatives rejected.* Largata synthesizing an anonymous identity (device id +
  placeholder name) keeps the contract frozen but plants fake reporters in permanent,
  undeletable data — indistinguishable from real ones the day anyone counts distinct
  reporters. Requiring `screen`, or validating its shape, couples worklog to Largata's
  route table: a rename there becomes a lost report or a forced deploy here.
- *Assumption.* Anonymous reports stay rare and benign — the relay secret still gates the
  door, so "anonymous" means a signed-out Largata user, not the public internet.
- *Invalidates it.* Anonymous-report abuse (no uid to correlate) → revisit toward
  requiring identity on signed-in screens plus rate limiting at the relay.

**ADR-012 — Team notes on Reports: an append-only, editable log (reverses "no comments").**
- *Context.* 2026-08-29. The Epic 3 spec deliberately excluded comments ("no assignment,
  no comments, no deletion"), betting that the who-last-changed status stamp was enough
  attribution. Real triage use disproved half the bet: `discuss` parks a question on a
  visible agenda, but the *answer* — why a report was dismissed, what the founders
  decided — evaporated into chat. Status records where a Report stands; nothing recorded
  why. Spec: `docs/tickets/reports-inbox/spec.md`, "Amendments → Team notes".
- *Decision.* A **Note** entity: team-authored prose on a Report, team-facing only — the
  intake contract (ADR-010/011) is untouched. The **log is append-only** — no deletion,
  ever, matching "Reports are kept forever" — while each Note's **text is editable by its
  author** (see *Revised* below), always stamped with a visible edited-at (rewrites are
  visible, never silent). Author/editor identity comes from the JWT, never the request
  body. Notes float free of status changes.
- *Revised 2026-08-29, same day, before merge: editing is **author-only**.* The decision
  above originally read "editable by **any** Member", with the visible stamp as the
  safeguard. The developer reversed it on first sight of the built screens: seeing the
  ledger render as attributed, per-person entries — rather than the single shared notes
  field they had pictured — made a Note read as **signed testimony**, and nobody should
  put words under someone else's name. A non-author's edit is now `403`, the same
  ownership answer INV-2 gives on a time entry, so the app has one ownership rule rather
  than two. This is exactly the revisit the *Invalidates it* clause below anticipated —
  though the trigger was the design becoming legible, not the team outgrowing trust.
  Costs: a typo in a teammate's note now needs them to fix it, or a follow-up Note.
  `edited_by` is kept in the schema and the wire response even though it now always equals
  the author — a stamp is worth more when it records who actually acted than when it
  assumes; the UI simply renders "Edited · when" rather than repeating the name.
- *Alternatives rejected.* (1) A single mutable notes field per report — last-writer-wins
  silently destroys the previous decision and its attribution, defeating the feature's
  entire point. (2) Full comments — threads, replies, reporter visibility — recreate an
  issue tracker inside a 4-person inbox and reopen the reporter-feedback loop rejected in
  ADR-010. (3) Strict immutability (the session's recommended default) — developer call
  against: typo friction outweighs tamper-evidence among four trusted founders.
  (4) A unified status+notes activity log — needs status *history* the schema doesn't
  keep; recorded as a visible non-decision rather than scope creep. (5) Any-Member edit
  with a visible edited-by stamp — the original decision, reversed the same day; see
  *Revised* above.
- *Assumption.* The convention "a changed decision is a new Note; edits are for typos"
  holds socially without enforcement. Author-gating no longer relies on trust for
  *tamper*-resistance, but it does assume a teammate's typo is worth less than the
  integrity of an attributed record — true at four founders, and more true as the team grows.
- *Invalidates it.* A need for a real audit trail or status history; reporter-visible
  responses; or notes becoming long-lived enough that a wrong one must be retracted rather
  than superseded → revisit toward note versioning, a proper activity log, or a
  soft-delete that keeps the row.

**Deferred (until validated).** Caching, read replicas, async/queues, rate limiting,
real observability — explicitly **not** decided now; revisit signal-driven post-validation.
