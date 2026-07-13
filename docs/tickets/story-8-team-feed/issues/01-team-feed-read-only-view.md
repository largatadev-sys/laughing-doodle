# 01 — Team feed read-only view

**What to build:** A new `(app)/team.tsx` screen showing the whole team's TimeEntries for a
selected week, grouped by day. Add a week selector (prev/next controls, defaulting to the
week — Monday–Sunday — containing today) that fetches via `apiClient.listEntries({ from, to
}, token)` (no `userId`, so every member's entries come back). Render the results in a
`SectionList` with one section per `entryDate`, in the order the API already returns
(`entryDate ASC, id ASC`). Every row shows the author's name (`entry.authorName`), duration,
and description — read-only for now, no Edit/Delete affordances (that's ticket 02). Add a
"Team" link to `(app)/index.tsx`'s header that navigates here.

Full design context: [docs/plans/story-8-team-feed.md](../../../plans/story-8-team-feed.md)
(decisions 1–5, 8, 9, and the navigation decision). API contract:
`GET /api/entries?from=&to=` — see
[docs/plans/story-4-list-entries.md](../../../plans/story-4-list-entries.md).

**Blocked by:** None — all dependencies (`apiClient.listEntries`, `AuthContext`, the `(app)`
route group) already shipped in 7a/7b.

**Status:** ready-for-agent

- [ ] A "Team" link in `(app)/index.tsx`'s header navigates to `(app)/team.tsx`, which gets
      the default native back button (no custom back-link code, matching `new.tsx`/`edit.tsx`).
- [ ] Team Feed defaults to the current week (Monday–Sunday containing today) on load.
- [ ] Entries from more than one user appear, grouped into sections by `entryDate`, sections
      in ascending date order.
- [ ] Every row shows the author's name, duration, and description.
- [ ] Prev/next controls shift the selected week by 7 days and refetch for the new range.
- [ ] A week with no entries shows an empty state (e.g. "No entries this week."), not an
      error and not a `404` (the API returns `200 []`).
- [ ] No bare `fetch` — the call goes through `apiClient.listEntries`.
- [ ] Works identically in Expo native dev, from the same code.
