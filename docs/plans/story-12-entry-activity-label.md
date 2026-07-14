# Story 12 — Entry activity label: "logged / edited · when"

**Epic:** 2 (Largata UX & mobile visual identity) · **Status:** planned + ticketed, implementation pending
**Scope:** client only. **No schema / API / auth / INV-2 change.** `EntryResponse` already returns
both `createdAt` and `updatedAt` ([types.ts](../../client/src/lib/types.ts)); nothing on the backend
moves.

## Problem

The activity caption on an entry card (`EntryCard`, shown on the day view and the home feed) reads
`logged · <relativeTime(createdAt)>`. Two shortcomings surfaced while looking at the day view:

1. It only ever says **"logged"** — even after an entry has been **edited**, so you can't tell a
   freshly-logged row from one that was changed later.
2. `relativeTime` degrades to `"3d ago"` between 1–7 days, then to a date. On a **timesheet**, "3d
   ago" is the wrong unit of information — you want the actual date, not to do subtraction in your
   head. (This is also where the original timezone bug lived; see "Relationship to in-flight work".)

## Goal (the rule, in the developer's words)

- We record `created_at` and `updated_at`. **If not yet edited, say "logged"; if edited, say
  "edited"** and key off `updated_at`.
- **If that action happened on the same calendar day as the viewer, show relative** ("22m ago" /
  "5h ago"). **If it did not, show the date — never "days ago".**
- **Sort entries by `created_at`, not `updated_at`** — an edit must not reorder the list.

## Design decisions (frontend-design)

This is a **caption refinement, not a redesign**, and the deliberate design position is *restraint*.
The card's signature element is already the duration-as-load tally bar (the Largata direction —
[[project_largata_ux_direction]]); loading visual weight onto a secondary metadata line would fight
it. So:

- **D1 — Copy: "edited" (CONFIRMED 2026-07-14).** The action button says **Edit**; an interface
  keeps a verb consistent through its whole flow (Edit → "edited"), the way "Publish" produces
  "Published". "edited" is also what the *person* did, in their own words, vs the systemy "updated".
- **D2 — No visual decoration.** Same muted `type.caption` style for both states — just the verb and
  time swap. No "edited" pill, no accent color, no second timestamp. (Chanel's mirror: remove the
  accessory.) A future "logged X, edited Y" detail-on-tap is a possible nicety but explicitly **out
  of scope**.
- **D3 — Date format matches the app's existing terse vocabulary.** `"Jul 3"` (the `MONTHS`
  abbreviations already in `datetime.ts`), with the year appended only when it differs from the
  current year: `"Jul 3, 2025"`. No new date format introduced.
- **D4 — The same-day-relative / else-absolute rule is a considered non-default.** The generic answer
  (a `formatDistanceToNow`-style "5 hours ago / 3 days ago / 2 months ago") is exactly what we're
  rejecting: for logged work, the calendar date carries the information, and a fuzzy "3 days ago"
  hides it. Same-day gets relative because *then* recency is the useful frame ("22m ago" reads as
  "just now-ish"); across a day boundary, the date wins. This is grounded in the timesheet's job, not
  a stylistic default.
- **Separator unchanged** — the middle dot: `logged · 5h ago`, `edited · Jul 3`.

## Behavioral spec

Reference instant `ref` and verb:
- `edited = new Date(updatedAt).getTime() > new Date(createdAt).getTime()`
  (on create the backend sets both to the *same* `OffsetDateTime`, so they're equal → "logged").
- `verb = edited ? 'edited' : 'logged'`; `ref = edited ? updatedAt : createdAt`.

Time text, with `now = new Date()` (both `ref` and `now` read in the **viewer's local zone** —
`getFullYear/Month/Date` are local, so "same day" is the viewer's day):

| Condition (viewer-local) | Output |
| --- | --- |
| `ref` future or `< 45s` ago (clock skew guard) | `just now` |
| same local day, `< 60 min` | `Nm ago` |
| same local day, `≥ 60 min` | `Nh ago` (same day ⇒ ≤ ~23h) |
| different local day, same year | `MMM D` → `Jul 3` |
| different local day, different year | `MMM D, YYYY` → `Jul 3, 2025` |

Note this renders the **action instant's local date** (the viewer's zone), which is legitimately
viewer-relative — it is *not* `entry_date`. The stable work-day (`entry_date`) still drives which
day-page / feed group the card belongs to and the screen header; that is unchanged. (Two viewers in
different zones seeing a different "edited" date for the same edit near midnight is correct-per-viewer.)

## Implementation

1. **`client/src/lib/datetime.ts`** — replace `relativeTime` (only consumer is `EntryCard`) with:
   ```ts
   activityLabel(createdAt: string, updatedAt: string, now?: Date): { verb: 'logged' | 'edited'; when: string }
   ```
   Reuse the existing `isSameDay(ref, now)` helper for the same-day test and the existing `MONTHS`
   array for the date branch. Keep the `now` param injectable for testing.
2. **`client/src/components/EntryCard.tsx`** — `const a = activityLabel(entry.createdAt, entry.updatedAt);`
   render `{a.verb} · {a.when}`; **no style change** (D2).
3. **Sorting** — the day view (`app/(app)/day/[date].tsx`) already sorts by `createdAt` desc; the feed
   and profile group by `entryDate` then `createdAt`. **No functional change**, but add a one-line
   comment at the day-view sort ("order by createdAt — the edited label must not reorder rows") so a
   future edit doesn't switch it to `updatedAt`.

**Applies wherever `EntryCard` renders** (day view + home feed) for one consistent behavior — not a
day-view-only prop. If day-view-only is ever wanted, add a prop then.

## Non-goals

- No "Yesterday" / "1d ago" — the rule is literal: not-today ⇒ the date.
- No second/paired timestamp ("logged X, edited Y"), no tooltip, no visual badge.
- No backend, API, schema, sort-order (still created-desc), or seed change.

## Verification

No client test suite exists yet, so:
- **Node repro** (as used for the timezone fix) under a `+10` TZ covering every row of the spec table:
  logged-today→relative, edited→"edited"+updatedAt's time, logged-other-day→`Jul 3`, cross-year→year
  shown, future-skew→`just now`.
- **Live check (standing rule):** on the day view and feed, confirm an unedited row reads
  `logged · …`, an edited row reads `edited · …` keyed to the edit time, a today action is relative,
  and an older one shows a date. Not "done" until the developer has seen it live.
- *(Optional, deferred):* seed a first `datetime.test.ts` — worthwhile but its own small task.

## Relationship to in-flight (uncommitted) work

An interim fix earlier this session made `relativeTime`'s >7-day fallback use `entryDate` to stop a
timezone day-shift. **Story 12 supersedes that** — `activityLabel` replaces `relativeTime` entirely,
and its not-today branch renders the action's local date. The **seed-data timestamp fixes**
(`created_at` on the work day, per-user hours) from that same session are **independent and stand** —
they're about data quality, not this label. Land Story 12 on `feature/12-entry-activity-label` off
`dev`; fold the now-moot interim `relativeTime` edit into it (net: `relativeTime` removed).
