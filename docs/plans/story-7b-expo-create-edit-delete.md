# Story 7b — Expo client: create/edit/delete my entries

**Status.** Planned, not yet built. Immutable once implementation lands — see
[BUILD_STATUS.md](../../BUILD_STATUS.md) for live status.

**Source.** [07-epic-map.md](../design/07-epic-map.md) Story 7b, elaborated through a
`/grill-with-docs` session on 2026-07-13. This file records *why* each choice was made;
the epic map records *what* is required. Per the developer's steer for this session,
implementation-detail decisions below were made by the agent and recorded here for review
before implementation; only the one decision that could touch a CLAUDE.md stop-rule item
(schema/backend changes for concurrency protection) was put to the developer directly.

---

## Scope

A form to create a TimeEntry, and edit/delete controls on entries the caller authored, in
the existing Expo client (`client/`). Builds directly on 7a's shipped scaffold, `apiClient`,
`tokenStorage`, `AuthContext`, and the `(app)` route group. No team-feed grouping (Story 8),
no offline support.

## Decisions made this session (and why)

1. **Lost-update protection stays deferred — no backend or schema change.** Story 5's plan
   (decision #4) deliberately shipped `PUT /api/entries/{id}` with no `version` column or
   `If-Match`/`409` guard, flagging "revisit before/at Story 7" once a real multi-session
   client existed. 7b is that point, so this was put to the developer directly rather than
   decided by the agent — a `version` column is a schema/migration change, and even the
   lighter compare-and-swap-on-`updatedAt` option touches `EntryService`/`UpdateEntryRequest`
   outside 7b's declared client-only scope, reopening Story 5's shipped, "immutable" plan.
   **Confirmed: keep deferring.** The risk stays low (same-account-only race, always
   recoverable by re-editing, no cross-user exposure — INV-2 is untouched). Recorded here as
   an explicit, non-silent re-defer with a concrete trigger: revisit if a real collision is
   ever reported, or before Story 9's deploy if multi-device/multi-tab usage in practice
   turns out to be more than theoretical.

2. **`apiClient.ts` gets three new methods** — `createEntry(request, token)`,
   `updateEntry(id, request, token)`, `deleteEntry(id, token)` — built on the existing
   private generic `request<T>()` helper, same as `login`/`listEntries`. No new gateway
   pattern; this is 7a decision #5's P6 gateway restated, not re-decided. `deleteEntry`
   relies on `request<T>` already returning `undefined` on `204` (verified — no change
   needed there).

3. **Two new hand-written TS types** in `types.ts` — `CreateEntryRequest` and
   `UpdateEntryRequest`, each `{ entryDate: string; durationMin: number; description: string }`
   — mirroring the backend's `CreateEntryRequest`/`UpdateEntryRequest` records field-for-field.
   Kept as two distinct types (not one shared type) for the same reason Story 5 decision #3
   kept the backend DTOs distinct: they describe what two different endpoints accept, not a
   data shape in the abstract, and have no obligation to stay identical going forward. No
   codegen — same restraint call as 7a decision #9.

4. **One shared `EntryForm` component** (`client/src/components/EntryForm.tsx`), used by
   both the create and edit screens, rather than two near-duplicate forms. It owns the
   three-field `useState` form state, a client-side pre-submit check that mirrors the
   backend's rules (`entryDate` present, `durationMin > 0`, `description` non-blank) purely
   to avoid an obviously-doomed round trip, and renders the submit error using the response's
   `error.details` map when present (one message per field) — the backend already returns
   field-keyed `details` on `400 VALIDATION_FAILED`, so using it is free and more useful than
   `login.tsx`'s single generic-message pattern. Server validation remains authoritative;
   the client check is a UX nicety, not a second source of truth.

5. **`description` input gets a client-side `maxLength={500}`**, matching the
   `time_entries.description varchar(500)` column, even though `EntryService.validate()`
   currently enforces no upper bound server-side (confirmed by reading the code — only
   required/non-blank is checked). This is a pure client-side UX guard against a latent gap;
   no backend change, and not urgent enough to flag on the off-epic ledger — the DB column
   would simply reject an over-length insert if this guard were ever bypassed (e.g. a future
   non-client caller), which is the DB doing its job, not a bug this story needs to fix.

6. **New routes live under the existing `(app)` group**: `(app)/new.tsx` (create) and
   `(app)/[id]/edit.tsx` (edit), matching 7a's *actual* shipped layout (the `(app)` group
   holding every authenticated screen), not the original 7a plan text's now-superseded
   `app/entries/...` path. Reached via `router.push()` from the list screen; on success,
   `router.back()` returns to the list. No screen-specific header override — the group's
   shared `(app)/_layout.tsx` header ("My Entries" title, "Log out" action) stays as-is on
   the form screens too, matching every other screen in the group; revisit only if it looks
   wrong once built (a visual call, not an architectural one).

