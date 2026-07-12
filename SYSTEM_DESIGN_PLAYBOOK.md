# System Design Playbook

**An MVP-first lifecycle for designing and building systems that scale — by addition, not rewrite.**

This is a reusable, project-agnostic playbook. Run it once per new system. It takes you from a blank page to a deployed, validated MVP, and then to a production-grade system — without ever throwing the foundation away.

It is also a thinking tool. Each step is written around the *question an architect asks*, the *tradeoff being made*, and the *condition that would prove the decision wrong*. Follow the steps and you ship a system. Internalise the questions and you stop being someone who executes specs and become someone who writes them.

> **How to run this — read before starting.**
> This is a **convergent** process: its job is to drive each decision to a single committed answer, not to widen the option space. Do **not** engage brainstorming or ideation behaviour while running it — divergent facilitation (generating options, "let's explore", open-ended discovery) directly conflicts with the convergence every one-way-door artifact demands. The questions throughout this playbook exist to be **answered and recorded, not opened up**. Assistance that helps *capture, format, or render* the decisions is welcome; only the divergent/ideation mode is excluded. Drive every artifact to a committed decision.

---

## The two ideas everything rests on

**1. Two layers, only one of them churns.**
There is a *design layer* (what makes a system coherent: domain modelling, contracts, boundaries, consistency) and an *execution layer* (how the work gets done: prompts, context, agentic loops). The execution layer evolves fast and is commoditising. The design layer has been stable for thirty years and is where architectural judgement lives. This playbook is almost entirely about the design layer, because that is the durable skill.

**2. One-way doors vs. two-way doors.**
Every design decision is one of two kinds:

- **One-way doors** — catastrophic or near-impossible to reverse once code exists. Domain model, tenancy/isolation, consistency model, core architecture, core contracts. *Get these right at production quality from day one, even for an MVP.* They are cheap to do well on a blank page and ruinous to retrofit.
- **Two-way doors** — cheap to change later. Most features, UI, edge cases, internal implementation, scale optimisations. *Defer these aggressively.* Specifying them early is wasted effort because you will change them anyway.

The entire MVP-first strategy is this split applied: **design the foundation for production, build the feature set for validation, scale by addition.** "MVP" is permission to cut *features*. It is never permission to cut *foundation* — because later is a rewrite, and a rewrite is the punishment for succeeding.

---

## Section 0 — How to use this playbook

### The lifecycle

```
FOUNDATION  →  MVP  →  VALIDATE  →  SCALE
(design the      (build the    (go / pivot   (same loop,
 one-way doors    thinnest      / kill        signal-driven
 at production    feature set   against        prioritisation,
 depth)           that tests    written         deepen the
                  the idea)     criteria)       two-way doors)
```

### The rules of engagement

- **Design is days, not weeks.** Several artifacts below are an afternoon each. If you are not at a deployed MVP within roughly a week, the documents have stopped being design and started being avoidance.
- **No Scrum. Continuous flow.** You are solo with agents. Sprints, story points, and velocity are human-coordination tools that mean nothing here. Your entire board is `Backlog → Building → Done`, with a work-in-progress limit of **one** (one story per context window). Pull → build → ship → pull the next.
- **The lifecycle is real; the framework is not.** Keep lowercase-agile (iterate, vertical slices, working software over documentation, just-in-time detail). Drop every named SDLC ceremony — they are all shaped for teams.

### Per-system checklist

```
[ ] Foundation: artifacts 1–4 at production depth, 5–7 at MVP depth
[ ] Validation hypothesis stated (Artifact 01); kill criteria committed at the validation gate (§6)
[ ] CLAUDE.md + BUILD_STATUS seeded
[ ] MVP epic broken into agent-ready stories
[ ] Walking skeleton (= the MVP) deployed end-to-end to production
[ ] Validation gate reached and decided: go / pivot / kill
[ ] If go: scale path begun, signal-driven
```

---

## Section 1 — What the design phase produces

The output of design is **not a pile of documents**. It is a *substrate that makes any single story self-contained enough to build correctly in isolation.* It has three parts:

| Part | What it is | Stability |
|---|---|---|
| **Context package** (artifacts 00–06) | Reference the agent reads to make globally-coherent decisions | Stable; the one-way-door parts barely change |
| **MVP epic** (artifact 7, near-term) | The moving feed of work | Changes constantly by design |
| **The bridge** (CLAUDE.md + BUILD_STATUS + story template) | Standing rules + standing state + the per-feature unit the agent consumes every session | Living |

A story is buildable on its own *only because* the context package sits behind it. Stable substrate + one moving unit = feature-by-feature execution that converges instead of wandering. Wandering toward an underspecified goal is the precise definition of "vibecoding."

### Depth markers — and how build mode reassigns them

Because we build MVP-first, every artifact declares its required depth:

- **[PRODUCTION DEPTH]** — a one-way door. Do it properly now. Cutting corners here means a rewrite later.
- **[MVP-THIN]** — a two-way door. Cover only what the MVP touches; deepen after validation.

**The markers below are the defaults for _Product_ mode.** But which doors are one-way is not fixed — it is set by *why you are building*, declared in artifact 1. A learning build and a product build can be the same system on paper and have opposite depth maps. So read the markers as the Product-mode baseline, then let your declared mode reassign them:

