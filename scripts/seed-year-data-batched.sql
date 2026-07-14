-- Same as seed-year-data.sql (~1 year of sample entries) but split into 4 smaller INSERTs of
-- ~3 months each (~300–350 rows apiece) so no single statement is large/slow enough to trip a
-- statement-timeout or row guard on Railway's web Query tab.
--
-- DEV / TEST ONLY (fabricated data). Attributes by user_id, so usernames don't matter.
--
-- How to run: paste the whole file into Railway → Data → Query. If the tab still stops after the
-- first statement, run the four INSERT blocks ONE AT A TIME — each commits on its own, so partial
-- progress is kept. Verify after with:
--     SELECT to_char(entry_date,'YYYY-MM') m, count(*) FROM time_entries GROUP BY 1 ORDER BY 1 DESC;
-- Expect the month count to climb toward ~13. For a clean slate first: DELETE FROM time_entries;

CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- (unused here; harmless, keeps parity with other scripts)

-- ── Batch 1 of 4 — days 0–90 (most recent ~3 months) ──────────────────────────────────────────
INSERT INTO time_entries (user_id, entry_date, duration_min, description, created_at, updated_at)
SELECT u.id, d.entry_date,
       (ARRAY[30,45,60,90,120,150,180,210,240])[1 + floor(random()*9)::int],
       (ARRAY['Standup + admin','Code review','Sprint planning','Bug fixing','Feature work',
              'Wrote docs','Pairing session','Refactoring','Test coverage','Design review',
              'Customer support','Release prep'])[1 + floor(random()*12)::int],
       ts.logged_at, ts.logged_at
FROM (SELECT id FROM users) u
CROSS JOIN LATERAL (SELECT (CURRENT_DATE - day_offset)::date AS entry_date
                    FROM generate_series(0, 90) AS day_offset
                    WHERE EXTRACT(ISODOW FROM CURRENT_DATE - day_offset) < 6 AND random() > 0.15) d
CROSS JOIN LATERAL (SELECT generate_series(1, 1 + (random() < 0.4)::int) AS n) reps
-- created_at/updated_at at a local-zone work-hour ON entry_date — not 17:00 UTC, which rolls to the
-- next day east of UTC (the "logged · Jul 3 on the Jul 2 screen" bug). DST-correct; capped at now().
-- Change 'Australia/Sydney' if your machine is in another zone. u.id gives each user a distinct base
-- hour and (with reps.n) forces per-row evaluation instead of one shared time per day.
CROSS JOIN LATERAL (SELECT LEAST((d.entry_date + time '08:00' + (u.id * interval '73 min') + (reps.n * interval '17 min') + (random() * interval '55 min')) AT TIME ZONE 'Australia/Sydney', now()) AS logged_at) ts;

-- ── Batch 2 of 4 — days 91–181 ────────────────────────────────────────────────────────────────
INSERT INTO time_entries (user_id, entry_date, duration_min, description, created_at, updated_at)
SELECT u.id, d.entry_date,
       (ARRAY[30,45,60,90,120,150,180,210,240])[1 + floor(random()*9)::int],
       (ARRAY['Standup + admin','Code review','Sprint planning','Bug fixing','Feature work',
              'Wrote docs','Pairing session','Refactoring','Test coverage','Design review',
              'Customer support','Release prep'])[1 + floor(random()*12)::int],
       ts.logged_at, ts.logged_at
FROM (SELECT id FROM users) u
CROSS JOIN LATERAL (SELECT (CURRENT_DATE - day_offset)::date AS entry_date
                    FROM generate_series(91, 181) AS day_offset
                    WHERE EXTRACT(ISODOW FROM CURRENT_DATE - day_offset) < 6 AND random() > 0.15) d
