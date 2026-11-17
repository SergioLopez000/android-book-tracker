#!/usr/bin/env bash
# Prints the issue number a pull request belongs to, or nothing.
# Usage: scripts/resolve-issue-number.sh <head-branch> <pr-body>
# 1. A branch named <type>/<N>-<slug> wins.
# 2. Otherwise the first "Closes|Fixes|Resolves #N" in the body (any
#    inflection, case-insensitive). "Refs #N" never counts.
# Inputs are only matched with [[ =~ ]]; nothing is evaluated or executed.
set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "usage: resolve-issue-number.sh <head-branch> <pr-body>" >&2
  exit 2
fi
branch="$1"
body="$2"

valid='^[1-9][0-9]*$'
branch_re='^[a-z]+/([1-9][0-9]*)-'
keyword_re='(^|[^[:alnum:]_])(close[sd]?|fix(e[sd])?|resolve[sd]?):?[[:space:]]+#([0-9]+[[:alnum:]_]*)'

if [[ "$branch" =~ $branch_re ]]; then
  echo "${BASH_REMATCH[1]}"
  exit 0
fi

while IFS= read -r line || [[ -n "$line" ]]; do
  line="$(tr '[:upper:]' '[:lower:]' <<<"${line%$'\r'}")"
  if [[ "$line" =~ $keyword_re ]]; then
    number="${BASH_REMATCH[4]}"
    if [[ "$number" =~ $valid ]]; then
      echo "$number"
    fi
    exit 0
  fi
done <<<"$body"
