# 02 — Inbox tab: the team sees Reports

**What to build:** The Inbox becomes visible. A fifth tab (bug icon) joins the pill —
Home · Calendar · ＋ · Reports · Profile — with a badge counting `new` reports so fresh
feedback shows from any screen. Opening it lists reports newest-first: type glyph,
description snippet, reporter name, platform + app version, submitted time, and a status
pill, filterable by status (default: everything open). Demo: inject a report through the
intake endpoint, watch it appear in the browser with the badge lit.

**Blocked by:** 01 — Intake skeleton.

**Status:** done

- [x] Fifth tab with a bug icon, in the order Home · Calendar · ＋ · Reports · Profile,
      riding the existing headless tab bar, crossfade transition, and reduced-motion
      behavior.
- [x] Badge shows the count of reports in `new`; hidden at zero; refreshes when the app
      foregrounds and when the tab gains focus (derived from the list fetch — no counts
      endpoint).
- [x] The badge does NOT clear from merely opening the tab (it clears only as reports are
      triaged out of `new` — the control for that is ticket 03; until then the badge
      simply reflects the list).
- [x] Inbox list newest-first by submitted time, status filter chips with the default
      showing `new` + `discuss` + `in progress`.
- [x] Cards carry type glyph, snippet, reporter name, platform + app version, submitted
      time, status pill — Largata brand, ≥44px touch targets, phone-portrait.
- [x] Manual UI checklist (web primary + native dev) per the standing no-automated-e2e
      decision; a curl-injected report appears with no client change.
