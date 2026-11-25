#!/usr/bin/env bash
# Harness for scripts/token-expiry.sh: feeds raw response headers on stdin and asserts the
# printed state. Needs no network and no GNU date (the parser does its own date arithmetic).
# Local or CI-agnostic; the weekly workflow only calls the parser, never this harness.
# shellcheck disable=SC2016 # fixtures contain literal $( ) and backticks
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
parser="$root/scripts/token-expiry.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/work"

failures=0
fail() {
  echo "FAIL: $1"
  failures=$((failures + 1))
}

NOW=946684800 # 2000-01-01 00:00:00 UTC
H='github-authentication-token-expiration'

# run <headers> [now]: sets $out and $status; the parser runs in an empty cwd.
run() {
  set +e
  out="$(cd "$tmp/work" && printf '%b' "$1" | NOW_EPOCH="${2:-$NOW}" bash "$parser" 2>&1)"
  status=$?
  set -e
}

# expect <name> <headers> <state> [days] [expires]: exact state/days/expires, exit 0.
expect() {
  local name="$1" headers="$2" state="$3" days="${4-}" expires="${5-}"
  run "$headers"
  local want
  want="$(printf 'state=%s\ndays=%s\nexpires=%s' "$state" "$days" "$expires")"
  if [ "$status" -ne 0 ]; then
    fail "$name (exit $status: $out)"
  elif [ "$out" != "$want" ]; then
    fail "$name (got '$out', want '$want')"
  fi
}

# --- States ---
expect "14 days left is ok" "HTTP/2 200\n$H: 2000-01-15 00:00:00 UTC\n" ok 14 "2000-01-15 00:00:00 UTC"
expect "13 days left is expiring" "HTTP/2 200\n$H: 2000-01-14 00:00:00 UTC\n" expiring 13 "2000-01-14 00:00:00 UTC"
expect "13 days 23:59:59 left is expiring" "HTTP/2 200\n$H: 2000-01-14 23:59:59 UTC\n" expiring 13 "2000-01-14 23:59:59 UTC"
expect "already past is expired" "HTTP/2 200\n$H: 1999-12-31 00:00:00 UTC\n" expired "" "1999-12-31 00:00:00 UTC"
expect "expiring exactly now is expired" "HTTP/2 200\n$H: 2000-01-01 00:00:00 UTC\n" expired "" "2000-01-01 00:00:00 UTC"
expect "401 is invalid" "HTTP/2 401\nx-other: 1\n" invalid
expect "401 with HTTP/1.1 status line is invalid" "HTTP/1.1 401 Unauthorized\n$H: 2000-02-01 00:00:00 UTC\n" invalid
expect "no header is missing-header" "HTTP/2 200\ncontent-type: application/json\n" missing-header
expect "empty stdin is missing-header" "" missing-header
expect "garbage is unparseable and echoed" "HTTP/2 200\n$H: not-a-date\n" unparseable "" "not-a-date"

# --- Date shapes and zones ---
expect "date only means midnight UTC" "$H: 2000-01-20\n" ok 19 "2000-01-20"
expect "positive offset without colon" "$H: 2000-01-15 02:00:00 +0200\n" ok 14 "2000-01-15 02:00:00 +0200"
expect "negative offset" "$H: 2000-01-14 19:00:00 -0500\n" ok 14 "2000-01-14 19:00:00 -0500"
expect "offset with colon" "$H: 2000-01-15 02:00:00 +02:00\n" ok 14 "2000-01-15 02:00:00 +02:00"
expect "no zone means UTC" "$H: 2000-01-15 00:00:00\n" ok 14 "2000-01-15 00:00:00"
expect "leap day is valid" "$H: 2000-02-29 00:00:00 UTC\n" ok 59 "2000-02-29 00:00:00 UTC"
expect "non-leap Feb 29 is unparseable" "$H: 2001-02-29 00:00:00 UTC\n" unparseable "" "2001-02-29 00:00:00 UTC"
expect "month 13 is unparseable" "$H: 2000-13-01 00:00:00 UTC\n" unparseable "" "2000-13-01 00:00:00 UTC"
expect "day 31 in a 30-day month is unparseable" "$H: 2000-04-31 00:00:00 UTC\n" unparseable "" "2000-04-31 00:00:00 UTC"
expect "hour 24 is unparseable" "$H: 2000-01-15 24:00:00 UTC\n" unparseable "" "2000-01-15 24:00:00 UTC"
expect "empty value is unparseable" "$H:\n" unparseable
expect "blank value is unparseable" "$H:    \n" unparseable
expect "value is trimmed" "$H:    2000-01-20 00:00:00 UTC   \n" ok 19 "2000-01-20 00:00:00 UTC"

# --- Robustness ---
expect "CRLF line endings" "HTTP/2 200\r\n$H: 2000-01-15 00:00:00 UTC\r\n\r\n" ok 14 "2000-01-15 00:00:00 UTC"
expect "upper-case header name" "GITHUB-AUTHENTICATION-TOKEN-EXPIRATION: 2000-01-15 00:00:00 UTC\n" ok 14 "2000-01-15 00:00:00 UTC"
expect "mixed-case header name" "Github-Authentication-Token-Expiration: 2000-01-15 00:00:00 UTC\n" ok 14 "2000-01-15 00:00:00 UTC"
expect "first duplicate header wins" "$H: 2000-01-20 00:00:00 UTC\n$H: 2000-01-02 00:00:00 UTC\n" ok 19 "2000-01-20 00:00:00 UTC"
expect "similar header name is ignored" "x-$H: 2000-01-20 00:00:00 UTC\n" missing-header

# A hostile value is echoed literally and nothing is executed from it.
evil='$(touch x) "q" ; rm'
expect "shell metacharacters are unparseable and echoed literally" "$H: $evil\n" unparseable "" "$evil"
if [ -e "$tmp/work/x" ]; then
  fail "hostile value executed a command (marker file created)"
fi
expect "backticks are unparseable" "$H: \`touch y\`\n" unparseable "" '`touch y`'
if [ -e "$tmp/work/y" ]; then
  fail "backtick value executed a command (marker file created)"
fi

# The output never carries a token fixture that appears elsewhere in the input.
run "HTTP/2 200\nauthorization: Bearer ghp_FIXTURE0123456789\n$H: 2000-01-15 00:00:00 UTC\n"
if grep -q 'ghp_FIXTURE' <<<"$out"; then
  fail "output leaks the token fixture"
fi

# --- Usage ---
set +e
bash "$parser" --bogus </dev/null >/dev/null 2>&1
usage_status=$?
NOW_EPOCH=abc bash "$parser" </dev/null >/dev/null 2>&1
epoch_status=$?
set -e
[ "$usage_status" -ne 0 ] || fail "unknown argument must exit non-zero"
[ "$epoch_status" -ne 0 ] || fail "non-numeric NOW_EPOCH must exit non-zero"

if [ "$failures" -ne 0 ]; then
  echo "$failures check(s) failed"
  exit 1
fi
echo "All token-expiry checks passed"
