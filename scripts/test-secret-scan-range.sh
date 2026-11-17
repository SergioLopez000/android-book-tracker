#!/usr/bin/env bash
# Table-driven harness for scripts/secret-scan-range.sh.
# Each case runs the script inside a throwaway git repo with the given env and
# expects "<stdout>|<exit>|<stderr>". Exit "nz" means any non-zero code.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="$root/scripts/secret-scan-range.sh"
if [[ ! -x "$script" ]]; then
  echo "FAIL: range script not found or not executable: $script"
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Throwaway repo with two commits: c1 and c2 exist, FAR (valid hex) does not.
repo="$tmp/repo"
git init -q "$repo"
git -C "$repo" -c user.name=t -c user.email=t@t -c commit.gpgsign=false commit -q --allow-empty -m c1
git -C "$repo" -c user.name=t -c user.email=t@t -c commit.gpgsign=false commit -q --allow-empty -m c2
c1="$(git -C "$repo" rev-parse HEAD~1)"
c2="$(git -C "$repo" rev-parse HEAD)"
far="0123456789abcdef0123456789abcdef01234567"
zeros="0000000000000000000000000000000000000000"

failures=0
# check <name> <want_out> <want_exit|nz> <want_err: any|none> -- VAR=value...
check() {
  local name="$1" want_out="$2" want_exit="$3" want_err="$4" out err code=0
  shift 5
  err="$tmp/$name.err"
  out="$(cd "$repo" && env -i PATH="$PATH" "$@" "$script" 2>"$err")" || code=$?
  local bad=0
  [[ "$out" == "$want_out" ]] || bad=1
  if [[ "$want_exit" == "nz" ]]; then
    [[ "$code" != 0 ]] || bad=1
  else
    [[ "$code" == "$want_exit" ]] || bad=1
  fi
  if [[ "$want_err" == "any" ]]; then
    [[ -s "$err" ]] || bad=1
  else
    [[ ! -s "$err" ]] || bad=1
  fi
  if [[ "$bad" == 1 ]]; then
    echo "FAIL: $name: got '$out' (exit $code, stderr $(wc -c <"$err") bytes), want '$want_out' (exit $want_exit, stderr $want_err)"
    failures=$((failures + 1))
  fi
}

# Pull requests scan BASE..HEAD.
check pr-range "$c1..$c2" 0 none -- EVENT_NAME=pull_request BASE_SHA="$c1" HEAD_SHA="$c2"

# Pushes scan BEFORE..AFTER.
check push-range "$c1..$c2" 0 none -- EVENT_NAME=push BEFORE_SHA="$c1" AFTER_SHA="$c2"

# New branch (all-zeros BEFORE): head commit only.
check push-new-branch "-1 $c2" 0 none -- EVENT_NAME=push BEFORE_SHA="$zeros" AFTER_SHA="$c2"

# Valid-looking BEFORE that is not in the clone: fail closed, never head-only.
check push-unreachable-before "" nz any -- EVENT_NAME=push BEFORE_SHA="$far" AFTER_SHA="$c2"

# Malformed SHAs (short, uppercase, option-like) exit 2 before any git call.
check push-short-before "" 2 any -- EVENT_NAME=push BEFORE_SHA="${c1:0:7}" AFTER_SHA="$c2"
check push-uppercase-after "" 2 any -- EVENT_NAME=push BEFORE_SHA="$c1" AFTER_SHA="${c2^^}"
check pr-option-injection "" 2 any -- EVENT_NAME=pull_request BASE_SHA="--output=x" HEAD_SHA="$c2"

# Unknown or empty event names are rejected.
check empty-event "" nz any -- EVENT_NAME= BEFORE_SHA="$c1" AFTER_SHA="$c2"
check unknown-event "" nz any -- EVENT_NAME=schedule BEFORE_SHA="$c1" AFTER_SHA="$c2"

if [[ "$failures" -gt 0 ]]; then
  echo "$failures case(s) failed"
  exit 1
fi
echo "all cases passed"