- **Product mode** — domain model, tenancy, core architecture are one-way doors (the defaults below hold).
- **Learning mode** — *the thing you are learning* is the only one-way door; everything else is a two-way door you cut **more** ruthlessly than an MVP, because anything not serving the lesson is distraction.
- **Portfolio mode** — *legibility* is the one-way door (clean, readable, runnable by a stranger); scale and edge cases are two-way doors you ignore.

A "done handoff" in Product mode = artifacts 1–4 at production depth, 5–7 thin, CLAUDE.md and BUILD_STATUS seeded, and the MVP epic sliced into stories. In other modes the spine is whatever artifact 1's mode makes one-way.

---

## Section 2 — The artifacts (00–07)

For each: **the architect's question** it answers, what to include, the *done-enough* bar, what to leave out, its depth marker, and a fill-in template.

**Numbering & file convention.** The artifacts are numbered **00–07** and produced as files named `NN-<artifact>.md` so they sort uniformly on disk: `00-requirements-brief.md`, `01-intent-and-constraints.md`, `02-domain-model.md`, `03-tenancy-model.md`, `04-architecture.md`, `05-api-conventions.md`, `07-epic-map.md`. Artifact **06** is the one exception — it is two files, because it has a reusable core: `06a-engineering-principles.md` (reused verbatim across systems) and `06b-engineering-decisions.md` (filled fresh per system). Keep the numbering when you create them in a repo. Artifact 00's *input* — the founder discussion doc — is deliberately **not** a numbered artifact; it lives outside the repo (see Artifact 00).

---

### Artifact 00 — Requirements Brief  `[FRONT DOOR — precedes Artifact 01]`

**Architect's question:** *What did we agree we're building, and is every one-way-door decision settled enough to start designing?*

The front door. It captures the **settled, design-shaping agreements** — problem, actors, domain, invariants, tenancy, non-goals — in the form the design phase consumes, and it feeds Artifact 01. For a collective product its job is to force *false consensus* into the open before design starts: several founders who each meant something different discover it here, on paper, cheaply — not after building.

**Its input is a non-artifact.** The messy conversation happens in a separate **discussion doc** (`requirements-discussion.template.md`), held outside the repo — the working space where founders hash it out and mark each area Agreed / Disputed / Undecided. Artifact 00 is the *recording* of that conversation's design-relevant conclusions. The discussion doc is not one of the numbered artifacts; it is what produces this one.

**Include**
- Problem & why-now
- **Actors** — every distinct kind of user/role *(one-way door → feeds 01, 02, 04)*
- Core domain objects & the journey *(feeds 02)*
- **Invariants** — the rules that must never break *(feeds 02)*
- **Tenancy / one-or-many / scale** *(one-way door → feeds 03)*
- Constraints & explicit non-goals *(feeds 01, 04)*
- A **resolution state** (Agreed / Disputed / Undecided) on each, and a **handoff gate**: you may not start Artifact 01 until every one-way-door area is Agreed.

**Excludes:** the "what does worth-continuing mean" agreement. That is a go/no-go rule on the *result*, not a design input — so it lives at the **validation gate (§6)** as the kill criteria, not here. Artifact 00 carries only a pointer to it.

**Done enough:** every one-way-door area (actors, tenancy) is Agreed and the discussion doc's disagreement register is clear. An unresolved one-way door is the one thing that must not pass into design.

**Leave out:** solutions, screens, tech choices, features. This is the problem-space agreement, not the design.

> **▸ TEMPLATE — 00-requirements-brief.md** *(each area also carries: Resolution ⬜ Agreed / Disputed / Undecided)*
>
> **Problem & why-now.** `<what's broken, who feels it, what they do today>`
> **Actors.** `<every distinct user/role — who pays, who uses>` *(one-way door)*
> **Domain objects & journey.** `<the main things; one object's life start → finish>`
> **Invariants.** `<numbered: must always hold / must never happen>`
> **Tenancy / scale.** `<one org / many isolated / many shared; rough numbers>` *(one-way door)*
> **Constraints & non-goals.** `<regulatory, integrations, limits; what it will NOT do>`
> **Kill criteria →** decided at the validation gate (§6), not here.
> **Handoff gate.** All one-way-door areas Agreed + register clear → begin Artifact 01.

---

### Artifact 01 — Intent & Constraints  `[ALWAYS FIRST — declares the build mode]`

**Architect's question:** *Why am I building this, for whom, under what limits — and what does "done" mean for that reason?*

This is your "no" machine, the contract for the whole effort, and — critically — the artifact that **declares your build mode**, which then sets which doors are one-way for everything else. Without it you and the agent both gold-plate, and success becomes a feeling instead of a decision.

**Declare the mode first.** Why does this system exist?

| Mode | What replaces the hypothesis | What "done" means | What you cut |
|---|---|---|---|
| **Learning** | A learning objective: *"build X to understand Y"* | You understand Y and can explain the tradeoff | Everything not touching Y — aggressively, deeper than an MVP |
| **Portfolio** | A demonstration target: *"show competence in Z to viewer W"* | It is legible and runnable by someone else | Scale, edge cases, anything invisible to a viewer |
| **Product / POC** | A validation hypothesis (kill criteria committed at the validation gate, §6) | Someone adopts or pays, or the signal says stop | Features and polish — never foundation |

