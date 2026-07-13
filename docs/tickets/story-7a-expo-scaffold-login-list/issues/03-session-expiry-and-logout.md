# 03 — Session expiry and logout

**What to build:** If any authenticated API call comes back `401` (an expired or invalid
token — the backend's ~7-day JWT TTL means this will eventually happen mid-session, and
there's no refresh/revocation per ADR-002), the client clears the stored session and
returns the user to the login screen instead of showing a broken or stuck UI. A visible
logout action on the my-entries list does the same thing on demand, so a session can be
ended manually as well as by expiry.

Full design context (why each choice was made):
[docs/plans/story-7a-expo-scaffold-login-list.md](../../../plans/story-7a-expo-scaffold-login-list.md)
(decisions 8, 11).

**Blocked by:** 02 — Login and my-entries list.

**Status:** ready-for-agent

- [ ] Any `401` response from an authenticated call clears the stored token/user and
      redirects to `/login` — verified by forcing a call with a corrupted/invalid stored
      token.
- [ ] After the redirect, the login screen behaves as a fresh, unauthenticated start (no
      stale entries visible, a new login is required).
- [ ] A logout action is visible on the my-entries list; using it clears the stored
      session and returns to `/login` the same way an expiry does.
- [ ] Logging back in after a logout/expiry works normally (no leftover state from the
      previous session).
