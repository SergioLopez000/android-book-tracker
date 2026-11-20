#!/usr/bin/env bash
# Table-driven harness for scripts/kover-summary.sh.
# Each case writes an XML fixture and expects "<stdout>|<exit>".
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
summary="$root/scripts/kover-summary.sh"
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

# Only counters that are direct children of <report> count; nested ones
# (package, class, method) must be ignored.
check report-level-only \
  '<?xml version="1.0"?><!DOCTYPE report><report name="x">
<package name="p"><class name="c"><counter type="LINE" missed="99" covered="1"/></class>
<counter type="BRANCH" missed="99" covered="1"/></package>
<counter type="BRANCH" missed="1" covered="3"/>
<counter type="LINE" missed="25" covered="75"/>
</report>' 'line=75.0 branch=75.0' 0

check one-decimal-rounding \
  '<report name="x"><counter type="LINE" missed="1" covered="2"/><counter type="BRANCH" missed="1" covered="1"/></report>' \
  'line=66.7 branch=50.0' 0

check missing-branch-counter \
  '<report name="x"><counter type="LINE" missed="0" covered="10"/></report>' 'line=100.0 branch=n/a' 0

check zero-over-zero-branch \
  '<report name="x"><counter type="LINE" missed="1" covered="1"/><counter type="BRANCH" missed="0" covered="0"/></report>' \
  'line=50.0 branch=n/a' 0

check zero-over-zero-both \
  '<report name="x"><counter type="LINE" missed="0" covered="0"/><counter type="BRANCH" missed="0" covered="0"/></report>' \
  'line=n/a branch=n/a' 0

check no-counters \
  '<report name="x"></report>' 'line=n/a branch=n/a' 0

check multiline-counters \
  '<report name="x">
<counter
  type="LINE"
  missed="1"
  covered="3"/>
</report>' 'line=75.0 branch=n/a' 0

# A JVM (non-Android) Kover report: same JaCoCo-style layout, report-level counters
# last, nested method/class/sourcefile counters and <line> elements to be ignored.
check jvm-report \
  '<?xml version="1.0" ?>
<report name="Kover Gradle Plugin XML report for :">
<package name="com/example/backend">
<class name="com/example/backend/App" sourcefilename="App.kt">
<method name="&lt;init&gt;" desc="()V">
<counter type="BRANCH" missed="9" covered="9"/>
<counter type="LINE" missed="9" covered="1"/>
</method>
<counter type="LINE" missed="9" covered="1"/>
</class>
<sourcefile name="App.kt">
<line nr="3" mi="0" ci="2" mb="0" cb="0"/>
<counter type="LINE" missed="9" covered="1"/>
</sourcefile>
<counter type="LINE" missed="9" covered="1"/>
</package>
<counter type="INSTRUCTION" missed="8" covered="64"/>
<counter type="BRANCH" missed="2" covered="6"/>
<counter type="LINE" missed="1" covered="13"/>
<counter type="METHOD" missed="1" covered="8"/>
</report>' 'line=92.9 branch=75.0' 0

check not-xml \
  'this is not a report' 'line=n/a branch=n/a' 0

missing_out=""
missing_code=0
missing_out="$("$summary" "$tmp/does-not-exist.xml" 2>/dev/null)" || missing_code=$?
if [[ -n "$missing_out" || "$missing_code" != 1 ]]; then
  echo "FAIL: missing-file: got '$missing_out' (exit $missing_code), want '' (exit 1)"
  failures=$((failures + 1))
fi

usage_code=0
"$summary" >/dev/null 2>&1 || usage_code=$?
if [[ "$usage_code" != 1 ]]; then
  echo "FAIL: no-args: got exit $usage_code, want 1"
  failures=$((failures + 1))
fi

if ((failures > 0)); then
  echo "$failures case(s) failed"
  exit 1
fi
echo "All kover-summary cases passed"
