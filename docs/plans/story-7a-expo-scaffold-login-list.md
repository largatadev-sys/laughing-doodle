# Story 7a — Expo client: scaffold + login + my-entries list

**Status.** Planned, not yet built. Immutable once implementation lands — see
[BUILD_STATUS.md](../../BUILD_STATUS.md) for live status.

**Source.** [07-epic-map.md](../design/07-epic-map.md) Story 7a, elaborated through a
`/grill-with-docs` session on 2026-07-13. This file records *why* each choice was made;
the epic map records *what* is required. Per the developer's steer for this session,
implementation-detail decisions below were made by the agent and recorded here for review
before implementation; only the one decision that changed a design doc (the 7a/7b split
itself) was put to the developer directly.

---

## Scope

Stand up the Expo/TypeScript client project itself, then: login screen (stores JWT) →
read-only list of the caller's own entries (`GET /api/entries?userId=<me>`). Runs on web
(primary target) and native (Expo dev, practice target — [01](../design/01-intent-and-constraints.md)).
No create/edit/delete UI yet — that's Story 7b.

## Decisions made this session (and why)

1. **Split the original combined Story 7 into 7a/7b** — put to the developer directly
   because it changes `07-epic-map.md` and `BUILD_STATUS.md`. Confirmed: split, along the
   line the epic map itself already sketched (auth+list vs. the three write forms). 7a is
   the heavier half — it also stands up the project scaffold, routing, API client, and
   token-storage plumbing that 7b then reuses without re-deciding.

2. **New `client/` directory at the repo root** for the Expo project, sibling to `src/`
   (Java) and `build.gradle`. One repo stays polyglot: Gradle drives the backend,
   npm/Expo CLI drives the client — matching ADR-001's "single Expo codebase" without
   inventing a second repo, and matching CLAUDE.md's existing single-repo branch/merge
   workflow (a `feature/7a-...` branch touches only `client/` plus this plan file and the
   doc updates already made).

3. **Expo Router (file-based routing)**, not manually-wired React Navigation. It's Expo's
   current default, sits on top of React Navigation anyway, and gives one routing model
   that resolves consistently to a stack on native and to URLs on web — directly serving
   ADR-001's "web is the real target, native is practice from the same code" without
   platform-specific navigation code.

