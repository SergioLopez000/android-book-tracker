#!/usr/bin/env bash
# Harness for scripts/token-expiry-notify.sh and scripts/token-expiry-read.sh. A fake `gh`
# and a fake `curl` on PATH log every invocation and serve canned data; jq is the real one.
# Needs no network. Local only (never in CI); the workflow only calls the two scripts.
# shellcheck disable=SC2016 # fixtures contain literal $( ) and backticks
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
notify="$root/scripts/token-expiry-notify.sh"
reader="$root/scripts/token-expiry-read.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin" "$tmp/work"

failures=0
fail() {
  echo "FAIL: $1"
  failures=$((failures + 1))
}

# Fake gh: logs argv; `issue list` serves $GH_LIST_FILE; create/edit snapshot the body file.
cat >"$tmp/bin/gh" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$FAKE_LOG"
if [ "$1 $2" = "issue list" ]; then cat "$GH_LIST_FILE"; fi
while [ $# -gt 0 ]; do
  if [ "$1" = "--body-file" ]; then cp "$2" "$FAKE_BODY"; fi
  shift
done
FAKE
# Fake curl: logs argv and the stdin config, then serves canned response headers.
cat >"$tmp/bin/curl" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$FAKE_LOG"
cat >"$FAKE_STDIN"
printf 'HTTP/2 200\r\ngithub-authentication-token-expiration: %s\r\n' "$CURL_EXPIRY"
FAKE
chmod +x "$tmp/bin/gh" "$tmp/bin/curl"

REMINDER='PROJECT_TOKEN rotation needed'
WARNING='PROJECT_TOKEN expiry header missing'
HOSTILE='$(touch marker) `x`'

# notify <state> <list-json> [expires]: runs the notify script in an empty cwd.
notify() {
  : >"$tmp/log"
  rm -f "$tmp/body" "$tmp/work/marker"
  printf '%s' "$2" >"$tmp/list.json"
  set +e
  out="$(cd "$tmp/work" && PATH="$tmp/bin:$PATH" FAKE_LOG="$tmp/log" FAKE_BODY="$tmp/body" \
    GH_LIST_FILE="$tmp/list.json" GITHUB_REPOSITORY=o/r GITHUB_SERVER_URL=https://h GITHUB_REF_NAME=main \
    STATE="$1" DAYS=5 EXPIRES="${3-2000-01-06}" bash "$notify" 2>&1)"
  status=$?
  set -e
}

count() { grep -c -- "$1" "$tmp/log" || true; }

# expect_create <name> <state> <title>: no open issue, so exactly one create.
expect_create() {
  local name="$1" state="$2" title="$3"
  notify "$state" '[]'
  if [ "$status" -ne 0 ]; then fail "$name (exit $status: $out)"; return; fi
  [ "$(count '^issue create ')" = 1 ] || fail "$name: want exactly one create"
  [ "$(count '^issue edit ')" = 0 ] || fail "$name: unexpected edit"
  grep -q -- "--title $title --label secret-rotation" "$tmp/log" || fail "$name: create lacks title '$title'"
}

# expect_edit <name> <state> <title> <number>: an open issue exists, so edit it, never create.
expect_edit() {
  local name="$1" state="$2" title="$3" number="$4"
  notify "$state" "[{\"number\":7,\"title\":\"Other\"},{\"number\":$number,\"title\":\"$title\"}]"
  if [ "$status" -ne 0 ]; then fail "$name (exit $status: $out)"; return; fi
  [ "$(count '^issue create ')" = 0 ] || fail "$name: created a duplicate"
  [ "$(count "^issue edit $number ")" = 1 ] || fail "$name: want exactly one edit of #$number"
}

expect_create "expiring opens the reminder" expiring "$REMINDER"
grep -q '5 days left' "$tmp/body" || fail "expiring body lacks the days left"
expect_create "expired opens the reminder" expired "$REMINDER"
grep -q 'has expired' "$tmp/body" || fail "expired body lacks the expiry wording"
expect_create "invalid opens the reminder" invalid "$REMINDER"
grep -q 'rejected PROJECT_TOKEN' "$tmp/body" || fail "invalid body lacks the rejection wording"
expect_create "missing-header opens the warning" missing-header "$WARNING"
expect_create "unparseable opens the warning" unparseable "$WARNING"
[ "$REMINDER" != "$WARNING" ] || fail "reminder and warning titles must differ"

expect_edit "expiring edits the open reminder" expiring "$REMINDER" 42
expect_edit "expired edits the open reminder" expired "$REMINDER" 42
expect_edit "invalid edits the open reminder" invalid "$REMINDER" 42
expect_edit "missing-header edits the open warning" missing-header "$WARNING" 43
expect_edit "unparseable edits the open warning" unparseable "$WARNING" 43

# An open reminder must not be reused for the warning (distinct title), and vice versa.
notify missing-header "[{\"number\":42,\"title\":\"$REMINDER\"}]"
[ "$(count '^issue create ')" = 1 ] && [ "$(count '^issue edit ')" = 0 ] || fail "warning must not edit the reminder issue"
notify expiring "[{\"number\":43,\"title\":\"$WARNING\"}]"
[ "$(count '^issue create ')" = 1 ] && [ "$(count '^issue edit ')" = 0 ] || fail "reminder must not edit the warning issue"

# Gating: states outside the whitelist do nothing at all.
for state in ok skipped bogus ""; do
  notify "$state" '[]'
  if [ "$status" -ne 0 ] || [ -s "$tmp/log" ]; then fail "state '$state' must be a no-op (exit $status)"; fi
done

# Invariants over every invocation of the cases above.
for state in expiring expired invalid missing-header unparseable; do
  notify "$state" '[]'
  if grep -Eq '^issue (close|comment)' "$tmp/log"; then fail "$state: closed or commented"; fi
  while IFS= read -r line; do
    case "$line" in
    "issue "* | "label "*) case "$line" in *"--repo o/r"*) ;; *) fail "$state: no --repo in '$line'" ;; esac ;;
    esac
  done <"$tmp/log"
  grep -q '^issue create .*--body-file ' "$tmp/log" || fail "$state: create lacks --body-file"
  grep -q '^label create secret-rotation .*--force' "$tmp/log" || fail "$state: label create lacks --force"
