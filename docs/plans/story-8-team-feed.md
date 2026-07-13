# Story 8 — Team feed (shared visibility view)

**Status.** Planned, not yet built. Immutable once implementation lands — see
[BUILD_STATUS.md](../../BUILD_STATUS.md) for live status.

**Source.** [07-epic-map.md](../design/07-epic-map.md) Story 8, elaborated through a
`/grill-with-docs` session on 2026-07-14. This file records *why* each choice was made; the
epic map records *what* is required. Per the developer's steer for this session,
implementation-detail decisions below were made by the agent and recorded here for review
before implementation; only the one decision that reshapes the client's navigation structure
was put to the developer directly.

---

## Scope

A read-only, whole-team view of TimeEntries for a selected week, grouped by day, in the
existing Expo client (`client/`). Builds on 7a/7b's shipped scaffold, `apiClient`, `AuthContext`,
the `(app)` route group, and the `EntryResponse` shape (already carries `authorName`).
Consumes Story 4's `GET /api/entries` with no backend changes. No charts/reports, no
per-project rollups, no offline support, no pagination.

## Decision put to the developer directly (and why)

**Navigation to the Team Feed screen.** Story 8 adds a second top-level screen. Two shapes
were on the table:

- *Bottom tabs* — restructure `(app)/_layout.tsx` from a single `Stack` into a `Tab`
  navigator, with "My Entries" and "Team" as peer tabs. More conventional IA for two
  equally-weighted screens, but a real structural change made ahead of knowing whether a
  3rd/4th top-level section is ever coming.
- *Stack header link* — keep the single `Stack` as-is; add a "Team" link in My Entries'
  header (same pattern as the existing "+ New" link), pushing to `(app)/team.tsx`. Zero new
  navigator type, minimal diff, consistent with 7a/7b's restraint calls.

This reshapes navigation for every future screen, so it was raised directly rather than
decided by the agent. **Confirmed: Stack header link.** `(app)/_layout.tsx` stays a single
`Stack`; "My Entries" remains the default landing screen; Team Feed is a pushed screen
reached via a header link, with the default native back button returning to My Entries
(same as `new.tsx`/`edit.tsx` already do — no custom back-link code needed).

## Decisions made this session (and why)

1. **No backend change — reuse `GET /api/entries?from=&to=` with no `userId`.** Story 4
   already returns all members' entries for a date range, each with `authorName`, sorted
   `entryDate ASC, id ASC` server-side (`TimeEntryRepository.findByFilters`). Grouping by
   day is a handful of entries at this scale (4 users) and is done client-side in plain JS —
   no new endpoint, no aggregation query. This is the lower-risk, fully reversible option
   (the endpoint is unchanged either way) and matches the existing pattern of 7a/7b doing
   all client-only stories with zero backend touches.