4. **No state-management or data-fetching library** (no Redux/Zustand/React Query) for
   7a's two screens. Plain `useState`/`useEffect` plus one typed `apiClient` wrapper.
   Justified by P9 (restraint — an abstraction is earned by pain that already exists;
   two screens with no cache-invalidation complexity don't earn one) and by 06b's dial
   ("Floor everywhere" for this build outside the two Learning subsystems, and the client
   isn't one of them — auth/JWT and the *ownership* enforcement path are).

5. **One typed `apiClient` module** (`client/lib/apiClient.ts`) as the single P6 gateway —
   every HTTP call to the backend goes through it; no bare `fetch` in screen components.
   It attaches the `Authorization: Bearer` header when a token is present, and uniformly
   parses the `{error:{code,message,details}}` envelope on any non-2xx response — verified
   both `JwtAuthenticationEntryPoint` (security-filter rejections) and
   `GlobalExceptionHandler` (controller-thrown errors) emit the *same* `ErrorEnvelope`
   shape, so one parser covers every failure path with no special-casing.

6. **Token storage abstracted behind one module** (`client/lib/tokenStorage.ts`) because
   `expo-secure-store` has no web implementation: native uses `SecureStore`, web falls
   back to `localStorage`, selected via Expo's `Platform.OS` at runtime. This is the
   standard Expo cross-platform pattern, not a novel design — flagged here only because
   it's the one piece of platform-conditional code in an otherwise shared codebase.

7. **Caller identity comes from the login response, not from decoding the JWT
   client-side.** `LoginResponse.user` (a `UserSummary`) already carries `id`, `name`,
   `username`, `role` — stored alongside the token, and `user.id` is passed as this
   screen's `?userId=` filter on `GET /api/entries`. No JWT parsing library needed on the
   client; the server is the only JWT-aware party on either side of this flow beyond
   carrying the opaque bearer string.

8. **Global 401 handling**: `apiClient` throws a typed `UnauthorizedError` on any `401`;
   an `AuthContext` (React Context wrapping the app, provided in the Expo Router root
   layout) catches it, clears the stored token, and the router redirects to `/login`.
   This directly implements the epic map's AC-3 (added during this split — the original
   combined Story 7 didn't call out expiry handling explicitly, but ADR-002's ~7-day TTL
   with no revocation/refresh means a session *will* eventually 401 mid-use, and the app
   needs a defined, non-crashing response to that rather than an undefined one).

9. **Hand-written TypeScript types** (`client/lib/types.ts`) mirroring the backend records
   verbatim (`EntryResponse`, `LoginResponse`/`UserSummary`, `ErrorEnvelope`) — no codegen
   tool (e.g. OpenAPI generator). Justified by P9/restraint at this scale (one developer,
   both ends of the contract, a handful of fields) and P7 (boundary types must exist and
   be named, not that their *source* must be generated).

10. **Bare React Native `StyleSheet.create`** for styling — no NativeWind/Tailwind, no UI
    component kit. Matches P9's restraint dial, which "leans harder toward restraint as
    builds get smaller," and the mode dial's "Floor everywhere" for non-Learning
    subsystems.

11. **A minimal logout action** on the list screen (not in the epic map's original AC
    list, added here) — without it, AC-3's "expired/invalid token returns to login" path
    has no way to be manually exercised on a *valid* token, and the screen would otherwise
    have no way to end a session. Small, in-scope addition, not a scope expansion beyond
    what login start plus the AC-3 the split introduced.

12. **npm** as package manager (Expo CLI's default) — no yarn/pnpm; nothing in this build
    needs workspace features.

## Mechanical facts (settled by the existing API contract, not re-decided)

- `POST /api/auth/login {username, password}` → `200 {token, user:{id,name,username,role}}`;
  `401 {error:{code:"UNAUTHENTICATED",...}}` on bad credentials (`AuthController`,
  `LoginResponse`, `UserSummary`).
- `GET /api/entries?userId=<id>` → `200` array of
  `{id,userId,authorName,entryDate,durationMin,description,createdAt,updatedAt}`
  (`EntryResponse`); empty range/result → `200 []`, never `404` ([05](../design/05-api-conventions.md)).
- `Authorization: Bearer <jwt>` required on every route except `POST /api/auth/login`
  ([05](../design/05-api-conventions.md)); a missing/invalid/expired token → `401` via
  `JwtAuthenticationEntryPoint`, same `ErrorEnvelope` shape as controller-thrown errors.
- No pagination exists yet ([05](../design/05-api-conventions.md)) — the list screen
  renders the full result array as-is.
- Test depth for the UI is **none, automated** per [06b](../design/06b-engineering-decisions.md)
  (deliberate — "the fastest-churning two-way door"); this story's acceptance criteria are
  manual/UI, matching the epic map.

## Deliverables

- `client/` — new Expo (TypeScript) project via `npx create-expo-app` (Expo Router
  template), pinned to whatever stable SDK version the scaffold command resolves at
  implementation time (recorded in `BUILD_STATUS.md`'s off-epic ledger if it introduces
  any surprise, matching how the Java/Spring Boot version moves were recorded in Story 1).
  - `app/_layout.tsx` — root layout; wraps the app in `AuthProvider`; on mount, checks
    stored token and routes to `/login` or `/entries` accordingly.
  - `app/login.tsx` — username/password form → `apiClient` login call → on success,
    `AuthProvider` persists token + user, navigates to `/entries`; on failure, renders the
    error envelope's `message`.
  - `app/entries/index.tsx` — on mount/focus, calls `GET /api/entries?userId=<me.id>`;
    renders `entryDate`, `durationMin`, `description` per row; loading and empty states;
    a logout action that clears the token and returns to `/login`.
  - `lib/apiClient.ts` — the P6 gateway: base URL from `EXPO_PUBLIC_API_URL`, attaches the
    bearer token, parses success/error JSON uniformly, throws typed `ApiError` /
    `UnauthorizedError`.
  - `lib/tokenStorage.ts` — `SecureStore` (native) / `localStorage` (web) behind one
    `getToken`/`setToken`/`clearToken` interface.
  - `lib/auth.tsx` — `AuthContext`/`AuthProvider`: holds `{token, user}`, exposes
    `login()`/`logout()`, wired to `tokenStorage` and to `apiClient`'s 401 callback.
  - `lib/types.ts` — `EntryResponse`, `LoginResponse`, `UserSummary`, `ErrorEnvelope` TS
    types mirroring the backend records.
  - `.env.example` — `EXPO_PUBLIC_API_URL=http://localhost:8080` placeholder; `.env`
    gitignored (consistent with CLAUDE.md's secret-hygiene habit, even though this
    particular value isn't sensitive — `EXPO_PUBLIC_` vars are inlined into the client
    bundle by design).
- `docs/design/07-epic-map.md` — Story 7 split into 7a/7b (done this session).
- `BUILD_STATUS.md` — Story 7 row split into 7a/7b, both ⬜ (done this session).

## Manual verification (no automated UI test per 06b)

- `npx expo start --web` against a locally running backend (`./gradlew bootRun` +
  `docker compose up`): log in with a seeded user → land on the entries list → list shows
  only that user's own entries.
- Log in with wrong password → error message rendered, no navigation.
- Manually clear/corrupt the stored token (or wait out a short test TTL) → next API call →
  redirected to `/login`.
- `npx expo start` (native, Expo Go or a simulator) → same login → list flow works from
  the same code.

## Explicitly out of scope (do not build)

Create/edit/delete UI (Story 7b), team-feed grouping (Story 8), offline support,
pagination, any automated e2e/UI test suite, native app store distribution, any change to
backend endpoints or DTOs.
