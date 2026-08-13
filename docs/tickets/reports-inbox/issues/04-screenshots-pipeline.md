# 04 — Screenshots: intake, storage, serving

**What to build:** Reports can carry evidence. The intake endpoint accepts the wire
contract's 0–3 screenshot parts (JPEG/PNG, ≤5 MB each — Largata sends its sanitized,
downsized variants), worklog stores the bytes itself (database — the inbox must never
depend on Largata being up), and an authenticated endpoint streams each image back to
Members by ordinal. Pure backend slice; the inbox UI shows them in ticket 05. Demo/verify:
post a report with two images via curl, fetch them back byte-identical with a member JWT.

**Blocked by:** 01 — Intake skeleton.

**Status:** done

- [x] A new migration adds screenshot storage: report reference, ordinal (0–2), content
      type, image bytes.
- [x] Intake accepts 0–3 image parts alongside the JSON part; a report with images →
      `201` with a screenshot count/ordinals in the response.
- [x] More than 3 parts, an oversized part, or a non-JPEG/PNG part → `400` envelope,
      nothing persisted (report row included — all-or-nothing).
- [x] Idempotent replay of a report with screenshots neither duplicates rows nor
      rewrites bytes.
- [x] Screenshot read endpoint streams bytes with the stored content type to an
      authenticated Member; no/invalid JWT → `401`; unknown report or ordinal → `404`.
- [x] The team list response carries screenshot ordinals/counts only — never bytes.
- [x] A byte-for-byte round-trip test (multipart in → streamed out identical) at the API
      integration seam.