You do **not** need a market problem to build. Curiosity, practice, and portfolio are legitimate reasons. But you **do** need a stated objective — that is what makes a build *converge* instead of wander, and a wandering build with no objective is the learning equivalent of vibecoding.

**Include (all modes)**
- The mode, declared explicitly
- The mode's success condition (the matching row above)
- **The actors** — every distinct kind of user or role the system serves (even if it is just you). This is a one-way-door input: the domain model's boundaries and the authorization model both derive from it. Name the actors now, not during implementation.
- **Explicit non-goals** — what this deliberately will *not* do

**Include (Product mode additionally)**
- Who pays, and the primary user
- Scale assumptions as orders of magnitude (concurrent users, data volume, growth)
- Non-functional requirements (availability, latency, consistency, compliance)

**Done enough:** one page; someone could use it to reject a proposed feature. In Product mode, the validation hypothesis is stated here; its kill criteria are committed at the validation gate (§6), specific enough that you cannot rationalise failure as success. In Learning mode, the objective names something you do **not** already understand (see guardrails).

**Leave out:** solutions, UI ideas, technology choices.

> **▸ TEMPLATE — intent-and-constraints.md**
>
> **Build mode.** `<Learning / Portfolio / Product>`
> **Why this exists (success condition).**
> · *Learning* → *I am building `<X>` to understand `<Y>`. Done when I can explain `<the specific tradeoff / mechanism>`.*
> · *Portfolio* → *I am showing competence in `<Z>` to `<viewer>`. Done when a stranger can run and read it.*
> · *Product* → hypothesis: *We believe `<who>` will `<do what>` because `<why>`.* Kill criteria → committed at the validation gate (§6), not here.
> **Actors.** `<each distinct user/role the system serves — may be just you>`
> **Non-goals.** `<what this will not do>`
> **— Product mode only —**
> **Who pays / primary user.** `<...>`
> **Scale assumptions.** `<~N users, ~N records, growth>`
> **NFRs.** Availability `<target>` · Latency `<target>` · Consistency `<where strong vs eventual>` · Compliance `<...>`

---

### Artifact 02 — Domain Model  `[PRODUCTION DEPTH]`

**Architect's question:** *What are the true entities, what are the rules that must always hold, and where are the consistency boundaries?*

The single most important artifact and the one most engineers skip. An agent can infer a database schema from a list of entities. It **cannot** infer your invariants — and that is exactly where it makes locally-reasonable, globally-wrong decisions across thirty files before you notice.

**Include**
- Entities and their key attributes
- **The high-level flow (the journey)** — the end-to-end path each actor takes (e.g. "customer browses → builds order → submits → kitchen receives → fulfilled"). This is *not* UI; it is the **narrative form of the state machine**. Writing it is how you discover the lifecycle and the aggregate boundaries — derive the state machines below from it.
- **Aggregate roots and their boundaries** — what is consistent together, what is eventually consistent across
- **Invariants** — business rules that must *always* be true (e.g. "an enrollment cannot exist without an active term")
- **Lifecycle state machines** for stateful entities (e.g. "invoice: draft → issued → paid → void")
- A short glossary (ubiquitous language) so terms mean one thing everywhere

**Done enough:** the journey is mapped for each actor; every aggregate has its invariants written; every stateful entity has its states and legal transitions.

**Leave out:** a full ERD with every column, DB-specific types, indexes — those derive later. And the **detailed UI flow** — screens, modals, buttons, field-level validation. That is the most-churning thing in the system, a two-way door that emerges per-story in execution. Capture the *journey* here; never the *screens*.

> **▸ TEMPLATE — domain-model.md**
>
> **Glossary.** `<Term = precise meaning>` …
> **High-level flow.** For each actor: `<step → step → step>` — the journey, not the screens. The state machines below derive from this.
> **Entities.** For each: `<name — key attributes — one-line purpose>`
> **Aggregates.** `<Aggregate root → entities inside its boundary → what stays consistent within it>`
> **Invariants.** Numbered list of rules that must always hold. `<INV-1: ...>`
> **State machines.** For each stateful entity: `<states>` and `<from → to (trigger)>` transitions; mark illegal transitions explicitly.

---

### Artifact 03 — Tenancy & Isolation Model  `[PRODUCTION DEPTH]`

**Architect's question:** *How is one customer's data kept separate from another's, and at which layer is that boundary enforced?*

A one-way door that cross-cuts everything. Get it wrong after fifty stories and it is a rewrite. *If the system is genuinely single-tenant, this collapses to a one-line ADR — do not force it.*

**Include**
- Isolation strategy (shared schema + row-level security / schema-per-tenant / database-per-tenant) and *why*
- How tenant context propagates: request → through each layer → to the query
- Where the boundary is enforced (and how it is impossible to bypass)
- What is shared vs. what is isolated
- Tenant onboarding implications

**Done enough:** a developer can state exactly how any given query is scoped to a tenant.

**Leave out:** the actual RLS policies or migration SQL — that is implementation.

