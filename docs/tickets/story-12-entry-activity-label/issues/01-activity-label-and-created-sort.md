# 01 — Entry card activity label ("logged / edited · when") + created-order sort

**What to build:** Replace `relativeTime` in `client/src/lib/datetime.ts` (its only consumer is
`EntryCard`) with `activityLabel(createdAt, updatedAt, now?)` returning
`{ verb: 'logged' | 'edited'; when: string }`, and render it in
`client/src/components/EntryCard.tsx` as `{verb} · {when}` with **no style change**.

Rules (see full design context:
[docs/plans/story-12-entry-activity-label.md](../../../plans/story-12-entry-activity-label.md)):

- **Verb:** `edited` when `new Date(updatedAt).getTime() > new Date(createdAt).getTime()`, else
  `logged`. The reference instant is `updatedAt` when edited, otherwise `createdAt`. (Backend stamps
  both equal on create → "logged".)
- **When (viewer-local):** future/`<45s` → `just now`; same local day `<60m` → `Nm ago`; same local
  day `≥60m` → `Nh ago`; **different local day → the date, never "days ago"** — `Jul 3`, or
  `Jul 3, 2025` when the year differs. Reuse the existing `isSameDay` helper and `MONTHS` array.
- **Copy:** use **`edited`** (button says *Edit* → state says *edited*; matches the "Publish →
  Published" consistency rule). **Confirmed by the developer (2026-07-14).**
- **Sort:** entries stay ordered by `createdAt` (day view is already `createdAt` desc; feed/profile
  group by `entryDate` then `createdAt`). No functional change — add a one-line comment at the
  day-view sort so the new "edited" label doesn't tempt a switch to `updatedAt`.

Applies wherever `EntryCard` renders (day view + home feed), for one consistent behavior.

**Blocked by:** none. (Supersedes the in-flight interim `relativeTime` >7-day fallback tweak — fold
it in; the seed-data timestamp fixes from that session are independent and stay.)

**Status:** ready-for-agent

_(Fully specified; the only open decision — D1, "edited" vs "updated" — is resolved to "edited".)_

- [ ] `activityLabel` replaces `relativeTime`; `relativeTime` is removed (no remaining callers).
- [ ] Unedited entry reads `logged · …`; an edited entry reads `edited · …` keyed to `updatedAt`.
- [ ] Same-viewer-day action shows relative (`just now` / `Nm ago` / `Nh ago`); any other day shows
      the date (`Jul 3` / `Jul 3, 2025`) — no `Nd ago`, no "Yesterday".
- [ ] Clock-skew / future `ref` degrades to `just now`, not a negative duration.
- [ ] `EntryCard` caption keeps the same `type.caption` style — no pill, color, or second timestamp.
- [ ] Day-view list order unchanged (still `createdAt` desc); an edit does not reorder a row.
- [ ] Verified by a Node repro under a `+10` TZ across every spec-table row, **and** live by the
      developer on the day view + feed (edited vs unedited, today vs older).
- [ ] Behaves identically in Expo native and web from the same code.

## Comments

- 2026-07-14 — D1 resolved: verb is **"edited"** (developer confirmed), for Edit→edited flow
  consistency. Status → ready-for-agent.
