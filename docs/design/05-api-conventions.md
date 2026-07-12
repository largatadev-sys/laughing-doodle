# 05 — API Conventions  `[MVP-THIN]`

Conventions every endpoint obeys, so they are never re-decided per feature. Per-endpoint
contracts are elaborated with each story ([07](07-epic-map.md)). Covers what the MVP
touches; expand after validation.

---

**Base.** `/api` prefix. JSON in, JSON out. HTTPS only. All times ISO-8601; dates `YYYY-MM-DD`.

**Status codes.**
- `GET` (single) → `200`, or `404` if not found.
- `GET` (collection) → `200` with a JSON array; **empty collection is `200 []`, never 404**.
- `POST` (create) → `201` with the created resource.
- `PUT` (update) → `200` with the updated resource.
- `DELETE` → `204`, no body (idempotent — deleting an already-absent id still `204`).
- Validation failure → `400`. Unauthenticated (missing/bad token) → `401`.
  Authenticated but not allowed (e.g. editing another user's entry) → `403`.

**Error envelope.** Every non-2xx returns:
```json
{ "error": { "code": "STRING_CODE", "message": "human-readable", "details": {} } }
```
`code` is a stable machine string (see [06b](06b-engineering-decisions.md) for the taxonomy);
`message` is for humans; `details` is optional (e.g. per-field validation errors).

**Auth.** `Authorization: Bearer <jwt>` on every endpoint except `POST /api/auth/login`.
Identity (user id, role) is taken from the verified token, **never** from the request body.

**Resource naming.** Plural nouns, lowercase: `/api/entries`, `/api/users`, `/api/auth/login`.
No verbs in paths (the HTTP method is the verb).

**Pagination.** Not implemented in v1 (volume is trivial). When needed: query params
`?limit=&offset=`, response stays a bare array. Filtering the entries collection uses
`?from=YYYY-MM-DD&to=YYYY-MM-DD&userId=<optional>`.

**Versioning.** None in v1 (single client, developer controls both ends). If ever needed:
URL prefix `/api/v2`. Deferred.
