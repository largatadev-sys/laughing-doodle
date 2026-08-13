# 05 — Screenshots in the inbox

**What to build:** Members see what reporters saw. Cards with screenshots show an
indicator; the report detail screen renders its screenshots (fetched with the client's
authenticated API access, same as every other call) and lets a Member tap one to view it
full-size. Demo: the two-image report from ticket 04's curl demo is browsable end-to-end
in the inbox.

**Blocked by:** 03 — Status lifecycle (the detail screen) · 04 — Screenshots pipeline
(the bytes).

**Status:** done

- [x] Cards indicate when a report carries screenshots (count or glyph — quiet, on-brand;
      the tally-bar-free card layout stays scannable).
- [x] The detail screen renders each screenshot via authenticated fetch (bearer header —
      a plain image `src` cannot carry one), with graceful loading/error states.
- [x] Tapping a screenshot opens it full-size; dismissable; respects reduced motion.
- [x] Works on web (primary) and native dev; manual UI checklist per the standing
      no-automated-e2e decision.
