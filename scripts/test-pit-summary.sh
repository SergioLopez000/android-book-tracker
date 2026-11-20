#!/usr/bin/env bash
# Table-driven harness for scripts/pit-summary.sh.
# Each case writes a mutations.xml fixture and expects "<stdout>|<exit>".
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
summary="$root/scripts/pit-summary.sh"
if [[ ! -x "$summary" ]]; then
  echo "FAIL: summary script not found or not executable: $summary"
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

failures=0
check() {
  local name="$1" xml="$2" want_out="$3" want_exit="$4" file out code=0
  file="$tmp/$name.xml"
  printf '%s\n' "$xml" >"$file"
  out="$("$summary" "$file" 2>/dev/null)" || code=$?
  if [[ "$out" != "$want_out" || "$code" != "$want_exit" ]]; then
    echo "FAIL: $name: got '$out' (exit $code), want '$want_out' (exit $want_exit)"
    failures=$((failures + 1))
  fi
}

m() { printf "<mutation detected='%s' status='%s'><sourceFile>A.kt</sourceFile></mutation>\n" "$1" "$2"; }

# KILLED and TIMED_OUT count as killed; NON_VIABLE is not a mutant that could survive.
check mixed "<mutations>
$(m true KILLED)
$(m true TIMED_OUT)
$(m false SURVIVED)
$(m false NO_COVERAGE)
$(m false NON_VIABLE)
</mutations>" "score=50 killed=2 total=4" 0
check all-killed "<mutations>
$(m true KILLED)
</mutations>" "score=100 killed=1 total=1" 0
check no-mutants "<mutations>
</mutations>" "score=n/a killed=0 total=0" 0

if [[ -n "$("$summary" "$tmp/missing.xml" 2>/dev/null || true)" ]]; then
  echo "FAIL: missing file must print nothing"
  failures=$((failures + 1))
fi
if "$summary" "$tmp/missing.xml" 2>/dev/null; then
  echo "FAIL: missing file must exit non-zero"
  failures=$((failures + 1))
fi

if ((failures > 0)); then
  echo "$failures failure(s)"
  exit 1
fi
echo "OK"
