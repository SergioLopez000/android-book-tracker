#!/usr/bin/env bash
# Table-driven harness for scripts/board-sync.sh. `gh` is replaced by a stub on
# PATH that logs the variables of every `gh api graphql` call (sorted, one line
# per call) and answers with canned JSON, so no network is used.
# Each case expects "<stdout>|<exit>|<ordered gh calls>". Exit "nz" = non-zero.
# Bash 3.2 safe (macOS): no associative arrays, no mapfile, no case conversion.
# The injection-like ISSUE literal is deliberately unexpanded.
# shellcheck disable=SC2016
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="$root/scripts/board-sync.sh"
if [[ ! -x "$script" ]]; then
  echo "FAIL: board-sync script not found or not executable: $script"
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
stub="$tmp/stub"
mkdir -p "$tmp/bin" "$stub"

cat >"$tmp/bin/gh" <<'STUB'
#!/usr/bin/env bash
q=""
vars=""
for a in "$@"; do
  case "$a" in
    query=*) q="${a#query=}" ;;
    *=*) vars="$vars
$a" ;;
  esac
done
case "$q" in
  *addProjectV2ItemById*) op=M1 ;;
  *updateProjectV2ItemFieldValue*) op=M2 ;;
  *) op=Q1 ;;
esac
echo "$op $(printf '%s\n' "$vars" | sed '/^$/d' | sort | tr '\n' ' ' | sed 's/ $//')" >>"$STUB_DIR/log"
if [[ -e "$STUB_DIR/$op.fail" ]]; then
  echo "stub: $op failed" >&2
  exit 1
fi
cat "$STUB_DIR/$op.json"
STUB
chmod +x "$tmp/bin/gh"

O_BACKLOG=oBacklog
O_READY=oReady
O_PROGRESS=oProgress
O_REVIEW=oReview
O_QA=oQa
O_DONE=oDone

base=(
  GH_TOKEN=tok GITHUB_REPOSITORY=o/r PROJECT_ID=PVT_P STATUS_FIELD_ID=PVTSSF_F
  OPT_BACKLOG="$O_BACKLOG" OPT_READY="$O_READY" OPT_IN_PROGRESS="$O_PROGRESS"
  OPT_IN_REVIEW="$O_REVIEW" OPT_IN_DEV_QA="$O_QA" OPT_DONE="$O_DONE"
)

# --- fixtures -------------------------------------------------------------
status_json() {
  case "$1" in
    none) echo null ;;
    backlog) echo "{\"optionId\":\"$O_BACKLOG\",\"name\":\"Backlog\"}" ;;
    ready) echo "{\"optionId\":\"$O_READY\",\"name\":\"Ready\"}" ;;
    progress) echo "{\"optionId\":\"$O_PROGRESS\",\"name\":\"In progress\"}" ;;
    review) echo "{\"optionId\":\"$O_REVIEW\",\"name\":\"In review\"}" ;;
    qa) echo "{\"optionId\":\"$O_QA\",\"name\":\"In dev (QA)\"}" ;;
    finished) echo "{\"optionId\":\"$O_DONE\",\"name\":\"Done\"}" ;;
    unknown) echo '{"optionId":"zzz","name":"Weird"}' ;;
  esac
}
item_json() { # <status key> <project id> <item id>
  echo "{\"id\":\"$3\",\"project\":{\"id\":\"$2\",\"number\":2},\"fieldValueByName\":$(status_json "$1")}"
}
q1() { # <issue json value> <nodes json>
  echo "{\"data\":{\"repository\":{\"issue\":$1}}}" >"$stub/Q1.json"
}
issue_with() { # <nodes csv>
  q1 "{\"id\":\"I_5\",\"projectItems\":{\"nodes\":[$1]}}"
}
on_board() { issue_with "$(item_json "$1" PVT_P PVTI_5)"; }
mutations_ok() {
  echo '{"data":{"addProjectV2ItemById":{"item":{"id":"PVTI_new"}}}}' >"$stub/M1.json"
  echo '{"data":{"updateProjectV2ItemFieldValue":{"projectV2Item":{"id":"x"}}}}' >"$stub/M2.json"
}

