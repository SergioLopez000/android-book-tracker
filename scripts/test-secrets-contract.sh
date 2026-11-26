#!/usr/bin/env bash
# Harness for scripts/check-secrets-contract.sh: runs the checker on the real repo
# (must pass) and on mktemp fixtures (each violation must fail with a FAIL: line).
# Needs no Docker or network; run locally (lefthook), never in CI.
# shellcheck disable=SC2016 # fixtures contain literal ${{ }} expressions
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
checker="$root/scripts/check-secrets-contract.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

failures=0
fail() {
  echo "FAIL: $1"
  failures=$((failures + 1))
}

# A complete fixture inventory (headings, table, owner on every row).
DEFAULT_INV='# Secrets
## Inventory
| Secret | Class | Owner | Scope | Location | Consumers | Rotation | Status |
|---|---|---|---|---|---|---|---|
| `PROJECT_TOKEN` | PAT | `SergioLopez000` | project | repository secret | board-sync | 90 days | active |
| `GITHUB_TOKEN` | automatic | `SergioLopez000` | job | runner | ci | automatic | active |
| `SPRING_DATASOURCE_URL` | db | `SergioLopez000` | dev | host | backend | 180 days | planned: created by #8 |
| `GOOGLE_BOOKS_API_KEY` | key | `SergioLopez000` | app | none | app | none | not provisioned; superseded by `GOOGLE_BOOKS_SERVER_API_KEY` (#29) |
## Environments
text
## Rotation cadence
text
## Runbook
An Android-app restriction would reject every request.'

# Creates a fixture root and prints its path. Overridable pieces (portable, no sed -i):
#   TOP      top-level permissions block   UNIT_PERMS  permissions block of unit-tests
#   UNIT_RUN step of unit-tests            E2E_RUN     step of e2e-tests
#   INVENTORY content of docs/secrets.md (unset: DEFAULT_INV; empty: file absent)
new_root() {
  local dir
  dir="$(mktemp -d "$tmp/root.XXXXXX")"
  mkdir -p "$dir/.github/workflows" "$dir/docs"
  if [ -n "${INVENTORY-$DEFAULT_INV}" ]; then
    printf '%s\n' "${INVENTORY-$DEFAULT_INV}" >"$dir/docs/secrets.md"
  fi
  {
    echo "name: CI"
    echo "on: push"
    echo "${TOP-$'permissions:\n  contents: read'}"
    echo "jobs:"
    echo "  unit-tests:"
    echo "    runs-on: ubuntu-24.04"
    echo "${UNIT_PERMS-$'    permissions:\n      contents: read'}"
    echo "    steps:"
    echo "      ${UNIT_RUN:-- run: echo ok}"
    echo "  e2e-tests:"
    echo "    runs-on: ubuntu-24.04"
    echo "    permissions:"
    echo "      contents: read"
    echo "    steps:"
    echo "      ${E2E_RUN:-- run: echo ok}"
  } | sed '/^$/d' >"$dir/.github/workflows/ci.yml"
  echo "$dir"
}

# Runs the checker on a root; sets $out and $status.
run_checker() {
  set +e
  out="$(bash "$checker" "$1" 2>&1)"
  status=$?
  set -e
}

expect_pass() {
  local name="$1" dir="$2"
  run_checker "$dir"
  if [ "$status" -ne 0 ] || grep -q '^FAIL:' <<<"$out"; then
    fail "$name (expected pass, got status $status: $out)"
  fi
}

# expect_fail <name> <root> <pattern that must appear in a FAIL: line> [forbidden text]
expect_fail() {
  local name="$1" dir="$2" pattern="$3" forbidden="${4:-}"
  run_checker "$dir"
  if [ "$status" -eq 0 ]; then
    fail "$name (expected failure, got success)"
  elif ! grep '^FAIL:' <<<"$out" | grep -Eq "$pattern"; then
    fail "$name (no FAIL: line matching '$pattern': $out)"
  elif [ -n "$forbidden" ] && grep -Fq "$forbidden" <<<"$out"; then
    fail "$name (output leaks '$forbidden')"
  fi
}

# --- Allow-list ---
d="$(new_root)"
expect_pass "clean fixture passes" "$d"

d="$(UNIT_RUN='- run: echo "${{ secrets.PROJECT_TOKEN }}"' new_root)"
expect_pass "allowed PROJECT_TOKEN reference passes" "$d"

d="$(UNIT_RUN='- run: echo "${{ secrets.NEW_API_KEY }}"' new_root)"
expect_fail "unlisted secret fails" "$d" 'ci\.yml.*NEW_API_KEY'

d="$(UNIT_RUN='- uses: ./.github/workflows/x.yml
        secrets: inherit' new_root)"
expect_fail "secrets: inherit fails" "$d" 'ci\.yml.*secrets: inherit'

d="$(UNIT_RUN='- run: echo "${{ secrets.GITHUB_TOKEN }}"' new_root)"
expect_fail "secrets.GITHUB_TOKEN fails" "$d" 'ci\.yml.*github\.token'

# A path such as docs/secrets.md is not a secret reference.
d="$(UNIT_RUN='- run: echo "see docs/secrets.md"' new_root)"
expect_pass "docs/secrets.md path is not a secret reference" "$d"

# e2e-tests stays secret-free (even for an allowed secret).
d="$(E2E_RUN='- run: echo "${{ secrets.PROJECT_TOKEN }}"' new_root)"
expect_fail "e2e-tests with a secret fails" "$d" 'e2e-tests.*PROJECT_TOKEN'

# --- Permissions ---
d="$(TOP='' new_root)"
expect_fail "missing top-level permissions fails" "$d" 'ci\.yml.*top-level permissions'

d="$(UNIT_PERMS='' new_root)"
expect_fail "job without permissions fails" "$d" 'ci\.yml.*unit-tests.*permissions'

d="$(TOP='permissions: write-all' new_root)"
expect_fail "write-all fails" "$d" 'ci\.yml.*write-all'

d="$(TOP='permissions: read-all' new_root)"
expect_fail "read-all fails" "$d" 'ci\.yml.*read-all'

# --- Expiry reminder workflow (token-expiry.yml) ---
# add_expiry_wf <root> <triggers> <read-expiry permissions line> <notify permissions line>
add_expiry_wf() {
  {
    echo "name: Token expiry"
    echo "on:"
    echo "$2"
    echo "permissions:"
    echo "  contents: read"
    echo "jobs:"
    echo "  read-expiry:"
    echo "    runs-on: ubuntu-24.04"
    [ -n "$3" ] && printf '    permissions:\n      %s\n' "$3"
    echo "    steps:"
    echo '      - run: echo "${{ secrets.PROJECT_TOKEN }}"'
    echo "  notify:"
    echo "    runs-on: ubuntu-24.04"
    [ -n "$4" ] && printf '    permissions:\n      %s\n' "$4"
    echo "    steps:"
    echo "      - run: echo ok"
  } >"$1/.github/workflows/token-expiry.yml"
}

d="$(new_root)"
add_expiry_wf "$d" "  schedule:
    - cron: '17 6 * * 1'
  workflow_dispatch:" "contents: read" "issues: write"
expect_pass "expiry workflow with scoped permissions passes" "$d"

d="$(new_root)"
add_expiry_wf "$d" "  workflow_dispatch:" "" "issues: write"
expect_fail "expiry job without permissions fails naming workflow and job" "$d" 'token-expiry\.yml.*read-expiry.*permissions'

d="$(new_root)"
add_expiry_wf "$d" "  workflow_dispatch:" "issues: write" "issues: write"
expect_fail "PROJECT_TOKEN job with a write permission fails" "$d" 'token-expiry\.yml.*read-expiry.*PROJECT_TOKEN.*write'

d="$(new_root)"
add_expiry_wf "$d" "  pull_request:" "contents: read" "issues: write"
expect_fail "expiry workflow with a pull_request trigger fails" "$d" 'token-expiry\.yml.*pull_request'

d="$(new_root)"
add_expiry_wf "$d" "  pull_request_target:" "contents: read" "issues: write"
expect_fail "expiry workflow with pull_request_target fails" "$d" 'token-expiry\.yml.*pull_request'

# --- Inventory drift guard ---
d="$(new_root)"
expect_pass "complete inventory passes (GITHUB_TOKEN excluded from workflows)" "$d"

d="$(INVENTORY="$(grep -v 'PROJECT_TOKEN' <<<"$DEFAULT_INV")" UNIT_RUN='- run: echo "${{ secrets.PROJECT_TOKEN }}"' new_root)"
expect_fail "inventory omitting PROJECT_TOKEN fails naming it" "$d" 'inventory.*PROJECT_TOKEN'

d="$(INVENTORY="$(grep -v 'PROJECT_TOKEN' <<<"$DEFAULT_INV")" new_root)"
expect_fail "allow-list entry absent from the inventory fails" "$d" 'inventory.*PROJECT_TOKEN'

d="$(INVENTORY='' new_root)"
expect_fail "missing docs/secrets.md fails" "$d" 'inventory.*docs/secrets\.md'

for heading in 'Inventory' 'Environments' 'Rotation cadence' 'Runbook'; do
  d="$(INVENTORY="$(grep -v "^## $heading\$" <<<"$DEFAULT_INV")" new_root)"
  expect_fail "missing heading $heading fails" "$d" "inventory.*## $heading"
done

d="$(INVENTORY="${DEFAULT_INV//\`SergioLopez000\` | project/other | project}" new_root)"
expect_fail "active row without owner SergioLopez000 fails" "$d" 'inventory.*PROJECT_TOKEN.*owner'

d="$(INVENTORY="${DEFAULT_INV//not provisioned; superseded by \`GOOGLE_BOOKS_SERVER_API_KEY\` (#29)/active / manual restriction}" new_root)"
expect_fail "Google Books row with the old wording fails naming the check" "$d" 'inventory.*GOOGLE_BOOKS_API_KEY.*not provisioned'

d="$(INVENTORY="$(grep -v 'GOOGLE_BOOKS_API_KEY' <<<"$DEFAULT_INV")" new_root)"
expect_fail "missing Google Books row fails" "$d" 'inventory.*GOOGLE_BOOKS_API_KEY row is missing'

d="$(INVENTORY="${DEFAULT_INV//Android-app restriction would reject/restriction note}" new_root)"
expect_fail "runbook without the Android-app warning fails" "$d" 'inventory.*Android-app restriction'

d="$(INVENTORY="$DEFAULT_INV
| \`EXTRA_NAME\` | x | \`SergioLopez000\` | x | x | x | x | planned: created by #8 |" new_root)"
expect_pass "extra inventory rows are allowed" "$d"

# --- Real repository ---
run_checker "$root"
if [ "$status" -ne 0 ] || grep -q '^FAIL:' <<<"$out"; then
  fail "real repository passes the contract ($out)"
fi

if [ "$failures" -ne 0 ]; then
  echo "$failures check(s) failed"
  exit 1
fi
echo "All secrets-contract checks passed"
