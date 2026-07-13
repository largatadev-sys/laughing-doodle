# 02 — Ownership-gated edit/delete on Team Feed rows

**What to build:** Extract a shared `EntryRow` component
(`client/src/components/EntryRow.tsx`) from `(app)/index.tsx`'s current inline row markup,
taking an `EntryResponse`, an optional `showAuthor` flag, and optional `onEdit`/`onDelete`
callbacks (omitted, not just hidden, when the caller isn't the entry's owner). Refactor
`index.tsx` to render its `FlatList` rows through `EntryRow` (`showAuthor={false}`, always
passing edit/delete since every row there is already the caller's own) with no change in its
behavior. Wire ticket 01's `team.tsx` `SectionList` rows through the same `EntryRow`
(`showAuthor={true}`, edit/delete passed only when `entry.userId === session.user.id`),
reusing the existing platform-branched delete confirm (`Alert.alert` / `window.confirm`) and
removing a deleted row from its section in local state.

Full design context: [docs/plans/story-8-team-feed.md](../../../plans/story-8-team-feed.md)
(decisions 6, 7, 10). Ownership-gate precedent:
[docs/plans/story-7b-expo-create-edit-delete.md](../../../plans/story-7b-expo-create-edit-delete.md)
decision 9. API contracts unchanged from 7b: `PUT /api/entries/{id}` / `DELETE
/api/entries/{id}` (403 no admin bypass, 404 checked before ownership).

**Blocked by:** 01 — Team feed read-only view (needs the `SectionList` row-rendering in
place before extracting a shared row component around it).

**Status:** ready-for-agent

- [ ] `EntryRow` is used by both `index.tsx` and `team.tsx` — no duplicated row markup.
- [ ] `index.tsx` behaves identically after the refactor: same fields shown, same
      always-on Edit/Delete, same delete-confirm flow, same local-state removal on success.
- [ ] On Team Feed, Edit/Delete appear only on rows where `entry.userId ===
      session.user.id`; no affordance appears on any other row (verify the guard code path
      is actually exercised, e.g. by viewing a week with another user's entries, not just
      trusting it never fires).
- [ ] Tapping Edit on my own Team Feed row navigates to the existing `(app)/[id]/edit.tsx`,
      pre-filled, and returns to Team Feed with the change reflected (refetch on focus).
- [ ] Tapping Delete on my own Team Feed row shows the same platform-branched confirm as
      `index.tsx`; confirming removes it from its section immediately, with no manual
      refresh; cancelling leaves it untouched.
- [ ] A `403`/`404` surfaced mid-action (entry deleted/reassigned since the feed loaded) is
      shown as an inline error, not silently swallowed.
- [ ] Works identically in Expo native dev, from the same code.
