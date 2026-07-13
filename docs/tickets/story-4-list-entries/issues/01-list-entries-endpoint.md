# 01 — List entries (GET /api/entries)

**What to build:** An authenticated Member can `GET /api/entries` and see every team
member's logged time entries — not just their own — each carrying the author's name.
Optional `from`, `to`, and `userId` query params narrow the result independently; any of
them may be omitted (omitting all three returns every entry). An empty or non-matching
result — a genuinely empty range, an inverted `from`/`to` range, or a `userId` with no
entries — is always `200 []`, never `404`. Malformed filter values are rejected as a
`400 VALIDATION_FAILED` rather than leaking as a `500`. An unauthenticated request is
rejected outright.

Full design context (why each choice was made): [docs/plans/story-4-list-entries.md](../../../plans/story-4-list-entries.md).
Domain glossary and invariants: [docs/design/02-domain-model.md](../../../design/02-domain-model.md)
(INV-2: shared visibility — any Member may read any entry). API conventions:
[docs/design/05-api-conventions.md](../../../design/05-api-conventions.md).

**Blocked by:** None — can start immediately.

**Status:** ready-for-human

- [x] Authenticated `GET /api/entries` with no filters → `200` array containing entries
      from *all* seeded users, not just the caller, each with `authorName`.
- [x] `from`, `to`, and `userId` may each be omitted independently; omitting all three
      returns every entry.
- [x] `from`/`to` filter by `entry_date` (inclusive); `userId` filters to one author;
      filters compose together.
- [x] A range with no matching entries — including an inverted range where `from` is
      after `to` — → `200 []`, never `400`/`404`.
- [x] A `userId` with no matching entries (or no matching user at all) → `200 []`, not an
      error — the filter is not existence-checked against `users`.
- [x] Malformed `from`, `to`, or `userId` (a value that can't be parsed as a date/number)
      → `400 VALIDATION_FAILED` using the standard error envelope.
- [x] Results are ordered deterministically (`entry_date` ascending, then `id`).
- [x] No bearer token → `401`.
- [x] Error responses match the repo's standard envelope shape (`{"error": {"code",
      "message", "details"}}`).
