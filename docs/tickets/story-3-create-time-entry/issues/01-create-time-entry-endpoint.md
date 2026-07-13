# 01 — Create a time entry (POST /api/entries)

**What to build:** An authenticated Member can `POST /api/entries` with `entryDate`,
`durationMin`, and `description`, and get back the created entry. The entry is always
authored by the caller's own identity (from the JWT), never by anything the request body
claims. Bad input is rejected with a clear validation error; an unauthenticated request is
rejected outright.

Full design context (why each choice was made): [docs/plans/story-3-create-time-entry.md](../../../plans/story-3-create-time-entry.md).
Domain glossary and invariants: [docs/design/02-domain-model.md](../../../design/02-domain-model.md)
(INV-1: entries are always author-stamped from the token). API conventions:
[docs/design/05-api-conventions.md](../../../design/05-api-conventions.md).

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Authenticated `POST /api/entries` with a valid body → `201` with the created entry,
      including the author's name.
- [ ] The created entry's author is always the token's user — even if the request body
      includes a different user id, it is ignored (INV-1).
- [ ] `durationMin <= 0` → `400 VALIDATION_FAILED`.
- [ ] Missing `entryDate` → `400 VALIDATION_FAILED`.
- [ ] Missing or blank/whitespace-only `description` → `400 VALIDATION_FAILED`.
- [ ] No bearer token → `401`.
- [ ] Error responses match the repo's standard envelope shape (`{"error": {"code",
      "message", "details"}}`).
