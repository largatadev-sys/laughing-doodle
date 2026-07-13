# Tickets: Story 2 — Auth: login + JWT filter + seeded users

`POST /api/auth/login` verifies username + BCrypt password and returns a signed JWT; a
Spring Security filter validates the bearer token on every other route and populates the
security context; 4 users (one admin) are seeded via migration. Source:
[docs/plans/story-2-auth.md](../plans/story-2-auth.md), itself elaborated from
[docs/design/07-epic-map.md](../design/07-epic-map.md) Story 2 via a `/grilling` session.

Work the **frontier**: any ticket whose blockers are all done. Tickets 1 and 2 have no
blockers and can be worked in either order (or in parallel); 3 needs both; 4 needs 3.

---

## Seed users & persistence layer

**What to build:** 4 seeded users (one admin, three members) exist in the database with
BCrypt-hashed placeholder passwords, and a queryable persistence layer can look a user up
by username and expose their id/name/role — the password hash never leaves this layer.
Verifiable entirely on its own via a persistence-layer test; no HTTP surface needed yet.

**Blocked by:** None — can start immediately.

- [x] A migration seeds exactly 4 users: one `admin` role, three `member` role, each with
      a placeholder name/username (not real teammates' names) and a BCrypt hash of a
      single shared, obviously-placeholder password (never a real credential).
- [x] User lookup by username is case-insensitive in effect (queries against the
      already-lowercased stored value — see [02-domain-model.md](../design/02-domain-model.md)
      INV-5), and returns enough to identify the user (id, name, username, role) plus the
      password hash *only* for the internal verification path — nothing outside the
      persistence layer needs the hash itself.
- [x] A lookup for a username that doesn't exist returns an absent/empty result, not an
      exception — the caller (Story 2's login ticket) decides how to translate that.
- [x] A persistence-layer test proves: all 4 seeded users are queryable by username; a
      lookup for a non-existent username comes back empty; the stored password hash is a
      real BCrypt hash (not plaintext).

---

## Standardized error envelope

**What to build:** Any rejection anywhere in the app — bad credentials, forbidden,
not-found, conflict, validation failure, or an unexpected error — responds with the same
`{ "error": { "code", "message", "details" } }` shape and the correct HTTP status, produced
in exactly one place in the app. Verifiable by triggering each error case directly; does
not require the login flow to exist yet.

**Blocked by:** None — can start immediately.

- [x] A distinct, catchable representation exists for each error case named in
      [06b-engineering-decisions.md](../design/06b-engineering-decisions.md)'s taxonomy:
      validation failure (400), unauthenticated (401), forbidden (403), not found (404),
      conflict (409), and an unexpected/internal case (500).
- [x] Raising any of these, from anywhere in the app, produces the standard envelope shape
      with the matching `code` string and HTTP status — and nothing else in the app
      constructs that envelope by hand.
- [x] An unexpected/uncaught failure never leaks raw internal detail (stack trace, SQL,
      exception class name) to the client — it becomes the generic `INTERNAL`/500 case,
      with the real cause logged once, server-side, at that single point.
- [x] A test proves each error case, triggered directly, yields the correct status code
      and envelope shape.

---

## Login issues a JWT

**What to build:** `POST /api/auth/login` — a seeded user's correct username + password
returns `200` with a signed token and that user's public profile (id, name, username,
role). Wrong password and unknown username both return the *identical* generic `401`
error — same status, same code, same message text, so neither response reveals which part
of the credential was wrong. The password hash never appears in the response body.
Demoable directly via curl/Postman against the seeded users.

**Blocked by:** Seed users & persistence layer, Standardized error envelope.

- [x] `POST /api/auth/login` with a seeded user's correct username + password → `200` with
      a signed token and `user{id,name,username,role}` — no `password_hash` field anywhere
      in the response.
- [x] Wrong password for a real username → `401` using the standard error envelope, with
      the exact same `code` and `message` text as the unknown-username case below.
- [x] Unknown username → `401`, same code/message text as the wrong-password case — a
      client cannot distinguish "no such user" from "wrong password" from the response.
- [x] The issued token carries the user's identity and role and has a real, verifiable
      expiry (~7 days out per [04-architecture.md](../design/04-architecture.md) ADR-002)
      — it is not an opaque/unsigned value a client could forge.
- [x] The password hash is never written to a log line by any part of this flow.
- [x] Tests cover: correct credentials → 200 + expected body shape; wrong password → 401;
      unknown username → 401 with identical error body to the wrong-password case.

---

## Protected routes enforce the bearer token

**What to build:** Every route other than login (and the existing health check) requires
a valid `Authorization: Bearer <token>`. A missing, malformed, or expired token is
rejected with `401` before the request reaches any business logic. A valid token — one
actually issued by the login ticket above — is accepted, and the caller's user id and
role become available to the rest of the app without a second database lookup. This is
the enforcement mechanism every later story (create/edit/delete a time entry) builds on.

**Blocked by:** Login issues a JWT.

- [x] A request to any non-login, non-health route with no `Authorization` header → `401`
      using the standard error envelope.
- [x] The same route with a malformed or invalid-signature token → `401`.
- [x] The same route with a well-formed but expired token → `401`.
- [x] The same route with a valid, unexpired token (minted by the login ticket) → the
      request proceeds, and the caller's user id + role are available to downstream code
      without re-querying the database or trusting anything from the request body.
- [x] `GET /api/health` continues to work with **no** token required (unchanged from
      Story 1) — confirming the filter doesn't regress the existing walking skeleton.
- [x] A test exercises the full filter chain (not just the token-parsing logic in
      isolation) against a route that requires authentication, covering: no token,
      invalid token, expired token, and valid token.
