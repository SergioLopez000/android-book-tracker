#!/usr/bin/env bash
# Parses the expiry of PROJECT_TOKEN from raw HTTP response headers read on stdin.
# Usage: curl -s -D - -o /dev/null ... | scripts/token-expiry.sh
# Prints three lines: state=<s>, days=<n|>, expires=<raw value|>.
#   state: ok | expiring (< 14 days) | expired | invalid (HTTP 401) | missing-header | unparseable
# Exit 0 for every parsed state; 2 only on a usage error. NOW_EPOCH overrides the clock (tests).
# The header value is untrusted: it is validated against a strict date shape and converted by
# plain arithmetic inside awk, so it never reaches a shell, eval or the date command. This also
# makes the script independent of GNU/BSD date. The token is never read or printed.
set -euo pipefail

if [ "$#" -ne 0 ]; then
  echo "usage: token-expiry.sh < headers (env: NOW_EPOCH)" >&2
  exit 2
fi

now="${NOW_EPOCH:-$(date +%s)}"
case "$now" in
  '' | *[!0-9]*)
    echo "token-expiry.sh: NOW_EPOCH must be an integer" >&2
    exit 2
    ;;
esac

LC_ALL=C awk -v now="$now" -v threshold=14 '
  function leap(y) { return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0 }
  function mdays(y, m) {
    if (m == 2) return leap(y) ? 29 : 28
    return (m == 4 || m == 6 || m == 9 || m == 11) ? 30 : 31
  }
  # Days since 1970-01-01 for a valid civil date (y >= 1970).
  function days_from_civil(y, m, d,   era, yoe, doy, doe) {
    if (m <= 2) y -= 1
    era = int(y / 400)
    yoe = y - era * 400
    doy = int((153 * (m + (m > 2 ? -3 : 9)) + 2) / 5) + d - 1
    doe = yoe * 365 + int(yoe / 4) - int(yoe / 100) + doy
    return era * 146097 + doe - 719468
  }
  # Seconds since the epoch for a validated value, or -1 when it is not a real date.
  function to_epoch(v,   y, mo, d, h, mi, s, rest, sign, oh, om, off) {
    if (v !~ /^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]( [0-9][0-9]:[0-9][0-9]:[0-9][0-9]( ?(UTC|GMT|Z|[+-][0-9][0-9]:?[0-9][0-9]))?)?$/) return -1
    y = substr(v, 1, 4) + 0; mo = substr(v, 6, 2) + 0; d = substr(v, 9, 2) + 0
    h = 0; mi = 0; s = 0; off = 0
    if (length(v) > 10) { h = substr(v, 12, 2) + 0; mi = substr(v, 15, 2) + 0; s = substr(v, 18, 2) + 0 }
    if (y < 1970 || mo < 1 || mo > 12 || d < 1 || d > mdays(y, mo) || h > 23 || mi > 59 || s > 59) return -1
    rest = substr(v, 20); sub(/^ /, "", rest)
    if (rest ~ /^[+-]/) {
      sign = (substr(rest, 1, 1) == "-") ? -1 : 1
      oh = substr(rest, 2, 2) + 0
      om = substr(rest, 4, 1) == ":" ? substr(rest, 5, 2) + 0 : substr(rest, 4, 2) + 0
      if (oh > 23 || om > 59) return -1
      off = sign * (oh * 3600 + om * 60)
    }
    return days_from_civil(y, mo, d) * 86400 + h * 3600 + mi * 60 + s - off
  }
  function emit(state, days, expires) {
    printf "state=%s\ndays=%s\nexpires=%s\n", state, days, expires
    exit 0
  }
  BEGIN { name = "github-authentication-token-expiration:" }
  {
    sub(/\r$/, "")
    low = tolower($0)
    if (!seen_status && low ~ /^http\//) {
      seen_status = 1
      split($0, parts, " ")
      if (parts[2] == "401") unauthorized = 1
      next
    }
    if (!found && index(low, name) == 1) {
      found = 1
      val = substr($0, length(name) + 1)
      gsub(/^[ \t]+|[ \t]+$/, "", val)
    }
  }
  END {
    if (unauthorized) emit("invalid", "", "")
    if (!found) emit("missing-header", "", "")
    raw = val
    gsub(/[^ -~]/, "?", raw)
    raw = substr(raw, 1, 100)
    exp_epoch = to_epoch(val)
    if (exp_epoch < 0) emit("unparseable", "", raw)
    diff = exp_epoch - now
    if (diff <= 0) emit("expired", "", raw)
    days = int(diff / 86400)
    emit(days >= threshold ? "ok" : "expiring", days, raw)
  }
'
