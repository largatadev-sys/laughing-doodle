# 01 — Delete own entry (ownership enforced)

**What to build:** A logged-in Member can delete one of their own time entries; it's gone
immediately. If they try to delete an entry they don't own, the request is refused and the
entry is left untouched. Deleting an entry that doesn't exist — or was already deleted — is
treated as already-done, not an error.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Author deletes their own entry → `204`, entry no longer retrievable
- [ ] A different authenticated user attempts to delete someone else's entry →
      `403 FORBIDDEN`, entry left unchanged and still present
- [ ] Deleting an unknown or already-deleted entry id → `204` (idempotent, no error)
- [ ] No/invalid auth token → `401`

(Full decision rationale already recorded in
[docs/plans/story-6-delete-own-entry.md](../../../plans/story-6-delete-own-entry.md) — this
ticket references it rather than duplicating it.)
