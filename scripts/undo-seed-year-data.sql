-- Undo scripts/seed-year-data.sql: delete ONLY the fabricated entries it inserted, keeping the
-- users and any REAL entries.
--
-- How the seeded rows are identified: the seed stamped created_at = entry_date + 17:00. Real app
-- entries stamp created_at at the actual moment they were logged (EntryService uses now()), which
-- is never exactly 17:00:00 on the same calendar day as entry_date. So that fingerprint isolates
-- the seeded rows precisely. Users are NOT touched by this (or by the seed) — usernames / names /
-- passwords are safe.
--
-- Run against the affected database (e.g. prod via Railway → Data → Query, or
-- `psql "$DATABASE_URL" -f scripts/undo-seed-year-data.sql`).

-- 1) PREVIEW — how many rows would be deleted vs kept (nothing changes yet). Run this first and
--    sanity-check: seeded_to_delete should match the seed volume (~1,000–1,400); real_to_keep
--    should match however many genuine entries prod had before.
SELECT
  count(*) FILTER (WHERE created_at = (entry_date + time '17:00')::timestamptz) AS seeded_to_delete,
  count(*) FILTER (WHERE created_at <> (entry_date + time '17:00')::timestamptz) AS real_to_keep,
  count(*) AS total
FROM time_entries;

-- 2) DELETE the seeded rows, atomically. Review the preview above before running this block.
BEGIN;

DELETE FROM time_entries
WHERE created_at = (entry_date + time '17:00')::timestamptz;

-- What remains after the delete — this should equal real_to_keep from the preview:
SELECT count(*) AS remaining FROM time_entries;

COMMIT;  -- change to ROLLBACK if `remaining` is not what you expect
