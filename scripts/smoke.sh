#!/usr/bin/env bash
# Smoke-test the timesheet full stack. Run against the LOCAL gate before deploying, and
# against PROD after. It exercises the single-origin serving, the SPA fallback, the /api
# auth boundary, and — crucially — the CORS-behind-a-TLS-proxy path that only the real
# HTTPS deploy first surfaced (Story 9 / ADR-008): a same-origin browser POST must not be
# rejected as "Invalid CORS request".
#
# Usage:
#   scripts/smoke.sh                                   # local gate (http://localhost:8080)
#   scripts/smoke.sh http://localhost:8080 admin PASS  # local + authenticated checks
#   scripts/smoke.sh https://<app>.up.railway.app      # prod (unauth checks + real-origin CORS)
#
# Exit code = number of failed checks (0 = all pass).
set -u
BASE="${1:-http://localhost:8080}"; BASE="${BASE%/}"
USER="${2:-}"; PASS="${3:-}"
fails=0
ok(){ echo "  PASS  $1"; }
no(){ echo "  FAIL  $1${2:+  (got: $2)}"; fails=$((fails+1)); }
code(){ curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$@"; }

echo "== smoke: $BASE =="
[ "$(curl -s --max-time 20 "$BASE/api/health")" = '{"status":"ok"}' ] && ok "GET /api/health" || no "health"
curl -s --max-time 20 "$BASE/" | grep -q '/_expo/static/js/web/entry-' && ok "GET / serves SPA shell" || no "/ SPA shell"
[ "$(code "$BASE/team")" = 200 ] && ok "GET /team SPA fallback" || no "/team fallback"
[ "$(code "$BASE/nope.js")" = 404 ] && ok "missing asset -> 404" || no "missing asset 404"
[ "$(code "$BASE/api/entries")" = 401 ] && ok "GET /api/entries no token -> 401" || no "/api 401 boundary"

echo "-- CORS behind TLS proxy (the trap that reached prod) --"
# A same-origin browser POST carries an Origin header. Behind a proxy that terminates TLS,
# the app must honor X-Forwarded-Proto to recognize it as same-origin (else -> 403 CORS).
if [ "${BASE#https://}" != "$BASE" ]; then
  # Real https deploy: use the real origin; the platform sets the forwarded headers.
  hdr=(-H "Origin: $BASE")
else
  # Local http gate: simulate the proxy (https Origin + matching forwarded host).
  probe="smoke-probe.example.com"
  hdr=(-H "Origin: https://$probe" -H 'X-Forwarded-Proto: https' -H "X-Forwarded-Host: $probe")
fi
resp=$(curl -s -w '|%{http_code}' --max-time 20 -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' "${hdr[@]}" \
  -d '{"username":"__cors_probe__","password":"__x__"}')
if echo "${resp%|*}" | grep -q 'Invalid CORS request'; then
  no "same-origin POST rejected as CORS — forward-headers not honored" "${resp%|*}"
else
  ok "same-origin POST not CORS-blocked (HTTP ${resp##*|})"
fi

# Only local can safely assert cross-origin rejection (we control the forwarded host there).
if [ "${BASE#https://}" = "$BASE" ]; then
  xo=$(curl -s -w '|%{http_code}' --max-time 20 -X POST "$BASE/api/auth/login" \
    -H 'Content-Type: application/json' \
    -H 'Origin: https://evil.example.com' -H 'X-Forwarded-Proto: https' -H 'X-Forwarded-Host: smoke-probe.example.com' \
    -d '{"username":"x","password":"y"}')
  { [ "${xo##*|}" = 403 ] && echo "${xo%|*}" | grep -q 'Invalid CORS'; } \
    && ok "genuine cross-origin still blocked (CORS not weakened)" || no "cross-origin not blocked" "${xo##*|}"
fi

if [ -n "$USER" ] && [ -n "$PASS" ]; then
  echo "-- authenticated (login + read) --"
  tok=$(curl -s --max-time 20 -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" | grep -oE '"token":"[^"]+"' | sed 's/"token":"//;s/"//')
  [ -n "$tok" ] && ok "login -> token" || no "login"
  [ -n "$tok" ] && { [ "$(code "$BASE/api/entries" -H "Authorization: Bearer $tok")" = 200 ] && ok "GET /api/entries w/ token -> 200" || no "authed list"; }
fi

echo "== $([ $fails -eq 0 ] && echo 'ALL PASS' || echo "$fails FAILED") =="
exit $fails
