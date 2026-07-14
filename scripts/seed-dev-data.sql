-- ~1 month of sample time entries for manual testing in the DEV environment ONLY.
-- Run this yourself against dev's Postgres (Railway dev environment's "Data"/"Query" tab,
-- or `psql "$DEV_DATABASE_URL" -f scripts/seed-dev-data.sql`). It is NOT a Flyway migration —
-- it never runs automatically and never touches prod (see CLAUDE.md: deploys are not "done"
-- on automated checks alone; likewise, prod data is never touched by an unreviewed script).
--
-- Assumes the standard seeded users from V2__seed_users.sql (admin, member1, member2,
-- member3, ids 1-4 on a fresh migration).
--
-- Generates one weekday entry per user for each of the last ~30 days (weekends skipped, like
-- a real timesheet), with a few days randomly skipped (nobody logs every single day) and
-- duration/description picked from a small rotating set for variety. Re-runnable: run
-- `DELETE FROM time_entries;` first if you want a clean slate before reseeding.

INSERT INTO time_entries (user_id, entry_date, duration_min, description)
SELECT
    u.id,
    d.entry_date,
    (ARRAY[45, 60, 90, 120, 150, 180, 210, 240])[1 + floor(random() * 8)::int],
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
        'Test coverage'
    ])[1 + floor(random() * 12)::int]
FROM
    (SELECT id FROM users) u
    CROSS JOIN LATERAL (
        SELECT (CURRENT_DATE - day_offset)::date AS entry_date
        FROM generate_series(0, 29) AS day_offset
        WHERE EXTRACT(ISODOW FROM CURRENT_DATE - day_offset) < 6  -- Mon-Fri only
    ) d
WHERE random() > 0.15;  -- ~15% of weekdays are skipped (nobody logs every single day)

-- Sanity check after running:
--   SELECT u.username, count(*) AS entries, sum(t.duration_min) AS total_min
--   FROM time_entries t JOIN users u ON u.id = t.user_id
--   GROUP BY u.username ORDER BY u.username;
