-- ~1 YEAR of sample time entries, for testing the app at volume (feed/Profile at scale — see the
-- "Pagination, virtualization & scale" backlog item in docs/design/07-epic-map.md).
--
-- DEV / TEST ONLY. This inserts fabricated entries. Do NOT run it against prod unless you
-- deliberately want demo data there. It is NOT a Flyway migration — run it yourself:
--     psql "$DATABASE_URL" -f scripts/seed-year-data.sql
--   or paste into Railway's Data → Query tab (dev environment).
--
-- Attributes entries to whatever users currently exist, BY ID — so it works no matter what the
-- usernames are now (admin/member1… or ed/jeb/cj/aj). Roughly: each user gets 1–2 entries per
-- weekday over the last 365 days, ~15% of weekdays skipped, weekends off. Expect ~1,000–1,400 rows.
--
-- Re-runnable. This SUPERSEDES the 30-day scripts/seed-dev-data.sql (it covers the whole year,
-- recent weeks included). For a clean single-year dataset, clear first:
--     DELETE FROM time_entries;   -- (uncomment the line below to do it in one shot)

-- DELETE FROM time_entries;

INSERT INTO time_entries (user_id, entry_date, duration_min, description, created_at, updated_at)
SELECT
    u.id,
    d.entry_date,
    (ARRAY[30, 45, 60, 90, 120, 150, 180, 210, 240])[1 + floor(random() * 9)::int],
    (ARRAY[
        'Standup + admin',
        'Code review',
        'Sprint planning',
        'Bug fixing',
        'Feature work',
        'Client scaffold updates',
        'Wrote docs',
        'Investigated deploy issue',
        'Backlog grooming',
        'Pairing session',
        'Refactoring',
        'Test coverage',
        'Design review',
        'Customer support',
        'Incident follow-up',
        'Release prep'
    ])[1 + floor(random() * 16)::int],
    -- Stamp created/updated at the day it was "logged" (17:00), so historical rows read
    -- coherently in the feed's relative-time and sort correctly on Profile.
    (d.entry_date + time '17:00')::timestamptz,
    (d.entry_date + time '17:00')::timestamptz
FROM
    (SELECT id FROM users) u
    CROSS JOIN LATERAL (
        SELECT (CURRENT_DATE - day_offset)::date AS entry_date
        FROM generate_series(0, 364) AS day_offset
        WHERE EXTRACT(ISODOW FROM CURRENT_DATE - day_offset) < 6  -- Mon–Fri only
          AND random() > 0.15                                     -- ~15% of weekdays skipped
    ) d
    CROSS JOIN LATERAL (
        -- 1 entry, plus a 2nd on ~40% of days
        SELECT generate_series(1, 1 + (random() < 0.4)::int)
    ) reps;

-- Sanity check after running:
--   SELECT count(*) AS total,
--          min(entry_date) AS oldest,
--          max(entry_date) AS newest,
--          count(DISTINCT user_id) AS users
--   FROM time_entries;
