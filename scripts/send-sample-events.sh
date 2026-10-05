#!/usr/bin/env bash
# Post a realistic sample batch of Largata usage Events to worklog's Events intake
# (POST /api/intake/events — Epic 4, ADR-014) and print the per-Event verdicts. It stands in
# for Largata's sender until that ships, so every live check has data to look at.
#
# What one run sends (fresh event ids every run):
#   - one Snapshot, dated --snapshot-age-hours ago (default 0 = now), with totals
#     traveler 40 · trip 25 · postcard 60 · diary 18 · itinerary 9
#   - after that Snapshot: traveler.created x2, trip.created x3, trip.deleted x1,
#     postcard.created x4, postcard.deleted x1, diary.created x1, itinerary.created x1,
#     and traveler.active pings from 5 Travelers (two of whom also created something)
#   - with --spread-days N: a few trip/postcard created Events and traveler.active pings on
#     each of the N days before the Snapshot, from a rotating cast of Travelers (series and
#     active-history data only; they are before the Snapshot, so totals ignore them)
#
# Expected Dashboard after one fresh run against an empty log (snapshot age 0):
#   active today = 5 · traveler 42 · trip 27 · postcard 63 · diary 19 · itinerary 10
#   (subject to the viewer's day boundary; a run just after local midnight may split days)
#
# Usage:
#   scripts/send-sample-events.sh <base-url> [--snapshot-age-hours N] [--spread-days N]
#   scripts/send-sample-events.sh <base-url> --replay      # resend the last batch -> all duplicate
#
# The secret comes from $REPORTS_INTAKE_SECRET (the same one that guards /api/intake/reports),
# defaulting to the local dev placeholder. It is sent as a header and never printed.
set -euo pipefail

BASE="${1:-}"
if [ -z "$BASE" ]; then
  sed -n '2,24p' "$0"
  exit 2
fi
BASE="${BASE%/}"
shift

SNAPSHOT_AGE_HOURS=0
SPREAD_DAYS=0
REPLAY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --snapshot-age-hours) SNAPSHOT_AGE_HOURS="$2"; shift 2 ;;
    --spread-days) SPREAD_DAYS="$2"; shift 2 ;;
    --replay) REPLAY=1; shift ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

SECRET="${REPORTS_INTAKE_SECRET:-dev-only-placeholder-intake-secret-change-me}"
LAST="${TMPDIR:-/tmp}/largata-sample-events.json"

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

ev() { # kind subject(or empty) epoch
  if [ -n "$2" ]; then
    printf '{"eventId":"%s","kind":"%s","subject":"%s","occurredAt":"%s"}' "$(uuid)" "$1" "$2" "$(iso "$3")"
  else
    printf '{"eventId":"%s","kind":"%s","occurredAt":"%s"}' "$(uuid)" "$1" "$(iso "$3")"
  fi
}

if [ "$REPLAY" = 1 ]; then
  [ -f "$LAST" ] || { echo "no previous batch at $LAST — run once without --replay first" >&2; exit 1; }
  echo "== replaying the last batch ($LAST) =="
else
  now=$(date -u +%s)
  snap=$(( now - SNAPSHOT_AGE_HOURS * 3600 ))
  # Every post-Snapshot Event sits between the Snapshot and now, spaced a few seconds apart,
  # so each one counts toward the totals whatever the snapshot age is.
  t=$(( snap + 5 ))
  events=()
  events+=("$(printf '{"eventId":"%s","kind":"snapshot","occurredAt":"%s","totals":{"traveler":40,"trip":25,"postcard":60,"diary":18,"itinerary":9}}' "$(uuid)" "$(iso "$snap")")")
  for s in sample-traveler-a sample-traveler-b; do events+=("$(ev traveler.created "$s" $((t+=3)))"); done
  for s in sample-traveler-a sample-traveler-c sample-traveler-c; do events+=("$(ev trip.created "$s" $((t+=3)))"); done
  events+=("$(ev trip.deleted sample-traveler-c $((t+=3)))")
  for s in sample-traveler-b sample-traveler-b sample-traveler-d sample-traveler-e; do events+=("$(ev postcard.created "$s" $((t+=3)))"); done
  events+=("$(ev postcard.deleted sample-traveler-d $((t+=3)))")
  events+=("$(ev diary.created sample-traveler-e $((t+=3)))")
  events+=("$(ev itinerary.created sample-traveler-a $((t+=3)))")
  for s in sample-traveler-a sample-traveler-b sample-traveler-c sample-traveler-d sample-traveler-e; do
    events+=("$(ev traveler.active "$s" $((t+=3)))")
  done
  # Series history: before the Snapshot, so the totals ignore it and only the series shows it.
  # Subjects rotate through a small cast so the active history varies day to day.
  who() { echo "sample-history-$(( ($1 * 7 + $2) % 9 ))"; }
  for (( d = 1; d <= SPREAD_DAYS; d++ )); do
    day=$(( snap - d * 86400 ))
    for (( i = 0; i < (d % 4) + 1; i++ )); do events+=("$(ev trip.created "$(who $d $i)" $(( day + i * 60 )))"); done
    for (( i = 0; i < (d % 3); i++ )); do events+=("$(ev postcard.created "$(who $d $((i + 3)))" $(( day + 30 + i * 60 )))"); done
    if (( d % 5 == 0 )); then events+=("$(ev trip.deleted "$(who $d 0)" $(( day + 45 )))"); fi
    for (( i = 0; i < (d * 3 % 5) + 1; i++ )); do events+=("$(ev traveler.active "$(who $d $((i + 5)))" $(( day + 90 + i * 60 )))"); done
  done

  if [ ${#events[@]} -gt 500 ]; then
    echo "batch has ${#events[@]} Events; the contract allows 500 — use a smaller --spread-days" >&2
    exit 1
  fi
  BODY="{\"events\":[$(IFS=,; echo "${events[*]}")]}"
  printf '%s' "$BODY" > "$LAST"
  echo "== sending ${#events[@]} Events to $BASE (Snapshot $(iso "$snap")) =="
fi

resp=$(curl -s -w '\n%{http_code}' --max-time 60 -X POST "$BASE/api/intake/events" \
  -H 'Content-Type: application/json' -H "X-Intake-Secret: $SECRET" --data-binary @"$LAST")
status="${resp##*$'\n'}"
json="${resp%$'\n'*}"

echo "HTTP $status"
if [ "$status" != 200 ]; then
  echo "$json"
  exit 1
fi
# One verdict per line, in request order.
echo "$json" | sed 's/^{"results":\[//; s/\]}$//; s/},{/}\n{/g'
for s in accepted duplicate rejected; do
  printf '%-10s %s\n' "$s" "$(echo "$json" | grep -o "\"status\":\"$s\"" | wc -l | tr -d ' ')"
done
