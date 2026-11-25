#!/usr/bin/env bash
# Opens or edits the single PROJECT_TOKEN reminder (or warning) issue; never closes an issue
# and never comments. Driven only by env vars: STATE, DAYS, EXPIRES, GITHUB_REPOSITORY,
# GITHUB_SERVER_URL, GITHUB_REF_NAME (and GH_TOKEN for gh). States outside the whitelist are
# a no-op, matching the `if:` gate of the notify job. Values reach the issue body only through
# printf '%s' arguments and a body file, never through a shell parse.
set -euo pipefail

state="${STATE-}"
days="${DAYS-}"
expires="${EXPIRES-}"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"

reminder="PROJECT_TOKEN rotation needed"
warning="PROJECT_TOKEN expiry header missing"
runbook="${GITHUB_SERVER_URL-}/${repo}/blob/${GITHUB_REF_NAME-}/docs/secrets.md#runbook"
body_file="$(mktemp)"
trap 'rm -f "$body_file"' EXIT

case "$state" in
expiring)
  title="$reminder"
  printf 'PROJECT_TOKEN expires on %s (%s days left).\n\nRotate it following the runbook: %s\n' "$expires" "$days" "$runbook" >"$body_file"
  ;;
expired)
  title="$reminder"
  printf 'PROJECT_TOKEN has expired (expiry date %s). Board automation is failing.\n\nRotate it following the runbook: %s\n' "$expires" "$runbook" >"$body_file"
  ;;
invalid)
  title="$reminder"
  printf 'GitHub rejected PROJECT_TOKEN (HTTP 401): it is expired, revoked or wrong. Board automation is failing.\n\nRotate it following the runbook: %s\n' "$runbook" >"$body_file"
  ;;
missing-header | unparseable)
  title="$warning"
  printf 'The expiry of PROJECT_TOKEN could not be determined (state: %s). Header value seen:\n\n    %s\n\nCheck the token expiry manually (GitHub settings, Developer settings, Personal access tokens), then run the "Token expiry" workflow manually (workflow_dispatch) to confirm the header format. Runbook: %s\n' "$state" "$expires" "$runbook" >"$body_file"
  ;;
*)
  echo "No notification for state '$state'."
  exit 0
  ;;
esac

gh label create secret-rotation --repo "$repo" --color D93F0B --description "Secret rotation reminders" --force
number="$(gh issue list --repo "$repo" --label secret-rotation --state open --json number,title --limit 100 |
  jq -r --arg title "$title" '[.[] | select(.title == $title)] | first | .number // empty')"
if [ -n "$number" ]; then
  gh issue edit "$number" --repo "$repo" --body-file "$body_file"
else
  gh issue create --repo "$repo" --title "$title" --label secret-rotation --body-file "$body_file"
fi
