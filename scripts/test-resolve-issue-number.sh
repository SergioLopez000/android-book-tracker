#!/usr/bin/env bash
# Table-driven harness for scripts/resolve-issue-number.sh.
# Each case passes <branch> <body> and expects "<stdout>|<exit>".
# Bash 3.2 safe (macOS): no associative arrays, no mapfile, no case conversion.
# The injection cases use deliberately unexpanded literals.
# shellcheck disable=SC2016
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="$root/scripts/resolve-issue-number.sh"
if [[ ! -x "$script" ]]; then
  echo "FAIL: resolver not found or not executable: $script"
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/work"

failures=0
# check <name> <want_out> <want_exit> <branch> <body>
check() {
  local name="$1" want_out="$2" want_exit="$3" out code=0
  out="$(cd "$tmp/work" && "$script" "$4" "$5" 2>/dev/null)" || code=$?
  if [[ "$out" != "$want_out" || "$code" != "$want_exit" ]]; then
    echo "FAIL: $name: got '$out' (exit $code), want '$want_out' (exit $want_exit)"
    failures=$((failures + 1))
  fi
}

# Branch precedence.
check branch-feat 12 0 "feat/12-x" ""
check branch-chore 5 0 "chore/5-issue-forms-board" ""
check branch-wins-over-body 7 0 "fix/7-x" "Closes #9"
check branch-large-number 123 0 "feature/123-big" ""
check branch-other-prefix 4 0 "hotfix/4-y" "Fixes #8"

# Body keywords when the branch has no number.
check body-closes 9 0 "dependabot/gradle/x" "Closes #9"
check body-fixes-lower 3 0 "x" "fixes #3"
check body-resolved-upper 4 0 "x" "RESOLVED #4"
check body-close-colon 8 0 "x" "Close: #8"
check body-second-line 6 0 "x" $'Summary\n\nResolves #6'
check body-first-match-only 3 0 "x" "Fixes #3 and closes #4"
check body-crlf 7 0 "x" $'Intro\r\nFixes #7\r\n'
check body-fix-singular 2 0 "x" "Fix #2"
check body-closed 11 0 "x" "closed #11"

# Refs and look-alikes never count.
check refs-ignored "" 0 "x" "Refs #5"
check cross-repo-ignored "" 0 "x" "Closes other/repo#5"
check word-prefix-ignored "" 0 "x" "prefixes #4"
check zero-ignored "" 0 "x" "Closes #0"
check leading-zero-ignored "" 0 "x" "Closes #05"
check trailing-letters-ignored "" 0 "x" "Closes #12abc"
check no-reference "" 0 "x" "Just a description"
check empty-inputs "" 0 "" ""

# Branches that do not match fall back to the body.
check branch-no-slug-falls-back 9 0 "feat/12" "Closes #9"
check branch-uppercase-falls-back 9 0 "Feat/12-x" "Closes #9"
check branch-number-in-slug-only 9 0 "feat/x-12" "Closes #9"

# Injection and option-like input are inert. The literals are deliberately unexpanded.
check injection-branch 12 0 'feat/12-$(touch p)' 'Body `touch q` and $(touch r)'
check injection-body "" 0 "x" 'Closes `touch p`; $(touch q) #1'
check option-like-branch "" 0 "--help" "-n"
check option-like-body 5 0 "feat/5-x" "--version"
for f in p q r; do
  if [[ -e "$tmp/work/$f" ]]; then
    echo "FAIL: injection created file $f"
    failures=$((failures + 1))
  fi
done

# Wrong argument count.
code=0
(cd "$tmp/work" && "$script" 2>/dev/null) || code=$?
[[ "$code" == 2 ]] || { echo "FAIL: argc-0: exit $code, want 2"; failures=$((failures + 1)); }
code=0
(cd "$tmp/work" && "$script" "feat/1-x" 2>/dev/null) || code=$?
[[ "$code" == 2 ]] || { echo "FAIL: argc-1: exit $code, want 2"; failures=$((failures + 1)); }
code=0
(cd "$tmp/work" && "$script" "a" "b" "c" 2>/dev/null) || code=$?
[[ "$code" == 2 ]] || { echo "FAIL: argc-3: exit $code, want 2"; failures=$((failures + 1)); }

if [[ "$failures" -gt 0 ]]; then
  echo "$failures case(s) failed"
  exit 1
fi
echo "all cases passed"
