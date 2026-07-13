# 01 — Expo client scaffold: boots on web + native

**What to build:** Stand up the Expo (React Native, TypeScript) client project at
`client/`, using Expo Router for navigation. The app boots and renders a minimal
placeholder screen, runnable both as a web build and in Expo native dev — proving the
single-codebase toolchain works before any auth/data logic is layered on. Directory
layout (`app/` for routes, `lib/` for the API client/token storage/types that later
tickets fill in) and `.env.example` (`EXPO_PUBLIC_API_URL` placeholder) are in place.

First Expo/TypeScript touch in this repo, so this ticket is deliberately isolated from
auth logic — new-toolchain setup risk (SDK version, web bundler config) shouldn't be
tangled with application logic risk.

Full design context (why each choice was made):
[docs/plans/story-7a-expo-scaffold-login-list.md](../../../plans/story-7a-expo-scaffold-login-list.md)
(decisions 2, 3, 12). Epic context:
[docs/design/07-epic-map.md](../../../design/07-epic-map.md) Story 7a.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `client/` is a working Expo Router + TypeScript project (via `create-expo-app`).
- [ ] `npx expo start --web` boots the app and renders a placeholder screen in a browser.
- [ ] `npx expo start` (native — Expo Go or simulator) boots the same app from the same
      code.
- [ ] `app/` and `lib/` directories exist per the plan's intended layout (routes vs.
      shared client logic), ready for the login/list ticket to build in.
- [ ] `.env.example` commits an `EXPO_PUBLIC_API_URL` placeholder; `.env` is gitignored.
- [ ] No auth, API client, or token storage code yet — out of scope for this ticket.
