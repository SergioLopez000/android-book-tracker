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

DIGEST="gcr.io/distroless/java21-debian13:nonroot@sha256:$(printf 'a%.0s' {1..64})"

# Creates a fixture root and prints its path. Overridable pieces (portable, no sed -i):
#   TOP      top-level permissions block   UNIT_PERMS  permissions block of unit-tests
#   UNIT_RUN step of unit-tests            E2E_RUN     step of e2e-tests
new_root() {
  local dir
  dir="$(mktemp -d "$tmp/root.XXXXXX")"
  mkdir -p "$dir/.github/workflows" "$dir/backend"
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
  printf 'FROM %s\nUSER nonroot\n' "$DIGEST" >"$dir/backend/Dockerfile"
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

# --- Dockerfile ---
d="$(new_root)"
printf 'ENV DB_PASSWORD=hunter2value\n' >>"$d/backend/Dockerfile"
expect_fail "secret-like ENV fails without leaking the value" "$d" 'Dockerfile.*ENV DB_PASSWORD' 'hunter2value'

d="$(new_root)"
printf 'ARG API_TOKEN\n' >>"$d/backend/Dockerfile"
expect_fail "secret-like ARG fails" "$d" 'Dockerfile.*ARG API_TOKEN'

d="$(new_root)"
printf 'ENV JAVA_TOOL_OPTIONS=-Xmx256m\nARG BUILD_DATE\n' >>"$d/backend/Dockerfile"
expect_pass "benign ENV and ARG pass" "$d"

d="$(new_root)"
printf 'FROM gcr.io/distroless/java21-debian13:nonroot\nUSER nonroot\n' >"$d/backend/Dockerfile"
expect_fail "FROM without digest fails" "$d" 'Dockerfile.*digest'

d="$(new_root)"
printf 'FROM %s\nFROM %s\n' "$DIGEST" "$DIGEST" >"$d/backend/Dockerfile"
expect_fail "two FROM lines fail" "$d" 'Dockerfile.*FROM'

d="$(new_root)"
rm "$d/backend/Dockerfile"
expect_fail "missing Dockerfile fails" "$d" 'Dockerfile.*FROM'

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
