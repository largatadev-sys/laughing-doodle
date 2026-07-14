-- Reset a user's password directly in the database — the sanctioned admin-reset path (see
-- V2__seed_users.sql and ADR-002). Use this when someone is locked out and therefore can't use
-- the in-app "Change password" (which requires the current password).
--
-- Auth-sensitive. Run it yourself against the target database (dev or prod) — Railway → Data →
-- Query, or  psql "$DATABASE_URL" -f scripts/reset-password.sql . It is NOT a Flyway migration.
--
-- You type the new password right here in the SQL, so it is never shared with anyone else. It's
-- BCrypt-hashed by pgcrypto — verified to authenticate against the app's Spring BCryptPasswordEncoder.
--
-- >>> EDIT the username and the new password below (both placeholders), then run. <<<

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- provides crypt()/gen_salt(); idempotent, safe to re-run

UPDATE users
SET password_hash = crypt('CHANGE_ME_new_password', gen_salt('bf', 10))
WHERE username = 'CHANGE_ME_username';

-- Confirm exactly one row matched and the account exists (this does NOT print the hash):
SELECT id, username, name, role FROM users WHERE username = 'CHANGE_ME_username';

-- Then log in with the new password. (You can change it again in-app afterwards if you like.)
--
-- ── Fallback ──────────────────────────────────────────────────────────────────────────────────
-- If your DB doesn't permit CREATE EXTENSION pgcrypto, reset to the placeholder password
-- "changeme123" using its known BCrypt hash instead, then change it in the app:
--   UPDATE users
--   SET password_hash = '$2a$10$9f1BL/OiJ5NBDm04Zs9eZeDwZiCTYVkYp1yIXfYNoX5WztG9fwD66'
--   WHERE username = 'CHANGE_ME_username';
