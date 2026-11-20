#!/usr/bin/env bash
# Prints "score=<pct> killed=<n> total=<n>" from a PIT mutations.xml.
# KILLED and TIMED_OUT are killed; NON_VIABLE mutants are not counted at all.
# Usage: scripts/pit-summary.sh <mutations.xml>
set -euo pipefail

file="${1:?usage: pit-summary.sh <mutations.xml>}"
[[ -f "$file" ]] || { echo "pit-summary: no such file: $file" >&2; exit 1; }

LC_ALL=C awk '
/<mutation / {
  if ($0 ~ /status=.NON_VIABLE./) next
  total++
  if ($0 ~ /status=.(KILLED|TIMED_OUT)./) killed++
}
END {
  score = total ? sprintf("%.0f", killed * 100 / total) : "n/a"
  printf "score=%s killed=%d total=%d\n", score, killed, total
}' "$file"
