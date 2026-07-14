-- Rename user identities (username = the login id; name = the display name shown in the app).
--
-- This is NOT a Flyway migration — run it yourself, once per environment, against dev AND prod:
--     psql "$DATABASE_URL" -f scripts/rename-users.sql
--   or paste it into Railway's Data → Query tab for each environment.
--
-- WHY IT'S SAFE (dev + prod):
--   time_entries.user_id references users.id (the numeric id), and JWTs encode the id too — NOT
--   the username. So renaming preserves every entry's attribution and does not invalidate any
--   logged-in session. The only thing that changes is what people type to log in from now on.
--   Passwords are untouched by this script.
--
-- RULES:
--   * username is the login id: keep it lowercase, unique, no spaces (a UNIQUE index on
--     lower(username) will reject a collision, and login lowercases what users type).
--   * name is free display text (any case, spaces fine).
--   * Targets are the current usernames (admin, member1..member3). Edit the WHERE if yours differ.
--
-- >>> EDIT THE VALUES BELOW before running. The right-hand side of each SET is the placeholder. <<<
BEGIN;
UPDATE users
SET username = 'ed',
    name = 'Ed'
WHERE username = 'admin';
UPDATE users
SET username = 'jeb',
    name = 'Jeb'
WHERE username = 'member1';
UPDATE users
SET username = 'cj',
    name = 'CJ'
WHERE username = 'member2';
UPDATE users
SET username = 'aj',
    name = 'AJ'
WHERE username = 'member3';
-- Safety check: every username must be unique case-insensitively. Expect ZERO rows back;
-- if any row prints, you introduced a duplicate — the COMMIT below will still run, so fix first.
SELECT lower(username) AS clashing_username,
    count(*)
FROM users
GROUP BY lower(username)
HAVING count(*) > 1;
-- Review the result, then COMMIT. (Swap to ROLLBACK to abort without changes.)
COMMIT;
-- Verify afterwards:
--   SELECT id, username, name, role FROM users ORDER BY id;