failures=0
# check <name> <want_out> <want_exit|nz> <want_calls> <action> <draft> <merged> [VAR=val...]
# Calls are joined with "|". Fixtures are cleared after every case.
check() {
  local name="$1" want_out="$2" want_exit="$3" want_calls="$4" action="$5" draft="$6" merged="$7" out code=0 calls
  shift 7
  : >"$stub/log"
  out="$(env -i PATH="$tmp/bin:$PATH" STUB_DIR="$stub" "${base[@]}" \
    ISSUE=5 ACTION="$action" DRAFT="$draft" MERGED="$merged" "$@" "$script" 2>"$tmp/err")" || code=$?
  calls="$(tr '\n' '|' <"$stub/log" | sed 's/|$//')"
  local bad=0
  [[ "$out" == "$want_out" ]] || bad=1
  if [[ "$want_exit" == "nz" ]]; then
    [[ "$code" != 0 ]] || bad=1
  else
    [[ "$code" == "$want_exit" ]] || bad=1
  fi
  [[ "$calls" == "$want_calls" ]] || bad=1
  if [[ "$bad" == 1 ]]; then
    echo "FAIL: $name: got '$out' (exit $code, calls '$calls'), want '$want_out' (exit $want_exit, calls '$want_calls')"
    failures=$((failures + 1))
  fi
  rm -f "$stub"/*.json "$stub"/*.fail
}

Q="Q1 name=r number=5 owner=o"
m2() { echo "M2 f=PVTSSF_F i=$1 o=$2 p=PVT_P"; }
review_from() { echo "moved: #5 $1 -> In review"; }
qa_from() { echo "moved: #5 $1 -> In dev (QA)"; }

# --- open / reopen / ready -> In review ------------------------------------
for ev in opened reopened ready_for_review; do
  on_board backlog; mutations_ok
  check "$ev-from-backlog" "$(review_from Backlog)" 0 "$Q|$(m2 PVTI_5 $O_REVIEW)" "$ev" false false
done
on_board ready; mutations_ok
check open-from-ready "$(review_from Ready)" 0 "$Q|$(m2 PVTI_5 $O_REVIEW)" opened false false
on_board progress; mutations_ok
check open-from-progress "$(review_from "In progress")" 0 "$Q|$(m2 PVTI_5 $O_REVIEW)" opened false false
on_board none; mutations_ok
check open-from-empty "$(review_from "(none)")" 0 "$Q|$(m2 PVTI_5 $O_REVIEW)" opened false false

# --- never regress ---------------------------------------------------------
on_board review; mutations_ok
check open-when-review "noop: #5 In review is not behind In review" 0 "$Q" opened false false
on_board qa; mutations_ok
check open-when-qa "noop: #5 In dev (QA) is not behind In review" 0 "$Q" reopened false false
on_board finished; mutations_ok
check open-when-done "noop: #5 Done is not behind In review" 0 "$Q" opened false false
on_board finished; mutations_ok
check ready-when-done "noop: #5 Done is not behind In review" 0 "$Q" ready_for_review false false

# --- merged -> In dev (QA) -------------------------------------------------
on_board review; mutations_ok
check merged-from-review "$(qa_from "In review")" 0 "$Q|$(m2 PVTI_5 $O_QA)" closed false true
on_board backlog; mutations_ok
check merged-from-backlog "$(qa_from Backlog)" 0 "$Q|$(m2 PVTI_5 $O_QA)" closed false true
on_board progress; mutations_ok
check merged-from-progress "$(qa_from "In progress")" 0 "$Q|$(m2 PVTI_5 $O_QA)" closed false true
on_board none; mutations_ok
check merged-from-empty "$(qa_from "(none)")" 0 "$Q|$(m2 PVTI_5 $O_QA)" closed false true
on_board finished; mutations_ok
check merged-when-done "noop: #5 Done is not behind In dev (QA)" 0 "$Q" closed false true
on_board qa; mutations_ok
check merged-when-qa "noop: #5 In dev (QA) is not behind In dev (QA)" 0 "$Q" closed false true

# --- events that never touch the board (no gh call at all) -----------------
on_board backlog; mutations_ok
check draft-opened "noop: draft pull request" 0 "" opened true false
on_board backlog; mutations_ok
check draft-reopened "noop: draft pull request" 0 "" reopened true false
on_board backlog; mutations_ok
check closed-unmerged "noop: closed without merge" 0 "" closed false false
on_board backlog; mutations_ok
check synchronize-ignored "noop: event synchronize ignored" 0 "" synchronize false false
on_board backlog; mutations_ok
check edited-ignored "noop: event edited ignored" 0 "" edited false false

# --- item missing: add, then update ----------------------------------------
issue_with ""; mutations_ok
check item-missing "$(review_from "(none)")" 0 "$Q|M1 c=I_5 p=PVT_P|$(m2 PVTI_new $O_REVIEW)" opened false false
issue_with "$(item_json qa PVT_OTHER PVTI_9)"; mutations_ok
check item-on-other-project "$(review_from "(none)")" 0 "$Q|M1 c=I_5 p=PVT_P|$(m2 PVTI_new $O_REVIEW)" opened false false
issue_with "$(item_json finished PVT_OTHER PVTI_9),$(item_json progress PVT_P PVTI_5)"; mutations_ok
check our-item-after-other "$(review_from "In progress")" 0 "$Q|$(m2 PVTI_5 $O_REVIEW)" opened false false
issue_with ""; mutations_ok
check item-missing-merged "$(qa_from "(none)")" 0 "$Q|M1 c=I_5 p=PVT_P|$(m2 PVTI_new $O_QA)" closed false true

# --- other issue and repo, so nothing is hardcoded -------------------------
issue_with "$(item_json ready PVT_P PVTI_42)"; mutations_ok
check other-issue-and-repo "moved: #42 Ready -> In review" 0 \
  "Q1 name=repo number=42 owner=me|$(m2 PVTI_42 $O_REVIEW)" opened false false \
  ISSUE=42 GITHUB_REPOSITORY=me/repo

# --- not an issue / unknown status -----------------------------------------
q1 null; mutations_ok
check issue-null "noop: #5 is not an issue" 0 "$Q" opened false false
on_board unknown; mutations_ok
check unknown-option "noop: #5 has an unknown status option" 0 "$Q" opened false false
if ! grep -q '^::warning::' "$tmp/err"; then
  echo "FAIL: unknown-option: expected a ::warning:: on stderr"
  failures=$((failures + 1))
fi

# --- token, errors and input validation ------------------------------------
on_board backlog; mutations_ok
check empty-token "::notice::PROJECT_TOKEN is not set; skipping board sync" 0 "" opened false false GH_TOKEN=
on_board backlog; mutations_ok; : >"$stub/Q1.fail"
check q1-fails "" nz "$Q" opened false false
on_board backlog; mutations_ok; : >"$stub/M2.fail"
check m2-fails "" nz "$Q|$(m2 PVTI_5 $O_REVIEW)" opened false false
issue_with ""; mutations_ok; : >"$stub/M1.fail"
check m1-fails "" nz "$Q|M1 c=I_5 p=PVT_P" opened false false

for bad in "1;x" "" "0" "05" "-1" "5 6" 'a$(b)'; do
  on_board backlog; mutations_ok
  check "bad-issue-$bad" "" 2 "" opened false false "ISSUE=$bad"
done

if [[ "$failures" -gt 0 ]]; then
  echo "$failures case(s) failed"
  exit 1
fi
echo "all cases passed"