CROSS JOIN LATERAL (SELECT generate_series(1, 1 + (random() < 0.4)::int) AS n) reps
-- created_at/updated_at at a local-zone work-hour ON entry_date — not 17:00 UTC, which rolls to the
-- next day east of UTC (the "logged · Jul 3 on the Jul 2 screen" bug). DST-correct; capped at now().
-- Change 'Australia/Sydney' if your machine is in another zone. u.id gives each user a distinct base
-- hour and (with reps.n) forces per-row evaluation instead of one shared time per day.
CROSS JOIN LATERAL (SELECT LEAST((d.entry_date + time '08:00' + (u.id * interval '73 min') + (reps.n * interval '17 min') + (random() * interval '55 min')) AT TIME ZONE 'Australia/Sydney', now()) AS logged_at) ts;

-- ── Batch 3 of 4 — days 182–272 ───────────────────────────────────────────────────────────────
INSERT INTO time_entries (user_id, entry_date, duration_min, description, created_at, updated_at)
SELECT u.id, d.entry_date,
       (ARRAY[30,45,60,90,120,150,180,210,240])[1 + floor(random()*9)::int],
       (ARRAY['Standup + admin','Code review','Sprint planning','Bug fixing','Feature work',
              'Wrote docs','Pairing session','Refactoring','Test coverage','Design review',
              'Customer support','Release prep'])[1 + floor(random()*12)::int],
       ts.logged_at, ts.logged_at
FROM (SELECT id FROM users) u
CROSS JOIN LATERAL (SELECT (CURRENT_DATE - day_offset)::date AS entry_date
                    FROM generate_series(182, 272) AS day_offset
                    WHERE EXTRACT(ISODOW FROM CURRENT_DATE - day_offset) < 6 AND random() > 0.15) d
CROSS JOIN LATERAL (SELECT generate_series(1, 1 + (random() < 0.4)::int) AS n) reps
-- created_at/updated_at at a local-zone work-hour ON entry_date — not 17:00 UTC, which rolls to the
-- next day east of UTC (the "logged · Jul 3 on the Jul 2 screen" bug). DST-correct; capped at now().
-- Change 'Australia/Sydney' if your machine is in another zone. u.id gives each user a distinct base
-- hour and (with reps.n) forces per-row evaluation instead of one shared time per day.
CROSS JOIN LATERAL (SELECT LEAST((d.entry_date + time '08:00' + (u.id * interval '73 min') + (reps.n * interval '17 min') + (random() * interval '55 min')) AT TIME ZONE 'Australia/Sydney', now()) AS logged_at) ts;

-- ── Batch 4 of 4 — days 273–364 (oldest ~3 months) ────────────────────────────────────────────
INSERT INTO time_entries (user_id, entry_date, duration_min, description, created_at, updated_at)
SELECT u.id, d.entry_date,
       (ARRAY[30,45,60,90,120,150,180,210,240])[1 + floor(random()*9)::int],
       (ARRAY['Standup + admin','Code review','Sprint planning','Bug fixing','Feature work',
              'Wrote docs','Pairing session','Refactoring','Test coverage','Design review',
              'Customer support','Release prep'])[1 + floor(random()*12)::int],
       ts.logged_at, ts.logged_at
FROM (SELECT id FROM users) u
CROSS JOIN LATERAL (SELECT (CURRENT_DATE - day_offset)::date AS entry_date
                    FROM generate_series(273, 364) AS day_offset
                    WHERE EXTRACT(ISODOW FROM CURRENT_DATE - day_offset) < 6 AND random() > 0.15) d
CROSS JOIN LATERAL (SELECT generate_series(1, 1 + (random() < 0.4)::int) AS n) reps
-- created_at/updated_at at a local-zone work-hour ON entry_date — not 17:00 UTC, which rolls to the
-- next day east of UTC (the "logged · Jul 3 on the Jul 2 screen" bug). DST-correct; capped at now().
-- Change 'Australia/Sydney' if your machine is in another zone. u.id gives each user a distinct base
-- hour and (with reps.n) forces per-row evaluation instead of one shared time per day.
CROSS JOIN LATERAL (SELECT LEAST((d.entry_date + time '08:00' + (u.id * interval '73 min') + (reps.n * interval '17 min') + (random() * interval '55 min')) AT TIME ZONE 'Australia/Sydney', now()) AS logged_at) ts;