7. **No new state-management/data-fetching library, and no manual refresh plumbing.**
   The list screen's existing `useFocusEffect` refetch-on-focus (from 7a) already satisfies
   AC-1/AC-2 ("appears/reflects... without a manual refresh") for free, because create/edit
   are separate routes reached by push/pop — returning to the list is itself a focus event.
   This reaffirms 7a decision #4 (restraint; no Redux/Zustand/React Query) rather than
   re-opening it.

8. **Delete is inline on the list row, not a separate screen** — a single irreversible
   action doesn't need a form. Gated behind a platform-branched confirm (`Alert.alert` on
   native, `window.confirm` on web), mirroring `tokenStorage.ts`'s existing `Platform.OS`
   branch — no new dependency. On a successful `204`, the row is removed from local list
   state directly (filter by `id`) rather than triggering a full refetch, since delete has
   no response body to reconcile against anyway and there's no navigation event to hang a
   `useFocusEffect` refetch on.

9. **Edit/Delete controls are gated on `entry.userId === session.user.id`.** Currently a
   no-op given the list already only ever shows the caller's own entries (7a's
   `?userId=<me>` filter), but added defensively: it's the literal wording of epic-map AC-4
   ("no edit/delete affordance... for another user's entry"), it's cheap, and it protects
   this row-rendering logic if Story 8's team feed ever reuses it against a mixed-author list.

10. **No special handling for a 403/404 surfaced mid-action** (entry deleted or reassigned
    by another session between list-load and the edit/delete tap) — surfaced inline via the
    existing `ApiError` message, same as any other failure, and resolved by the user
    navigating away and back (which refetches). Not silently swallowed, but not built out
    beyond what `apiClient`'s existing error types already give for free. Consistent with
    decision 1 — this is the same self-inflicted, low-stakes race, just surfaced through a
    different endpoint.

## Mechanical facts (settled by the existing API contract, not re-decided)

- `POST /api/entries {entryDate, durationMin, description}` → `201 EntryResponse`;
  `400 VALIDATION_FAILED` with per-field `details` on bad input; `401` with no/invalid token.
  No `userId` in the body — server stamps it from the token (INV-1), silently ignoring any
  client-sent value.
- `PUT /api/entries/{id} {entryDate, durationMin, description}` → `200 EntryResponse` (full
  replace, all three fields required every time — no partial-update semantics); `403
  FORBIDDEN` ("Only the author may edit this entry") if the caller isn't the author, **no
  admin bypass**; `404 NOT_FOUND` ("Entry not found") for an unknown id, checked *before*
  ownership; same `400`/`401` shapes as create.
- `DELETE /api/entries/{id}` → `204` on success **and** `204` if the id was already absent
  (idempotent by design — same response either way, the client cannot distinguish them from
  the response alone); `403 FORBIDDEN` ("Only the author may delete this entry") if the
  entry exists but isn't the caller's, **no admin bypass**; `401` with no/invalid token.
- Validation rules (shared by create/edit, from `EntryService.validate()`): `entryDate`
  required; `durationMin` required and `> 0` (no upper bound); `description` required and
  non-blank (whitespace-only rejected). All failing fields are returned together in one
  `400`'s `details` map, not fail-fast.
- Error envelope on every non-2xx: `{"error":{"code","message","details?"}}` — already
  parsed uniformly by `apiClient`'s existing error handling; `UnauthorizedError` on `401`
  already triggers the existing global logout-and-redirect path from 7a, so no new 401
  handling is needed for these three endpoints.
- `EntryResponse` field shape is unchanged from 7a/Story 4 — no client type changes needed
  for the list screen itself.

## Deliverables