done
notify expiring '[{"number":42,"title":"PROJECT_TOKEN rotation needed"}]'
grep -q '^issue edit 42 .*--body-file ' "$tmp/log" || fail "edit lacks --body-file"

# Hostile expiry value: appears literally in the body, executes nothing.
for state in missing-header unparseable expiring expired; do
  notify "$state" '[]' "$HOSTILE"
  [ ! -e "$tmp/work/marker" ] || fail "$state: hostile value executed"
  grep -qF -- "$HOSTILE" "$tmp/body" || fail "$state: hostile value not literal in the body"
  grep -qF -- "touch marker" "$tmp/log" && fail "$state: hostile value reached argv" || true
done

# read-expiry: the empty-token path skips without calling curl; the token never reaches argv.
# read <token> <expiry>: runs the reader, sets $out (GITHUB_OUTPUT content).
read_expiry() {
  : >"$tmp/log"
  : >"$tmp/gh_out"
  : >"$tmp/stdin"
  set +e
  stdout="$(cd "$tmp/work" && PATH="$tmp/bin:$PATH" FAKE_LOG="$tmp/log" FAKE_STDIN="$tmp/stdin" \
    GITHUB_OUTPUT="$tmp/gh_out" PROJECT_TOKEN="$1" CURL_EXPIRY="$2" NOW_EPOCH=946684800 bash "$reader" 2>&1)"
  status=$?
  set -e
}
read_expiry "" ""
[ "$status" -eq 0 ] || fail "empty token: exit $status ($stdout)"
grep -qx 'state=skipped' "$tmp/gh_out" || fail "empty token: want state=skipped"
[ ! -s "$tmp/log" ] || fail "empty token: curl must not be called"
read_expiry "tok-SECRET-123" "2000-01-06 00:00:00 UTC"
[ "$status" -eq 0 ] || fail "set token: exit $status ($stdout)"
grep -qx 'state=expiring' "$tmp/gh_out" || fail "set token: want state=expiring"
grep -q 'api.github.com/rate_limit' "$tmp/log" || fail "set token: curl not called for rate_limit"
grep -q 'tok-SECRET-123' "$tmp/log" && fail "token leaked into curl argv" || true
grep -q 'tok-SECRET-123' "$tmp/stdin" || fail "token must travel on the curl stdin config"
grep -q 'tok-SECRET-123' "$tmp/gh_out" && fail "token leaked into the outputs" || true

if [ "$failures" -ne 0 ]; then
  echo "$failures token-expiry notify check(s) failed"
  exit 1
fi
echo "All token-expiry notify checks passed"
