#!/usr/bin/env bash
# Prints "line=<pct> branch=<pct>" from the report-level counters of a
# Kover/JaCoCo XML report. A counter that is missing or has 0 total is "n/a".
# Usage: scripts/kover-summary.sh <report.xml>
set -euo pipefail

file="${1:?usage: kover-summary.sh <report.xml>}"
[[ -f "$file" ]] || { echo "kover-summary: no such file: $file" >&2; exit 1; }

# Tokenize on "<" and track element depth so only direct children of <report>
# (depth 1) are read, never package/class/method counters.
LC_ALL=C awk '
function attr(tag, name,    re, v) {
  re = name "=\"[0-9]+\""
  if (match(tag, re)) {
    v = substr(tag, RSTART + length(name) + 2, RLENGTH - length(name) - 3)
    return v + 0
  }
  return 0
}
function pct(type,    t) {
  t = missed[type] + covered[type]
  if (!(type in missed) || t == 0) return "n/a"
  return sprintf("%.1f", covered[type] * 100 / t)
}
BEGIN { RS = "<"; depth = 0 }
{
  tag = $0
  gsub(/[\n\r\t]/, " ", tag)
  if (tag == "" || tag ~ /^[?!]/) next
  if (tag ~ /^\//) { depth--; next }
  selfclose = (tag ~ /\/>?[ ]*$/ || tag ~ /\/[ ]*>/)
  if (tag ~ /^counter[ ]/ && depth == 1) {
    match(tag, /type="[A-Z]+"/)
    type = substr(tag, RSTART + 6, RLENGTH - 7)
    missed[type] = attr(tag, "missed")
    covered[type] = attr(tag, "covered")
  }
  if (!selfclose) depth++
}
END { printf "line=%s branch=%s\n", pct("LINE"), pct("BRANCH") }
' "$file"
