# Handoff: Largata Usage Dashboard — Mobile Redesign

> Saved 2026-09-24 from the claude.ai design project
> (<https://claude.ai/design/p/336cd720-1741-4656-9b4c-87412c90fc4f>), handed over by the
> developer. The prototype files (`Dashboard v2.dc.html`, `Dashboard (Current).dc.html`,
> `support.js`) live in that project; `support.js` is the prototype runtime and is not needed
> to implement. Where the build departs from this handoff, see "Build notes" at the bottom.

## Overview
A mobile rework of the existing read-only "Largata usage" dashboard screen in the Expo/React Native client (`client/src/app/(app)/dashboard.tsx`). It keeps the screen's data model (summary totals per counter + a bar series per counter/bucket) but restructures the layout for phones: status folded into the header, a red hero card for today's active travelers, horizontally scrolling totals tiles that double as the chart picker, and a tap-to-inspect bar chart.

## Fidelity
**High-fidelity.** Colors, type sizes, spacing, and radii are final and match the app's existing theme tokens. Recreate pixel-perfectly using the codebase's existing components where they fit.

## Screens / Views

### Dashboard (mobile, 390pt design width)
Vertical scroll, background `#F7F7F8`, sections spaced 24pt apart, 16pt side insets (totals row bleeds full-width).

**1. Sticky header** — white at 92% opacity with blur (like a translucent nav bar), 1px bottom border `#ECECEE`, padding 12×16.
- Back chevron: 36×36 hit area, red `#F5333F` stroke icon, left-aligned (−8 margin to align icon optically).
- Title: "Activity" — 700, 17/22, `#1A1A1E`.
- Status line under the title:
  - Live: 7pt red dot (`#F5333F`, opacity pulsing 1→0.35 over 2.4s ease-in-out infinite) + "**Live** · last event 4 min ago" — 500, 11.5/15, `#8A8A8F`; "Live" 700 `#1A1A1E`. Append " · couldn't refresh" when the last poll failed.
  - Silent: 7pt gray dot `#C4C4C8` + "Silent since 11:31 AM" — 700, 11.5/15, `#D62330`.
- No clock in the header (the phone status bar has one).

**2. Silent banner** (only when Largata is silent) — 16pt side margins, `#FCE3E4` bg, 1px `#F5333F` border, radius 18, padding 14×16, warning triangle icon 18×18 red. Title "Largata silent since 3 hours ago" 700 15/20 `#D62330`; body "Its hourly Snapshot stopped at 11:31 AM. Numbers as of 2:31 PM." 500 12.5/16 `#8A8A8F`.

**3. Today hero card** (tappable) — 16pt margins, bg `#F5333F`, radius 22, padding 20, shadow `0 8 20 rgba(245,51,63,0.28)`. Two decorative circles top-right, `rgba(255,255,255,0.10)`, 140pt (offset −32,−32) and 64pt (right 8, top 36).
- Eyebrow "TODAY" — 700, 11/14, letter-spacing 0.9, uppercase, `rgba(255,255,255,0.85)`.
- Baseline row: count "7" 800 52/58 white tabular-nums + "travelers active" 700 17/22 white.
- No explainer line.
- Press: scale 0.98. Tap charts **traveler active history** in the Over-time card (no totals tile highlights — it's a distinct series from the traveler totals tile).

**4. Totals — horizontal snap scroll** (doubles as the chart picker)
- Eyebrow "TOTALS" — 700, 11/14, ls 0.9, uppercase, `#F5333F`, 16pt inset.
- Row: horizontal scroll, snap to start, `scroll-padding-left` 16 so tiles snap back to the inset, contained overscroll, hidden scrollbar, 10pt gap, 6pt end spacer, 16pt leading padding.
- Order (hierarchy): travelers, trips, itineraries, diaries, postcards.
- Tile: 124pt wide fixed, white bg, 1.5px border `#F6CBCE`, radius 18, padding 14, shadow `0 4 12 rgba(26,26,30,0.06)`. Value 800 26/32 `#1A1A1E` tabular-nums; label 600 13.5/18 `#1A1A1E` (pluralized via `counterLabel`).
- Selected (picked for the chart via tile tap): bg `#FCE3E4`, border `#F5333F`, value `#F5333F`. Press: scale 0.97.
- Tapping a partially visible tile scrolls it fully into view (smooth; measure real tile offsets, clamp to scroll range).
- The travelers tile charts **signups**, not active history.

**5. Over time card** — 16pt margins, white, 1px `#F6CBCE` border, radius 22, padding 18×16, 16pt internal gaps.
- Bucket picker: full-width segmented control, track `#ECECEE` radius pill, 3pt padding; segments Day / Month / Year, min-height 38, active segment white bg + shadow `0 1 4 rgba(26,26,30,0.12)`, label 700 13 `#1A1A1E`; inactive `#8A8A8F`.
- Readout: latest value 800 34/40 `#F5333F` tabular-nums; label 500 12.5/17 `#8A8A8F` — "travelers active today" (hero pick), "traveler signups today" (traveler tile), or "postcards created today" etc. Phrase by bucket: today / this month / this year.
- Chart: 150pt tall; dashed hairlines `#ECECEE` at top and 50%; max value label top-right 700 10.5 `#C4C4C8`; bars flex-1 (max 34pt wide), 3pt gap, radius 4 top, on a 1px `#ECECEE` baseline. Selected bar `#F5333F`, others `#F7A9AC`, zero-value bars 2pt `#ECECEE`. Bars animate height in on mount (~450ms cubic-bezier(.2,.7,.2,1)); default selection is the last bar.
- Axis labels: first and last bucket ("Sep 11" / "Sep 24"; "Oct 2025" / "Sep 2026"; "2024" / "2026") 500 11 `#8A8A8F`.
- Tap a bar to select it → detail strip: `#F7F7F8` radius 14 padding 10×14, left = bucket label 700 12.5 `#1A1A1E`, right = "3 created" / "3 active" / "3 signed up" 500 12.5 `#8A8A8F`.
- Footer line: "55 created over 14 days" / "peak 9 active over 14 days" / "31 signups over 14 days" — 500 11.5 `#C4C4C8`.
- No "deleted" figures anywhere.

## Interactions & Behavior
- Hero card tap → picked = traveler **active** series, no totals highlight.
- Totals tile tap → picked = that counter's created/signups series, tile highlights, tile scrolls fully into view.
- Bucket change or counter change resets bar selection to the latest bar.
- Bar tap → selects that bar, updates the detail strip.
- Counters remain opaque strings from the API — labels always via `counterLabel(counter, n)`; never hardcode the counter list.
- Buckets: day = 14 points, month = 12, year = 3 (follow existing `useDashboardSeries` behavior).

## State Management
- `picked: string | null` (counter), `viaHero: boolean` (traveler active vs signups series), `bucket: 'day' | 'month' | 'year'`, `sel: number` (selected bar index, −1 = latest).
- Data from existing summary / series hooks; the traveler active-history series needs its own API series if not already exposed.

## Design Tokens (match `client/src/theme`)
- Red `#F5333F`, red-pressed/dark `#D62330`, red-tint bg `#FCE3E4`, red hairline `#F6CBCE`, bar muted `#F7A9AC`.
- Ink `#1A1A1E`, muted `#8A8A8F`, faint `#C4C4C8`, hairline `#ECECEE`, screen bg `#F7F7F8`, card white `#FFFFFF`.
- Font: Plus Jakarta Sans (400/500/600/700/800), tabular numerals for all counts.
- Radii: cards 18–22, pills 999, chart bars 4. Spacing on a 4pt rhythm.
- Shadows: card `0 4 12 rgba(26,26,30,0.06)`, hero `0 8 20 rgba(245,51,63,0.28)`.

## Build notes (2026-09-24)
- **Travelers-active history** is a new read, `GET /api/dashboard/active?bucket=&zone=[&from=&to=]`:
  distinct Travelers per bucket of the viewer's calendar, the same rule as "active today".
- **Traveler signups** use the `traveler` counter's series (`traveler.created`), per the
  wire contract's suggested kinds. If Largata names sign-ups differently, the travelers tile
  charts whatever `<counter>.created` it sends — the counter list is never hardcoded.
- **Day = 14 points**: the client asks for the last 14 days (`from` = today − 13). Month uses
  the server's default 12; year uses every year with data (the prototype's "3" is sample data).
- **The hero's number is the summary's `activeToday`**; the chart's latest day bucket counts
  the same rule, so the two agree.
- **UTC calendar (2026-10-05):** the screen counts "today" and every bucket in UTC for every
  viewer (the developer's call); times of day still display in local time.
- **Loading, error and no-data states** are not in the prototype; they keep the existing
  screen's behaviour (skeleton on first load, quiet failed polls, error card with retry).
