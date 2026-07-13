# 03 — Delete entry

**What to build:** A Member can delete one of their own entries directly from the
my-entries list. Add `apiClient.deleteEntry` and a per-row Delete affordance, shown only
when the entry belongs to the caller (same ownership gate as ticket 02's Edit affordance).
Deleting asks for confirmation first (`Alert.alert` on native, `window.confirm` on web —
mirroring `tokenStorage.ts`'s existing platform branch, no new dependency). On confirmed
success, the row is removed from local list state directly — no navigation and no
`useFocusEffect` refetch involved, since delete is a same-screen action with no response
body to reconcile against.

Full design context: [docs/plans/story-7b-expo-create-edit-delete.md](../../../plans/story-7b-expo-create-edit-delete.md)
(decisions 8–10). API contract: `DELETE /api/entries/{id}` — `204` on success **and** on an
already-absent id (idempotent, indistinguishable from the response alone), `403` (no admin
bypass) if the entry exists but isn't mine — see
[docs/plans/story-6-delete-own-entry.md](../../../plans/story-6-delete-own-entry.md).

**Blocked by:** 02 — Edit entry (shares the per-row actions area introduced there, to avoid
two tickets independently editing the same row-layout code).

**Status:** ready-for-agent

- [ ] A Delete affordance appears on each of my own entries in the list; none appears on an
      entry that isn't mine.
- [ ] Tapping Delete shows a confirmation prompt before anything happens.
- [ ] Cancelling the confirmation leaves the entry untouched.
- [ ] Confirming calls `DELETE /api/entries/{id}`, and on success the entry disappears from
      the list immediately, with no manual refresh.
- [ ] A `403` surfaced mid-delete (entry reassigned by another session since the list
      loaded) is shown as an inline error, not silently swallowed, and the row is not removed.
- [ ] Works identically in Expo native dev, from the same code.
