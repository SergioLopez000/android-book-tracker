#!/usr/bin/env bash
# Table-driven harness for scripts/check-commit-msg.sh.
# Each case is "message|expected-exit"; \n in a message becomes a newline.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
validator="$root/scripts/check-commit-msg.sh"
if [[ ! -x "$validator" ]]; then
  echo "FAIL: validator not found or not executable: $validator"
  exit 1
fi

long_subject="$(printf 'a%.0s' {1..101})"
ok_subject="$(printf 'a%.0s' {1..100})"

cases=(
  "feat: add x|0"
  "feat(scope): add x|0"
  "feat!: add x|0"
  "feat(scope)!: add x|0"
  "fix: a|0"
  "docs: a|0"
  "style: a|0"
  "refactor: a|0"
  "perf: a|0"
  "test: a|0"
  "build: a|0"
  "ci: a|0"
  "chore: a|0"
  "revert: a|0"
  "feat(a.b_c/d-1): add x|0"
  "Merge branch 'main' into x|0"
  "Revert \"feat: add x\"|0"
  "fixup! feat: add x|0"
  "squash! feat: add x|0"
  "amend! feat: add x|0"
  "# a comment\nfeat: add x|0"
  "\n\nfeat: add x\n\nbody text|0"
  "feat: $ok_subject|0"
  "|1"
  "# only a comment|1"
  "updated stuff|1"
  "feature: add x|1"
  "feat: |1"
  "feat(Scope): add x|1"
  "feat:add x|1"
  "feat: $long_subject|1"
)

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

failures=0
for row in "${cases[@]}"; do
  message="${row%|*}"
  expected="${row##*|}"
  printf '%b\n' "$message" >"$tmp"
  actual=0
  "$validator" "$tmp" >/dev/null 2>&1 || actual=$?
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: expected exit $expected, got $actual for: $message"
    failures=$((failures + 1))
  fi
done

if ((failures > 0)); then
  echo "$failures of ${#cases[@]} cases failed"
  exit 1
fi
echo "all ${#cases[@]} cases passed"
