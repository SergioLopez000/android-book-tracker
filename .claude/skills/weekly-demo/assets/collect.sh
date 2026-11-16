#!/usr/bin/env bash
# Print a JSON snapshot of one demo week from GitHub (raw data for the week JSON).
# Usage: collect.sh [START_DATE]   START_DATE = Monday, YYYY-MM-DD (default: this week's Monday,
#        or last week's Monday when run on a weekend before the demo week starts).
# Env:   REPO (default SergioLopez000/android-book-tracker), PROJECT_OWNER (SergioLopez000),
#        PROJECT_NUMBER (2), DEV_BRANCH (dev). Requires: gh (authenticated), jq, git.
set -euo pipefail
REPO="${REPO:-SergioLopez000/android-book-tracker}"
OWNER="${PROJECT_OWNER:-SergioLopez000}"
PROJECT="${PROJECT_NUMBER:-2}"
DEV="${DEV_BRANCH:-dev}"

if [[ $# -ge 1 ]]; then
  start="$1"
else
  dow=$(date +%u)  # 1=Mon .. 7=Sun
  start=$(date -v-"$((dow - 1))"d +%F 2>/dev/null || date -d "-$((dow - 1)) days" +%F)
fi
# Monday..Sunday window; the demo covers Monday..Friday.
end=$(date -j -v+6d -f %F "$start" +%F 2>/dev/null || date -d "$start +6 days" +%F)
friday=$(date -j -v+4d -f %F "$start" +%F 2>/dev/null || date -d "$start +4 days" +%F)
iso_week=$(date -j -f %F "$start" +%V 2>/dev/null || date -d "$start" +%V)
iso_year=$(date -j -f %F "$start" +%G 2>/dev/null || date -d "$start" +%G)

closed=$(gh issue list -R "$REPO" --state closed --limit 200 --search "closed:${start}..${end}" \
  --json number,title,closedAt,milestone \
  --jq 'map({number,title,closedAt,milestone:(.milestone.title // null)})')
merged=$(gh pr list -R "$REPO" --state merged --limit 200 --search "merged:${start}..${end}" \
  --json number,title,mergedAt,baseRefName --jq 'map({number,title,mergedAt,base:.baseRefName})')
opened=$(gh issue list -R "$REPO" --state all --limit 200 --search "created:${start}..${end}" \
  --json number --jq 'length')
board=$(gh project item-list "$PROJECT" --owner "$OWNER" --format json --limit 500 \
  --jq '[.items[] | (.status // "No status")] | group_by(.) | map({key: .[0], value: length}) | from_entries')
milestones=$(gh issue list -R "$REPO" --state open --limit 500 --json milestone \
  --jq 'map(.milestone.title // "No milestone") | group_by(.) | map({key: .[0], value: length}) | from_entries')
open_total=$(gh issue list -R "$REPO" --state open --limit 500 --json number --jq 'length')

commits=null
if root=$(git rev-parse --show-toplevel 2>/dev/null); then
  git -C "$root" fetch -q origin "$DEV" 2>/dev/null || true
  commits=$(git -C "$root" rev-list --count --since="${start}T00:00:00" --until="${end}T23:59:59" "origin/$DEV" 2>/dev/null || echo null)
fi

jq -n \
  --arg repo "$REPO" --arg start "$start" --arg friday "$friday" --arg end "$end" \
  --argjson week "$((10#$iso_week))" --argjson year "$iso_year" \
  --argjson closed "$closed" --argjson merged "$merged" --argjson opened "$opened" \
  --argjson board "$board" --argjson milestones "$milestones" --argjson open_total "$open_total" \
  --argjson commits "$commits" --arg dev "$DEV" \
  '{repo:$repo, iso_year:$year, iso_week:$week, start:$start, demo_end:$friday, window_end:$end,
    closed_issues:$closed, merged_prs:$merged, issues_opened:$opened,
    board_status:$board, board_total:($board|add // 0),
    open_issues_by_milestone:$milestones, open_issues_total:$open_total,
    commits_on_dev:$commits, dev_branch:$dev}'
