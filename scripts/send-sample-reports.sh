#!/usr/bin/env bash
# Post a varied set of sample Reports to worklog's Reports intake (POST /api/intake/reports —
# Epic 3, ADR-010; wire contract v1.2) so a fresh local gate has an inbox to triage and hand
# off. It stands in for Largata's relay, like send-sample-events.sh does for Events.
#
# What one run sends (fresh report ids every run, so it adds; it never replays):
#   six Reports chosen to exercise every shape the Handoff text formats (Story 28 ticket 02):
#   - two on the same screen (the index groups them), one with a Markdown-hostile, multi-line
#     description (#, |, backticks, blank lines)
#   - a signed-out reporter (no name, no uid) on iOS with full device context
#   - a pre-device-context Android Report (app version only, no os/browser/model)
#   - an idea with no screen (the index's "(no screen)" group)
#   - a multi-paragraph Android problem with os + device model
#   submitted over the last few days, all landing as New. Notes can only be added by a
#   Member in the app (intake never carries them), so add a few there to see them in a
#   Handoff. No screenshots.
#
# Usage:
#   scripts/send-sample-reports.sh <base-url>
#
# The secret comes from $REPORTS_INTAKE_SECRET, defaulting to the local dev placeholder, e.g.
#   set -a; . ./.env; set +a; scripts/send-sample-reports.sh http://localhost:8080
# It is sent as a header and never printed. Local and dev only: never point this at prod.
set -euo pipefail

BASE="${1:-}"
if [ -z "$BASE" ]; then
  sed -n '2,22p' "$0"
  exit 2
fi
BASE="${BASE%/}"
SECRET="${REPORTS_INTAKE_SECRET:-dev-only-placeholder-intake-secret-change-me}"
TMP="$(mktemp)"
# A Windows curl (Git Bash) cannot open a /tmp path; hand it the native one when available.
TMP_FOR_CURL="$TMP"
command -v cygpath >/dev/null 2>&1 && TMP_FOR_CURL="$(cygpath -w "$TMP")"

uuid() {
  # A random v4 UUID without depending on uuidgen (absent on Git Bash).
  local h
  h=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
  printf '%s-%s-4%s-%x%s-%s' "${h:0:8}" "${h:8:4}" "${h:13:3}" \
    $(( (16#${h:16:1} & 3) | 8 )) "${h:17:3}" "${h:20:12}"
}

iso() {
  # Epoch seconds -> ISO-8601 UTC instant. GNU date first, BSD date as the fallback.
  date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -r "$1" +%Y-%m-%dT%H:%M:%SZ
}

now=$(date -u +%s)
sent=0
failed=0

# send <hours-ago> <json with __ID__ and __AT__ placeholders>
send() {
  local at body status
  at=$(iso $(( now - $1 * 3600 )))
  body="${2//__ID__/$(uuid)}"
  body="${body//__AT__/$at}"
  printf '%s' "$body" > "$TMP"
  status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 -X POST "$BASE/api/intake/reports" \
    -H "X-Intake-Secret: $SECRET" -F "report=@$TMP_FOR_CURL;type=application/json") || status="curl-error"
  if [ "$status" = 201 ]; then
    sent=$((sent + 1))
    echo "  201  $(printf '%s' "$2" | sed -E 's/.*"description":"([^"]{0,60}).*/\1/')"
  else
    failed=$((failed + 1))
    echo "  $status  FAILED: $(printf '%s' "$2" | sed -E 's/.*"description":"([^"]{0,60}).*/\1/')"
  fi
}

echo "== sending sample Reports to $BASE =="

send 70 '{"reportId":"__ID__","type":"problem","description":"Itinerary day view: the hotel card shows the wrong check-in date.\n\n# Steps\n1. Open a trip with a hotel on day 2\n2. Tap the hotel card\n\n| expected | actual |\n|---|---|\n| Oct 12 | Oct 11 |\n\nConsole said `TypeError: cannot read checkIn`.","reporter":{"name":"Ada Traveler","uid":"sample-uid-ada"},"context":{"platform":"web","appVersion":"1.4.2","screen":"(tabs)/(trips)/itineraries/[id]","os":"macOS 14.6","browser":"Safari 17.6"},"submittedAt":"__AT__"}'

send 52 '{"reportId":"__ID__","type":"problem","description":"Dragging a stop to another day snaps it back.","reporter":{"name":"Ben Wanderer","uid":"sample-uid-ben"},"context":{"platform":"android","appVersion":"1.4.2","screen":"(tabs)/(trips)/itineraries/[id]","os":"Android 14","deviceModel":"Pixel 8"},"submittedAt":"__AT__"}'

send 40 '{"reportId":"__ID__","type":"problem","description":"The invite link opened the app but nothing happened after sign-in.","context":{"platform":"ios","appVersion":"1.4.3","screen":"join/[token]","os":"iOS 17.5","deviceModel":"iPhone15,3"},"submittedAt":"__AT__"}'

send 30 '{"reportId":"__ID__","type":"idea","description":"Let me pin a hotel to a specific day so it shows at the top of that day.","reporter":{"name":"Cleo Nomad","uid":"sample-uid-cleo"},"context":{"platform":"android","appVersion":"1.4.1"},"submittedAt":"__AT__"}'

send 12 '{"reportId":"__ID__","type":"idea","description":"A dark mode for the postcard editor would help at night.","reporter":{"name":"Dev Roamer","uid":"sample-uid-dev"},"context":{"platform":"web","appVersion":"1.4.3","screen":"(tabs)/postcards/new","os":"Windows 11","browser":"Chrome 129"},"submittedAt":"__AT__"}'

send 3 '{"reportId":"__ID__","type":"problem","description":"Uploading a postcard photo fails on mobile data.\n\nIt works on wi-fi. On 4G the spinner runs for about a minute, then the photo disappears with no error message.","reporter":{"name":"Eve Explorer","uid":"sample-uid-eve"},"context":{"platform":"android","appVersion":"1.4.3","screen":"(tabs)/postcards/new","os":"Android 13","deviceModel":"SM-S911B"},"submittedAt":"__AT__"}'

rm -f "$TMP"
echo "== $sent sent, $failed failed =="
[ "$failed" = 0 ]
