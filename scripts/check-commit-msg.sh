#!/usr/bin/env bash
# Validates a commit message file against Conventional Commits.
# Usage: scripts/check-commit-msg.sh <commit-message-file>
set -euo pipefail

file="${1:?usage: check-commit-msg.sh <commit-message-file>}"

subject=""
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "${line//[[:space:]]/}" ]] && continue
  [[ "$line" == \#* ]] && continue
  subject="$line"
  break
done <"$file"

types='feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert'
conventional="^(${types})(\\([a-z0-9._/-]+\\))?!?: [^ ].{0,99}\$"

if [[ -n "$subject" ]] && {
  [[ "$subject" =~ ^Merge\  ]] ||
    [[ "$subject" =~ ^Revert\ \" ]] ||
    [[ "$subject" =~ ^(fixup|squash|amend)!\  ]] ||
    [[ "$subject" =~ $conventional ]]
}; then
  exit 0
fi

cat >&2 <<'MSG'
Invalid commit message. Expected Conventional Commits:

  <type>(<optional-scope>)!: <subject up to 100 characters>

Types: feat fix docs style refactor perf test build ci chore revert
Scope: lowercase letters, digits, ., _, / or -.
Examples:
  feat(list): add reading status filter
  fix!: drop legacy export
MSG
exit 1
