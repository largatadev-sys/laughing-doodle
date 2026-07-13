# 01 — Edit own entry (ownership enforced)

**What to build:** A logged-in Member can edit one of their own time entries (date,
duration, description) and see the change reflected immediately. If they try to edit an
entry they don't own, the request is refused and the entry is untouched. Editing a
non-existent entry is reported clearly rather than silently succeeding or failing oddly.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Author edits their own entry → `200`, response reflects the new values, `updatedAt` changes
- [ ] A different authenticated user attempts to edit someone else's entry → `403 FORBIDDEN`, entry left unchanged
- [ ] Editing an unknown entry id → `404 NOT_FOUND`
- [ ] Invalid edit (non-positive duration, missing/blank description, missing date) → `400 VALIDATION_FAILED`
- [ ] No/invalid auth token → `401`

(Full decision rationale already recorded in
[docs/plans/story-5-edit-own-entry.md](../../../plans/story-5-edit-own-entry.md) — this
ticket references it rather than duplicating it.)