2. **Grouping is by `entry_date` only** (epic AC-1's literal requirement), rendered with
   React Native's built-in `SectionList` (no new dependency) — one section per date, in the
   order the server already returns (ascending). No secondary sort within a day (e.g. by
   author) — the server's `id ASC` tiebreak is stable and sufficient; adding a client-side
   re-sort would be complexity with no acceptance criterion asking for it.

3. **No day or member totals/rollups.** The epic's scope boundary is explicit ("no
   charts/reports; no per-project rollups"). A day is just a header over its entries — no
   summed minutes, no per-member subtotal. Keeps the surface a plain list, not a report.

4. **"Week" = Monday–Sunday, computed client-side from `today`, defaulting to the week
   containing today.** Not a backend concept — `from`/`to` are plain `LocalDate` query
   params either way. Monday-start matches a conventional work week; this is a two-way door
   (a UI framing choice, not a persisted invariant), so it's recorded here rather than in
   the glossary or as an ADR — it fails all three of `/domain-modeling`'s ADR criteria
   (easily reversed, unsurprising, no real competing trade-off worth recording).

5. **Prev/next week navigation, no date picker.** A small row under the header with `‹`/`›`
   controls shifts the selected week by 7 days and refetches; no calendar/picker UI for MVP
   (matches the Floor dial — this is a side tool, not a scheduling app). Selection resets to
   the current week each time the screen regains focus, via the same `useFocusEffect`
   refetch-on-focus pattern 7a/7b already use for the my-entries list — no extra
   state-persistence machinery to remember a previously-viewed week across navigations.

6. **Extract a shared `EntryRow` component** (`client/src/components/EntryRow.tsx`), used by
   both the existing my-entries `FlatList` (`(app)/index.tsx`) and the new Team Feed
   `SectionList`. Today the row markup/styles/edit-delete-confirm logic live inline in
   `index.tsx`; duplicating that into `team.tsx` would diverge two copies of the same row on
   day one. `EntryRow` takes the `EntryResponse`, an optional `showAuthor` flag (my-entries
   omits the author name since every row is mine; Team Feed always shows it), and optional
   `onEdit`/`onDelete` callbacks — omitted entirely (not just hidden) when the caller isn't
   the entry's owner. This is a mechanical refactor of already-shipped 7b code, not a
   behavior change to `index.tsx` — its rendered output is unchanged.

7. **Edit/Delete on Team Feed rows are gated on `entry.userId === session.user.id`** — the
   exact ownership-gate pattern 7b decision #9 already established for the my-entries list,
   and that decision explicitly flagged this as the reason the gate was added defensively
   ("protects this row-rendering logic if Story 8's team feed ever reuses it against a
   mixed-author list"). Tapping Edit on my own row navigates to the existing
   `(app)/[id]/edit.tsx` route (no new edit screen); tapping Delete reuses the existing
   platform-branched confirm + `apiClient.deleteEntry` + local list-state removal, adapted to
   remove the entry from its section rather than a flat array.

8. **`team.tsx`'s header is a local `Stack.Screen` override** (`title: 'Team Feed'`), the
   same technique `new.tsx`/`edit.tsx` already use — the shared `(app)/_layout.tsx`
   `screenOptions` title ("My Entries") is overridden per-screen, and Expo Router supplies
   the back button automatically for a pushed screen with no custom `headerLeft`, so no new
   back-navigation code is needed. `index.tsx` gains a new "Team" link in `headerRight`,
   alongside the existing "Log out" link (its `headerLeft` stays "+ New").

9. **No new `apiClient` method.** `apiClient.listEntries({ from, to }, token)` (omitting
   `userId`) already matches this exact call shape — Story 4's method was written generic
   enough for this. Adding a `listTeamEntries` wrapper would be a one-line pass-through with
   no behavior of its own.

10. **No special handling for a 403/404 surfaced mid-action on someone else's row** (e.g. an
    entry deleted or reassigned between load and a delete tap) beyond what already exists —
    same self-inflicted, low-stakes race 7b decision #10 already accepted for the my-entries
    screen, now just reachable through a second entry point onto the same `EntryRow`/delete
    code path.

## Mechanical facts (settled by the existing API contract, not re-decided)

- `GET /api/entries?from=&to=` (no `userId`) → `200` array of every member's entries in
  range, each with `authorName`, sorted `entryDate ASC, id ASC`; empty range → `200 []`
  (never `404`) — see [story-4 plan](story-4-list-entries.md) / Story 4 AC-1/AC-2.
- `PUT /api/entries/{id}` / `DELETE /api/entries/{id}` — same contracts as 7b (403 no admin
  bypass, 404 checked before ownership, error envelope shape) — unchanged by this story.
- `EntryResponse` shape is unchanged — no client type changes needed.

## Deliverables

- `client/src/components/EntryRow.tsx` — shared row (extracted from `index.tsx`): date/
  duration/description, optional author name, optional Edit/Delete actions.
- `client/src/app/(app)/index.tsx` — refactored to use `EntryRow` (`showAuthor={false}`,
  always passes edit/delete since every row is the caller's own); adds the "Team" header
  link.
- `client/src/app/(app)/team.tsx` — new screen: week selector (prev/next + current-week
  default), `SectionList` grouped by `entryDate`, each row rendered via `EntryRow`
  (`showAuthor={true}`, edit/delete only when `entry.userId === session.user.id`).
- `docs/tickets/story-8-team-feed/issues/` — two tracer-bullet tickets (below).

## Ticket breakdown

1. **01 — Team feed read-only view** — `(app)/team.tsx`, the week selector (prev/next +
   default-to-current-week), the `SectionList` grouped by `entryDate` fetching via
   `apiClient.listEntries({ from, to }, token)`, and the "Team" link from `(app)/index.tsx`.
   Renders every row read-only (no edit/delete yet — that's ticket 02). Blocked by: none (all
   dependencies already shipped by 7a/7b).
2. **02 — Ownership-gated edit/delete on Team Feed rows** — extract `EntryRow` from
   `index.tsx`, refactor `index.tsx` to use it, wire `team.tsx`'s rows through the same
   component with the `entry.userId === session.user.id` gate, reusing the existing
   platform-branched delete confirm. Blocked by: 01 (needs the Team Feed screen and its
   `SectionList` row-rendering in place before extracting a shared row component around it).

## Manual verification (no automated UI test per 06b)

- Open Team Feed → see entries from more than one user for the current week, grouped under
  date headers, in date order.
- Every row shows the author's name; only rows belonging to me show Edit/Delete.
- Prev/next week controls move the range by 7 days and refetch; a week with no entries shows
  an empty state, not an error.
- Edit one of my own entries from Team Feed → lands on the existing edit screen pre-filled →
  saving returns to Team Feed with the change reflected (refetch on focus).
- Delete one of my own entries from Team Feed (confirm the prompt) → it disappears from its
  section immediately, with no manual refresh.
- Confirm no Edit/Delete affordance renders on any row that isn't mine.
- Confirm My Entries (`index.tsx`) still behaves identically after the `EntryRow` extraction
  — same fields, same Edit/Delete gating, same delete-confirm flow.
- Repeat the above in Expo native dev (Expo Go or simulator), same code.

## Explicitly out of scope (do not build)

Charts/reports, per-project rollups, per-day or per-member totals, a calendar/date-picker
UI, pagination, grouping/aggregation on the backend, any automated e2e/UI test suite, admin
bypass of ownership, any change to backend endpoints, DTOs, or validation rules, persisting a
previously-viewed week across navigations, bottom-tab navigation (deferred per the
navigation decision above — revisit if a 3rd/4th top-level screen is ever added and two
peer entry points via header links starts feeling cramped).
