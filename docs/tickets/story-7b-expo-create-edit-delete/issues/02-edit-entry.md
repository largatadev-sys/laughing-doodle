# 02 — Edit entry

**What to build:** A Member can edit one of their own entries from the my-entries list. Add
`apiClient.updateEntry`, an `UpdateEntryRequest` type, and a `(app)/[id]/edit.tsx` screen
that reuses ticket 01's `EntryForm`, pre-filled from the entry's current values (passed via
route params from the list row — the list already holds the full `EntryResponse` in memory,
so no extra `GET` is needed). Add a per-row Edit affordance on the list, shown only when the
entry belongs to the caller. On success, the list reflects the change without a manual
refresh.

Full design context: [docs/plans/story-7b-expo-create-edit-delete.md](../../../plans/story-7b-expo-create-edit-delete.md)
(decisions 2, 3, 6, 7, 9). API contract: `PUT /api/entries/{id}` — full replace, all three
fields required, `403` (no admin bypass) if not the author, `404` for an unknown id — see
[docs/plans/story-5-edit-own-entry.md](../../../plans/story-5-edit-own-entry.md).

**Blocked by:** 01 — Create entry (reuses its `EntryForm` component).

**Status:** ready-for-agent

- [ ] An Edit affordance appears on each of my own entries in the list, and navigates to
      `(app)/[id]/edit.tsx` pre-filled with that entry's current values.
- [ ] No Edit affordance appears on an entry that isn't mine (verify the guard code path is
      actually exercised, not just assumed unreachable).
- [ ] Submitting a valid edit calls `PUT /api/entries/{id}`, and on success returns to the
      list, where the change is visible without a manual refresh.
- [ ] Submitting with a missing/invalid field shows inline error(s), same behavior as create.
- [ ] A `403`/`404` surfaced mid-edit (entry deleted or reassigned by another session since
      the list loaded) is shown as an inline error, not silently swallowed.
- [ ] Works identically in Expo native dev, from the same code.
