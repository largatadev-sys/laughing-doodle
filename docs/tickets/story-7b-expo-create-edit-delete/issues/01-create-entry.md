# 01 — Create entry

**What to build:** A Member can create a new TimeEntry from the Expo client. Add
`apiClient.createEntry`, a `CreateEntryRequest` type, a shared `EntryForm` component (three
fields: entry date, duration in minutes, description), a `(app)/new.tsx` screen that renders
it and calls the API, and a create affordance on the my-entries list that navigates there.
On success the new entry appears in the list without a manual refresh (the existing
`useFocusEffect` refetch on the list screen already covers this once `new.tsx` is a
separate route reached by push/pop). Invalid input shows inline field errors and does not
navigate or create anything.

`EntryForm` is built here as the first of two consumers (ticket 02 reuses it for edit) — see
[docs/plans/story-7b-expo-create-edit-delete.md](../../../plans/story-7b-expo-create-edit-delete.md)
(decisions 2–7).

Full design context: [docs/plans/story-7b-expo-create-edit-delete.md](../../../plans/story-7b-expo-create-edit-delete.md).
Epic context: [docs/design/07-epic-map.md](../../../design/07-epic-map.md) Story 7b. API
contract: `POST /api/entries` in [docs/design/05-api-conventions.md](../../../design/05-api-conventions.md).

**Blocked by:** None — all dependencies (apiClient, AuthContext, the `(app)` route group)
already shipped in Story 7a.

**Status:** ready-for-agent

- [ ] A create affordance is visible on the my-entries list and navigates to `(app)/new.tsx`.
- [ ] Submitting valid values (entry date, duration > 0, non-blank description) calls
      `POST /api/entries`, and on success returns to the list, where the new entry is visible
      without a manual refresh.
- [ ] Submitting with a missing/invalid field shows inline error(s) for the specific
      field(s) (using the response's `error.details` map when present) and does not navigate
      away or create anything.
- [ ] The description input enforces a client-side 500-character max (matching the backend
      column), and duration must be a positive whole number.
- [ ] No bare `fetch` — the create call goes through `apiClient.createEntry`.
- [ ] Works identically in Expo native dev, from the same code.
