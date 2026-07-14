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
    -- Stamp created/updated at a plausible work-hour on the SAME calendar day as entry_date,
    -- so historical rows read coherently in the feed's relative-time and sort correctly on
    -- Profile. See the ts LATERAL below for why this is timezone-aware.
    ts.logged_at,
    ts.logged_at
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
        SELECT generate_series(1, 1 + (random() < 0.4)::int) AS n
    ) reps
    CROSS JOIN LATERAL (
        -- "Logged" instant on entry_date, in the developer's LOCAL zone so it renders on the
        -- same calendar day in the app. A bare (entry_date + 17:00)::timestamptz is 17:00 UTC,
        -- which east of UTC rolls to the NEXT day (the "logged · Jul 3 on the Jul 2 screen" bug).
        -- AT TIME ZONE also gets DST offsets right per historical date. Change 'Australia/Sydney'
        -- if your machine is elsewhere; capped at now() so today's rows aren't future-dated.
        -- Referencing u.id + reps.n gives each user a distinct base hour AND a per-entry offset,
        -- and forces per-row evaluation (referencing only entry_date got cached once per day).
        SELECT LEAST(
            (d.entry_date + time '08:00'
               + (u.id * interval '73 min')
               + (reps.n * interval '17 min')
               + (random() * interval '55 min')) AT TIME ZONE 'Australia/Sydney',
            now()
        ) AS logged_at
    ) ts;

-- Sanity check after running:
--   SELECT count(*) AS total,
--          min(entry_date) AS oldest,
--          max(entry_date) AS newest,
--          count(DISTINCT user_id) AS users
--   FROM time_entries;