> **▸ TEMPLATE — tenancy-model.md**
>
> **Strategy.** `<shared-schema+RLS / schema-per-tenant / db-per-tenant>` because `<rationale>`.
> **Context propagation.** `<where tenant id enters → how it flows → how the query is scoped>`
> **Enforcement layer.** `<the single layer that guarantees isolation, and why it cannot be bypassed>`
> **Shared vs. isolated.** `<what crosses tenants vs. what never does>`
> **Onboarding.** `<what happens when a new tenant is added>`

---

### Artifact 04 — Architecture & ADRs  `[PRODUCTION DEPTH for the bones; ADRs accrete]`

**Architect's question:** *What are the major pieces, how do they fit, and what assumption makes each choice correct?*

Your real HLD. Its job now is to be *context for agents* — the map that keeps the loop converging. The most valuable habit in this entire playbook lives here: **every significant decision gets an ADR, and every ADR names the condition that would invalidate it.** That single discipline — writing the *why* and the *what-would-break-it* — is the core of architectural thinking. It is the thing a spec-executor never does and an architect always does.

**Include**
- Layer / module structure and module boundaries
- Tech-stack choices **with rationale** (not "we use X" — the *why* future-you can audit)
- Cross-cutting concerns: auth, the global exception/logging contract, configuration, external integrations
- **Deployment topology & environment progression** — where the system runs and how it gets promoted: the local/dev environment you develop against, the production target (e.g. a PaaS), and the path between them. This is a design decision, not an afterthought — it shapes config, containerization, env-var handling, and the walking-skeleton deploy — so decide it here rather than improvising at deploy time. Tech-neutral: "local Docker → PaaS" is one common instance, not a mandate.
- **Containerization scope** *(a named decision — must be stated, never inferred)* — what runs in containers locally: the **full application stack** (app + datastore + services) or the **datastore only** (app runs natively). These are different architectures with a real tradeoff — full-stack gives behavioural parity with prod (so "runs locally" predicts "runs deployed") at slower rebuilds; datastore-only iterates faster but drifts from prod (the exact source of "works on my machine" bugs). **Default: full local containerization**, because it makes the local end consistent with a parity-based promotion chain. Note the asymmetry: *local* is fully containerized; *prod* deploys to a host/PaaS (app + managed DB) — a different topology. Parity here is **behavioural** (same runtime, migrations, config surface), not identical infrastructure. Because "local Docker" silently resolving to datastore-only is a real failure mode, this scope must be **stated explicitly here, never left to inference**.
- An **ADR log** that grows over the life of the system

**Done enough:** the bones are fixed; the dev→prod environment path is named; ADRs accumulate as decisions are made. Deferred scale decisions (caching, replicas, queues) are explicitly marked "deferred until validated."

**Leave out:** detailed class design and per-feature sequence diagrams. Also the *actual* dev-environment files (the compose file, run scripts) — those are execution scaffolding that emerges during the build (usually the first story), not design. Decide the *topology* here; the setup files come later.

