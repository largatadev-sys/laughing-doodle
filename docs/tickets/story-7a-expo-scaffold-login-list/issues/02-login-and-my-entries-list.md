# 02 — Login and my-entries list

**What to build:** A Member can log in on the Expo client with their seeded username and
password, and land on a screen listing only their own time entries, pulled live from the
real backend. Wrong credentials show an inline error and do not navigate anywhere. This is
the full authenticated read path in one slice: a single typed `apiClient` gateway, a
platform-branched `tokenStorage` (SecureStore on native, `localStorage` on web), an
`AuthContext` that persists `{token, user}` from the login response, a login screen, and a
my-entries list screen.

Caller identity comes from the login response's `user.id` (`UserSummary`) — no
client-side JWT decoding. The list calls `GET /api/entries?userId=<me.id>` and renders
`entryDate`, `durationMin`, `description` per row; an empty result renders an empty state,
not an error.

Full design context (why each choice was made):
[docs/plans/story-7a-expo-scaffold-login-list.md](../../../plans/story-7a-expo-scaffold-login-list.md)
(decisions 4–7, 9–10). Domain glossary and invariants:
[docs/design/02-domain-model.md](../../../design/02-domain-model.md) (shared visibility;
TimeEntry). API conventions: [docs/design/05-api-conventions.md](../../../design/05-api-conventions.md).

**Blocked by:** 01 — Expo client scaffold.

**Status:** ready-for-agent

- [ ] Log in with a valid seeded username/password on web → token and user are persisted
      → navigates to the my-entries list.
- [ ] Log in with a wrong password or unknown username → inline error message rendered
      from the response's error envelope (`message`), no navigation, nothing persisted.
- [ ] The my-entries list shows only the caller's own entries (via `?userId=<me.id>`),
      not other users' — pulled live from `GET /api/entries`.
- [ ] An empty list of entries renders an empty state, not an error.
- [ ] The same login → list flow works identically in Expo native dev, from the same code.
- [ ] No bare `fetch` calls in screen components — all requests go through the one
      `apiClient` module.