- `client/src/lib/types.ts` — add `CreateEntryRequest`, `UpdateEntryRequest`.
- `client/src/lib/apiClient.ts` — add `createEntry`, `updateEntry`, `deleteEntry`.
- `client/src/components/EntryForm.tsx` — shared 3-field form (entryDate, durationMin,
  description), client-side pre-submit checks, field-level error rendering from
  `error.details`, disabled-while-submitting submit button (mirrors `login.tsx`'s pattern).
- `client/src/app/(app)/new.tsx` — create screen: renders `EntryForm`, calls
  `apiClient.createEntry`, `router.back()` on success.
- `client/src/app/(app)/[id]/edit.tsx` — edit screen: loads the entry's current values
  (passed via route params from the list row — no extra `GET` needed, the list already has
  the full `EntryResponse` in memory), renders `EntryForm` pre-filled, calls
  `apiClient.updateEntry`, `router.back()` on success.
- `client/src/app/(app)/index.tsx` — add a create affordance (e.g. a header/list action
  navigating to `/new`), and per-row Edit/Delete actions gated on `entry.userId ===
  session.user.id`; Delete wired to the platform-branched confirm + inline
  `apiClient.deleteEntry` + local state filter.
- `docs/tickets/story-7b-expo-create-edit-delete/issues/` — three tracer-bullet tickets
  (below).

## Ticket breakdown

1. **01 — Create entry** — `apiClient.createEntry`, `CreateEntryRequest` type, the shared
   `EntryForm` component (built here since create is its first consumer), the `new.tsx`
   screen, and the list's create affordance. Blocked by: none (all its dependencies are
   already shipped by 7a).
2. **02 — Edit entry** — `apiClient.updateEntry`, `UpdateEntryRequest` type, `[id]/edit.tsx`
   reusing `EntryForm` pre-filled, the list's per-row Edit affordance (with the ownership
   gate). Blocked by: 01 (reuses its `EntryForm`).
3. **03 — Delete entry** — `apiClient.deleteEntry`, the list's per-row Delete affordance
   (same ownership gate), platform-branched confirm, local list-state removal. Blocked by:
   02 (shares the per-row actions area being added there, to avoid two tickets editing the
   same row-layout code independently).

## Manual verification (no automated UI test per 06b)

- Create an entry via the form → land back on the list → the new entry appears without a
  manual refresh (AC-1).
- Submit the create form with a missing/invalid field → inline field error(s) shown, no
  navigation, nothing created.
- Edit one of my entries → land back on the list → the change is reflected without a manual
  refresh (AC-2).
- Delete one of my entries (confirm the prompt) → it disappears from the list immediately,
  no manual refresh (AC-3).
- Cancel a delete confirm → entry remains.
- Confirm no Edit/Delete affordance renders on any row that isn't mine (AC-4) — trivially
  true today since the list is self-only, but verify the guard code path is actually exercised
  (e.g. temporarily point the list at another user's id in dev tools) rather than just
  trusting it never fires.
- Repeat the create/edit/delete flow in Expo native dev (Expo Go or simulator), same code.

**Implementation notes (added post-build).** The above manual click-through was not
exercised live during this story's implementation — the local backend couldn't boot
against Postgres (`password authentication failed for user "timesheet"`, a pre-existing
mismatch between `.env`'s `DATABASE_URL`-embedded credential and `docker-compose.yml`'s
separate `DATABASE_PASSWORD`/`DATABASE_USER` vars, unrelated to this story; recreating the
Postgres container/volume did not resolve it, ruling out stale volume state as the cause).
Verification instead relied on: a clean `tsc --noEmit`, a clean `expo lint`, a successful
`expo export --platform web` (proves the app, including the new `/new` and `/[id]/edit`
routes, actually bundles), and a two-agent code review (Standards + Spec axes) against this
plan and the three tickets. **The manual verification steps above should still be run
before considering Story 7b done** once the local `.env`/Postgres credential mismatch is
resolved.

## Explicitly out of scope (do not build)

Team-feed grouping (Story 8), offline support, pagination, any automated e2e/UI test suite,
admin bypass of ownership, optimistic-concurrency/lost-update protection (deferred per
decision 1), any change to backend endpoints, DTOs, or validation rules (including the
`description` max-length gap noted in decision 5 — mitigated client-side only).