> **▸ TEMPLATE — architecture.md + one ADR per decision**
>
> **Architecture overview.** `<layers / modules and their boundaries; one diagram or bullet map>`
> **Cross-cutting.** Auth `<...>` · Errors & logging `<the contract>` · Config `<...>` · Integrations `<...>`
> **Deployment & environments.** Dev `<where you develop — e.g. local containers>` → Prod `<the target — e.g. a PaaS>`; promotion path `<how code moves dev → prod>`; the walking skeleton deploys to `<the prod target>`. *(Default three-environment model: `dev` ephemeral preview → `preprod` pristine prod-copy → `main` production; branch→env mapping and promotion flow live in CLAUDE.md's Git workflow. Collapse to dev→prod for Learning/throwaway builds.)*
> **Containerization scope.** Local: `<full app stack + datastore | datastore only>` — **default full-stack** for prod parity. Prod: deploys to `<host/PaaS>` (app + managed DB) — a different topology; parity is behavioural, not infrastructural. *(Learning/throwaway: datastore-only or none is fine.)*
>
> **ADR-NNN: `<title>`**
> - **Context.** `<the forces at play>`
> - **Decision.** `<what we chose>`
> - **Alternatives rejected.** `<X, and why not>`
> - **Assumption that makes this right.** `<...>`
> - **What would invalidate it.** `<the signal that means revisit>`

---

### Artifact 05 — API Conventions  `[MVP-THIN]`

**Architect's question:** *What rules will every endpoint obey, so I never re-decide them per feature?*

Conventions, not per-endpoint specs. Writing full OpenAPI for every endpoint upfront is the same over-specification trap as a full low-level design. Individual endpoint contracts are elaborated *with each story*.

**Include**
- Status-code rules (e.g. POST→201, DELETE→204 regardless of prior existence, GET-collection→200 with empty array)
- Error-envelope shape
- Pagination strategy, auth scheme, resource naming, versioning

**Done enough (MVP):** an agent can build a new endpoint consistent with all others without being re-told the conventions. Cover what the MVP touches; expand after validation.

**Leave out:** per-endpoint contracts for endpoints that do not exist yet.

> **▸ TEMPLATE — api-conventions.md**
>
> **Status codes.** `<rules>`
> **Error envelope.** `<shape>`
> **Pagination.** `<strategy>` · **Auth.** `<scheme>` · **Naming.** `<resource conventions>` · **Versioning.** `<approach>`

---

### Artifact 06 — Engineering Standards  `[REUSED CORE + PER-SYSTEM DECISIONS — dialed by mode]`

**Architect's question:** *What rules does every unit of code obey — so the low-level design is constrained without being pre-drawn?*

This is where your low-level design lives now: not per-class diagrams, but the rules every unit obeys. The agent generates the actual LLD per story, bounded by these standards — strictly better than a hand-written LLD that drifts out of sync.

**This is the one artifact you review rather than write from scratch.** Every other artifact here is produced fresh; engineering standards come with a **default** you carry across projects and *review* as you go — accept as-is if this system doesn't warrant change, or amend with reason. It is two files, split by how often they change:

- **`06a-engineering-principles.md`** — the universal quality contract. Stack-neutral, checkable invariants (layered ownership, the exception/logging contract, fail-fast, contract consistency, restraint, surgical changes), each with a mechanical review Check and a Floor-vs-Scales dial. **The sticky default** — reused across projects, rarely changed. You *review* it here and amend only when a system genuinely needs a principle added, trimmed, or dialed differently.
- **`06b-engineering-decisions.md`** — the per-system decision form: the choices the principles leave open (the contract's actual codes and envelope, the exception taxonomy, tenancy pointer, auth model, stack, **and the test depth**). **Expected to change every project** — you *decide* it here, fresh, as hard requirements for this MVP.

Both stay their own files — the playbook points at them, does not inline them — so the reusable default stays a single clean copy with no risk of drift. CLAUDE.md points the agent at both.

**Reviewing them is a step, not an assumption.** When you reach Artifact 06, go through both: ratify 06a as default or amend it, and fill 06b's decisions. **Do not let any decision pass by omission** — in particular the **test depth**: which layers exist (unit / integration / end-to-end) and how thick each is. Thin testing must be a choice you make on purpose, never a default that slipped through. *(This is the specific gap to close: last time, testing came out thin because it was inherited rather than decided.)*

**Set the dial** from the declared mode — Production → Full; MVP/POC → collapsed; Learning → floor everywhere except the subsystem being learned, which is Full.

**Done enough:** 06a is reviewed (ratified, or amended with a reason for each change); 06b answers every choice the principles leave open, test depth included; nothing a story would otherwise re-litigate is left undecided.

**Leave out:** an exhaustive style guide — let the formatter own style. Do not copy the universal principles into the decisions file; the decisions file holds only what is specific to this system.

---

### Artifact 07 — Epic Map  `[MVP-THIN — MVP epic detailed, rest are placeholders]`

**Architect's question:** *What is the whole territory, and what is the smallest slice of it that tests the idea?*

Epics upfront (the map). Stories elaborated just-in-time. The MVP epic is detailed now; every other epic is a named placeholder for the post-validation roadmap.

> A version number climbing on a story tracker (v2, v3, v4…) is a *symptom*: it means stories were written in detail upfront and reality kept forcing re-plans. With this structure the epic map is stable and only the next few stories are ever in flux.

**Include**
- All epics named and roughly sequenced (with dependencies)
- For the **MVP epic only**: stories elaborated to agent-ready depth (see Section 5)
- All other epics: a one-line placeholder each

**Done enough:** epics named and ordered; only near-term stories detailed.

**Leave out:** detailed stories for anything past the MVP.

> **▸ TEMPLATE — epic-map.md**
>
> **MVP epic: `<name>`** — the hypothesis-testing slice. Stories: `<list, each agent-ready>`
> **Backlog epics (placeholders).** `<epic — one line>` …
> **Rough sequence / dependencies.** `<...>`

---

## Section 3 — Exit criteria & the MVP skeleton

The walking skeleton **is** the MVP. They were never two things. Instead of one trivial feature, it is the *minimum set of real features that proves or kills the hypothesis* — built end-to-end through every layer (auth → API → service → domain → persistence → response) and **deployed to production.**

This is the highest-leverage move in the playbook: it validates your architecture, tenancy model, and deployment pipeline against reality while the cost of being wrong is one thin feature set — not fifty stories.

**Design stops when the one-way doors are decided — not when everything is perfect.**

```
[ ] Artifacts 1–4 at production depth
[ ] Artifacts 5–7 thin and sufficient for the MVP epic
[ ] Validation hypothesis + kill criteria committed (validation gate, §6)
[ ] CLAUDE.md seeded (Section 4)
[ ] BUILD_STATUS seeded — story table derived from artifact 07 (Section 4)
[ ] MVP epic sliced into agent-ready stories
→ Build the skeleton. Deploy it. Then start pulling MVP stories.
```

---

## Section 4 — CLAUDE.md: the standing rules

**This is not an eighth design artifact. It is the bridge.** Seeded by the design phase, owned by the build.

Its job is **persistent standing rules** — the always-loaded conventions and safety rules the agent reads at the start of every session, the thing that makes session 40 behave like session 1. The distinction that keeps it from rotting into an 800-line dump nobody reads:

- The **context package** is *reference / knowledge* — read when a story needs it. (What an Invoice is.)
- **CLAUDE.md** is *standing rules / behaviour* — obeyed every session, unprompted. (Never log-and-throw; ask before touching tenancy; never commit secrets; the git conventions.)

**It is rules, not knowledge. It is an index, not the content.** If it is knowledge, it goes in the package and CLAUDE.md *points* at it. That test keeps it under a page or two — the only length an agent actually respects. Seed it at skeleton time with the pointers, the stop rules, and the conventions; let the gotchas section grow as the agent surprises you. Do **not** try to finalise it upfront the way you finalise the domain model.

**CLAUDE.md holds standing rules and pointers — not the execution workflow.** How stories are actually built — the loop, the planning, the review, how a story gets sliced into work — is the *workflow's* job, chosen per project, and deliberately **not** defined here. CLAUDE.md stays workflow-agnostic so the design output can be built against by any workflow, including a fast ad-hoc one. It supplies the rules every workflow must obey; it does not supply the procedure.

> **▸ TEMPLATE — CLAUDE.md**
>
> **Context-package index.** Domain model → `<path>` · Tenancy → `<path>` · Architecture/ADRs → `<path>` · API conventions → `<path>` · Engineering principles → `<path>` · Engineering decisions → `<path>`
> **State — always maintain.** BUILD_STATUS → `<path>`. Every story that lands updates its row (status + commit); every change that wasn't a planned story gets a line in the off-epic ledger. Keeping BUILD_STATUS current is **non-negotiable** — a stale tracker misleads the next session with authority, which is worse than no tracker.
> **Stop rules (ask before doing).** `<touching tenancy / schema migrations / auth / deleting data / anything irreversible>`
> **Never commit secrets.** No `.env` or any file containing credentials, keys, tokens, passwords, or PII enters a commit — ever. This is P3 (never-log-secrets) extended from logs to commits: secrets never enter *any* durable artifact. Two layers, no third-party tool required:
> - **Structural guarantee (the real defence): `.gitignore` the secret files before the first commit.** `.env`, `.env.local`, anything holding credentials — gitignored so they *cannot* be staged. Real secrets live only in gitignored files locally and in the platform's env-var UI in prod; the repo carries `.env.example` with placeholders only. If nothing sensitive is ever tracked, there is nothing to leak. This is what prevents the leak — it does not depend on anyone remembering.
> - **Backstop (soft): scan the staged diff before every commit.** Before committing, check the staged content for secret/PII patterns (`API_KEY=`, `SECRET`, `PASSWORD=`, tokens, long high-entropy strings, any `.env` in the staged files) and refuse to commit on a match. This catches a secret that slipped into a tracked file the gitignore didn't cover. It is a backstop *because* it's an instruction — lead with the gitignore, never let the scan become an excuse to relax it.
>
> A committed secret is compromised even if a later commit deletes it (it stays in history) — **rotate it.**
> **Git workflow (default — three-environment promotion).** Branch→environment mapping: `main` → **production** (protected base) · `preprod` → a **pristine exact copy of production** (the verify gate) · `dev` → **ephemeral preview** with a throwaway DB. Flow: feature branches (`feature/<story-id>-<slug>`) are tested on the local dev environment, then **squash-merged into `dev`**. Promotion: **cherry-pick** the verified changes from `dev` → `preprod`, verify against the prod-copy, then promote `preprod` → `main` (production). Commit convention: `feat(scope): …` / `fix(scope): …`. *The developer owns all git operations — the agent proposes, never runs, git commands.* **Footgun to watch:** cherry-picking after squash-merges can pull a change whose dependency you didn't pick — a subset that built in `dev` may not build in `preprod`. Track inter-change dependencies before promoting. **Mode dial:** this is the default; for Learning/throwaway builds, collapse to a single `main` + short-lived feature branches — the three-environment pipeline is release discipline a pre-validation toy doesn't need.
> **Branches vs. worktrees.** The **feature branch is the default working branch** — create it directly (`git checkout -b feature/<story-id>-<slug>`), not a worktree. Use a **worktree only when parallel work genuinely needs it** (e.g. multiple stories in flight at once). When one is used: **branch first, then the worktree is a checkout _of_ that feature branch** (`git worktree add <path> feature/<story-id>`) — the branch is primary and drives promotion; the worktree is disposable scaffolding. **Lifecycle: remove the worktree once its feature branch is merged to `dev`** (`git worktree remove <path>`) — the branch and history persist; only the extra directory goes. Worktrees are orthogonal to containerization: the dev stack builds from whatever directory is checked out, so keep compose paths relative to work from either location.
> **Gotchas.** `<non-obvious traps in THIS codebase — grows during the build>`

---

### BUILD_STATUS — the standing state (seeded here too)

CLAUDE.md is the standing *rules*; **BUILD_STATUS is the standing *state*** — the live map of what's built, and the index a cold session reads first. It is the second file seeded at this handoff, and — like the initial CLAUDE.md — it is **derived, not authored**: its story table is the epic map (artifact 07) transcribed, every row not-started. From there it is maintained live during the build (per the CLAUDE.md rule above), becoming the index that ties the three record layers together:

- **Design artifacts** — what the system *is* and why. *Current state; curated.*
- **Story plans** — why each piece was built the way it was. *Immutable, point-in-time intent — never updated after the fact, so they never go stale.*
- **Commits** — what literally changed. *A complete but unranked event log; queried only when the curated record has a hole, not read to understand the project.*

BUILD_STATUS points at both the plan and the commit for each story, so the tracker is the map and the plans/commits are the territory. The **off-epic ledger** applies the same idea to unplanned work — a line for every change that wasn't a planned story — so small adjustments stop vanishing, especially on a project picked back up after time away (the exact case where a hand-maintained tracker is most valuable and most likely to have lapsed — reconcile against commits on return).

> **▸ TEMPLATE — BUILD_STATUS.md**
>
> **What this is + source-of-truth index.** One-line orientation; pointers → design artifacts (`docs/design/`), story plans (`<path>`), commit history. On session start: read this, then verify against the code — code wins, flag mismatches.
> **Story table** *(derived from artifact 07; all rows start ⬜)* — `| # | Story | Status | Plan | Commit |`, with a key: ⬜ not started · 🔄 in progress · ✅ done · ⚠ blocked.
> **Off-epic ledger** *(starts empty; grows during the build)* — `| Date | Change | Why it wasn't a story | Commit |`.

---

## Section 5 — The agent-ready story: the moving unit

The literal interface between design and execution — what you feed the agent, feature by feature. A story is agent-ready when it has all five:

1. **A context anchor** — which epic, which bounded context, which existing patterns to follow. Tells the agent *which parts of the context package to load.*
2. **One vertical slice** — a single feature through every layer (API → service → domain → persistence → response). *Never* a horizontal slice like "build the repository layer" — that produces untestable, uncommittable work.
3. **Acceptance criteria that map 1:1 to tests** — the ACs *are* the definition of done and the test spec at once.
4. **An explicit scope boundary** — what this story does *not* touch. This is what stops a five-file story becoming a thirty-file one. Agents over-reach; the boundary is the leash.
5. **Fits one context window.** If it doesn't, it is an epic wearing a story costume.

> **▸ TEMPLATE — story**
>
> **Title.** `<feature>`
> **Context anchor.** Epic `<...>` · Bounded context `<...>` · Follow patterns in `<...>`
> **Vertical slice.** `<the one feature, end to end through every layer>`
> **Acceptance criteria (= tests).**
> - `<AC-1 — observable behaviour>`
> - `<AC-2 — ...>`
> **Scope boundary — do NOT touch.** `<files / layers / concerns out of scope>`
> **Fits one window?** `<yes — if no, split>`

---

## Section 6 — The validation gate & the scale path

### The gate — and the kill criteria

The MVP is not the end of a process. It is a decision point, judged against the **kill criteria**: the pre-committed threshold that says whether the core hypothesis held.

**The kill criteria live here, not in the design.** They do not shape a single line of what you build — they are a go/no-go rule on the *result* — so they belong to this gate, not to Artifact 01 (which carries only a pointer). They are committed **upfront, before you build**, and for a collective product they are agreed by the founders and recorded, because otherwise each founder quietly holds a different definition of "worth continuing" and you fight about it at the worst possible time. Whoever measures them later (you, analysts, marketing) checks the real numbers against this line — you are not the one gathering the data; you are the one who drew the line in advance.

> **▸ Kill criteria (commit before building).** *By `<date>`, over `<window>`: primary — if `<metric>` < `<threshold>` → pivot, < `<lower>` → kill; floor — sustained `<minimum volume>` = too little to judge; qualitative — `<does the buyer keep it past the trial?>`.*

The three outcomes:

- **Go** — keep pulling stories against the same context package. The foundation already supports it.
- **Pivot** — the domain model flexes because it was built on *invariants*, not on the throwaway UI.
- **Kill** — you lost days, not months. This is the system working as designed.

The criteria were committed upfront for one reason: **so the gate is a decision, not a feeling.** You are emotionally invested in your projects; without pre-committed kill criteria you *will* rationalise whatever happened as validation. That is confirmation bias, and the written threshold is the only defence. **Sharpen them rather than relax them** — if they feel like they would strangle a promising product, they are usually measuring the wrong thing (revenue on day one instead of "will anyone even use this"), not being too strict. Relaxing them does not make the product more likely to succeed; it makes you less likely to find out whether it did.

### Scaling is not a new phase — it is the same loop, re-pointed

The core principle: scaling is the *same continuous-flow loop* — pull, build, ship — with **one** thing changed. Story prioritisation flips from **hypothesis-driven** (your best guess) to **signal-driven** (evidence from real usage). The MVP's entire job was to generate that signal. "What's next" = re-order the backlog against what real usage taught you, and keep pulling. **Addition, not rewrite** — true *only because* the one-way doors were built right.

### What comes due after validation

The deferred two-way doors, plus the production concerns the MVP was allowed to skip:

- The features and edge cases you deliberately cut
- NFRs gaining teeth — behaviour under real load, concurrency, latency
- **Observability becoming real** — metrics, alerting, tracing
- Security hardening — rate limiting, input audit, dependency scanning, tenancy-boundary verification
- Operational maturity — *tested* backups, rollback, runbooks, DR
- Performance optimisation — but only now, against real hot-path data
- The scale-architecture ADRs you marked "deferred" — caching, read replicas, async/queues — now decided with evidence

### The ordering principle: irreversibility × likelihood

You cannot do all of it at once. Sequence by how catastrophic and how likely each risk is:

1. **Data-loss & security risks first** — catastrophic and irreversible. Tested backups, verified tenant isolation, secrets locked down.
2. **Downtime risks second** — costly but recoverable. The actual bottleneck the load revealed, plus the observability to see the next one coming.
3. **Friction last** — annoying but survivable. Deferred features, polish, edge cases.

### Signal-triggered, never calendar-triggered

You do not add a cache because "it's time to scale." You add it because a metric shows a specific path is hot. This is anti-premature-optimisation applied to the scale phase — and it is *why observability is the first thing you build post-validation*: it is the instrument that generates every subsequent trigger. Scaling without it is vibecoding in a production costume.

### How the artifacts evolve

The MVP-thin docs deepen: API conventions expand to cover new surface, standards harden, placeholder epics get elaborated, fresh ADRs capture each scale decision. The one-way-door docs — domain, tenancy, core architecture — *mostly do not change.* **That non-change is the proof they were built right.**

---

## Section 7 — Guardrails

The honest failure modes. The first two are opposite risks, and you are exposed to both.

1. **Docs-as-procrastination.** Beautiful documents become the new avoidance — weeks of design, no shipped code. *Design is days. Walking skeleton within ~a week or the docs have become avoidance.*

2. **"It's just an MVP" as licence to skip the foundation.** The opposite trap: cutting one-way doors because you're moving fast. *Later is a rewrite. MVP cuts features, never foundation.* You do the one-way doors at full depth **precisely because** you're moving fast on everything else — that is what keeps the rest cheap.

3. **"It's just a learning project" as cover for the comfort zone.** Building without a market problem is legitimate; building without an *objective* is not. The label "learning project" quietly becomes permission to rebuild the same thing you already know how to build, indefinitely. *Test: finish the sentence "at the end I will understand X." If X is something you already understand, you are not learning — you are practising your comfort zone and calling it growth.* The objective in artifact 1 is what makes a learning build a real one.

4. **Premature optimisation at scale.** Adding caches/replicas/queues on a calendar instead of a signal. *Build observability first; let metrics trigger the work.*

5. **Confirmation bias at the gate.** Declaring victory because you want it. *Kill criteria are written before you build, and they are specific.*

6. **Horizontal slices.** "Build the repository layer" yields untestable, uncommittable work. *Every story is a vertical slice.*

7. **CLAUDE.md bloat.** It rots into a knowledge dump the agent half-ignores. *Procedure plus pointers, under two pages. Knowledge goes in the package.*

8. **The over-specification trap (the climbing version number).** Writing two-way-door detail at one-way-door timing. *Epics stable; only the next few stories in flux.*

---

## Section 8 — Stakeholder Views  *(generated, never maintained)*

The playbook deliberately **decomposes** a traditional PRD across artifacts (00, 01, 02, 07, and the validation gate) and keeps ADRs as a running log in Artifact 04 — because stable and volatile content change at different rates and shouldn't share a file. But a client, investor, or non-technical stakeholder expects the *traditional* formats: a PRD, and a flat list of decisions. This section produces those **as views** — assembled from the artifacts on demand, shown, and thrown away.

**The one rule that keeps this safe: a stakeholder view is a read-only rendering, never a source of truth.** You do not edit the generated PRD and treat it as the doc — that recreates the monolithic-PRD drift the playbook exists to avoid. Change happens in the artifacts; then you *regenerate* the view. If a stakeholder marks up the PRD, the changes flow back into the artifacts and you regenerate. Same discipline as BUILD_STATUS deriving from the epic map: the view is downstream of the truth, never parallel to it.

### View A — ADR Log  *(technical audience: a client CTO, a technical investor)*

Nearly free — your ADRs already live in Artifact 04 in a consistent format. The log is: collect every ADR across the system, render a summary index (ID · decision · status · date), then the full entries below. Template: `adr-log.template.md`.

### View B — Product Requirements Document  *(non-technical audience)*

A reshaping of artifacts into the format a non-technical reader expects — same content, their vocabulary. The assembly map:

| PRD section | Assembled from |
|---|---|
| Overview / problem & why-now | Artifact 01 (+ 00) |
| Users | Actors, Artifact 00 |
| Goals & **success metrics** | Intent (01) + kill criteria (validation gate, §6), reframed as metrics |
| What we're building (scope) | The MVP wedge, epic map (07) |
| Out of scope | Non-goals, Artifact 01 |
| Roadmap / what's next | Backlog epics, epic map (07) |
| Key decisions (optional) | Pointer to the ADR Log (View A) |
| Constraints & assumptions | Artifacts 01 / 04 |

Template: `prd.template.md` (markdown master) and a Word deliverable for the non-technical audience. Generating a PRD is **fill-from-artifacts**, not write-from-scratch — if a section has no artifact to draw from, that's a gap in the design, not the PRD.

---

## The throughline

The difference between an engineer who executes specs and an architect who writes them is one thing: **owning the *why*.** A spec-executor is handed decisions and builds from them. An architect makes the decisions, records the reasoning, and names the condition that would prove the reasoning wrong.

Every other habit in this playbook is downstream of that one. The domain model is the *why* behind the schema. The ADR is the *why* behind the stack. The declared intent is the *why* behind the build. The one-way/two-way split is the *why* behind what you finalise and what you defer.

So the single practice to carry into every system you touch: **for every significant decision, write the rationale and the invalidating condition.** Do that consistently and the architect's judgement stops being something you follow in a document and becomes the way you think.
