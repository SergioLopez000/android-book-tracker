#!/usr/bin/env bash
# Harness for scripts/check-backend-frozen.sh: runs the checker on the real repo
# (must pass) and on mktemp fixtures (each violation must fail with a FAIL: line).
# Fixtures are temporary roots, not real workflows. Needs no Docker or network;
# run locally (lefthook), never in CI.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
checker="$root/scripts/check-backend-frozen.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

failures=0
fail() {
  echo "FAIL: $1"
  failures=$((failures + 1))
}

DEFAULT_CI='name: CI
on: push
permissions:
  contents: read
jobs:
  unit-tests:
    runs-on: ubuntu-24.04
    steps:
      - run: echo ok
  ci-gate:
    needs: [unit-tests]
    runs-on: ubuntu-24.04
    steps:
      - run: echo ok'

DEFAULT_OTHER='name: Other
on: workflow_dispatch
jobs:
  noop:
    runs-on: ubuntu-24.04
    steps:
      - run: echo ok'

DEFAULT_DEPENDABOT='version: 2
updates:
  - package-ecosystem: gradle
    directory: /
  - package-ecosystem: npm
    directory: /e2e'

# Creates a fixture root and prints its path. Overridable pieces (portable, no sed -i):
#   CI_YML content of ci.yml   OTHER_YML content of other.yml   DEP_YML content of dependabot.yml
#   (unset: the clean default; empty: the file is absent)
new_root() {
  local dir
  dir="$(mktemp -d "$tmp/root.XXXXXX")"
  mkdir -p "$dir/.github/workflows"
  if [ -n "${CI_YML-$DEFAULT_CI}" ]; then
    printf '%s\n' "${CI_YML-$DEFAULT_CI}" >"$dir/.github/workflows/ci.yml"
  fi
  if [ -n "${OTHER_YML-$DEFAULT_OTHER}" ]; then
    printf '%s\n' "${OTHER_YML-$DEFAULT_OTHER}" >"$dir/.github/workflows/other.yml"
  fi
  if [ -n "${DEP_YML-$DEFAULT_DEPENDABOT}" ]; then
    printf '%s\n' "${DEP_YML-$DEFAULT_DEPENDABOT}" >"$dir/.github/dependabot.yml"
  fi
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

# expect_fail <name> <root> <pattern that must appear in a FAIL: frozen: line>
expect_fail() {
  local name="$1" dir="$2" pattern="$3"
  run_checker "$dir"
  if [ "$status" -eq 0 ]; then
    fail "$name (expected failure, got success)"
  elif ! grep '^FAIL: frozen:' <<<"$out" | grep -Eq "$pattern"; then
    fail "$name (no 'FAIL: frozen:' line matching '$pattern': $out)"
  fi
}

# --- Clean root and positive control ---
d="$(new_root)"
expect_pass "clean root passes" "$d"

d="$(OTHER_YML="$DEFAULT_OTHER
# server module frozen (#91), see README" new_root)"
expect_pass "frozen comment is allowed (positive control)" "$d"

# --- F0: the guard cannot pass on an empty tree ---
d="$(CI_YML='' OTHER_YML='' new_root)"
expect_fail "empty workflows directory fails" "$d" 'F0'

d="$(DEP_YML='' new_root)"
expect_fail "missing dependabot.yml fails" "$d" 'dependabot\.yml.*F0'

d="$(CI_YML="${DEFAULT_CI//ci-gate/ci-check}" new_root)"
expect_fail "ci.yml without a ci-gate job fails" "$d" 'ci\.yml.*F0'

d="$(DEP_YML='version: 2' new_root)"
expect_fail "dependabot.yml without package-ecosystem fails" "$d" 'dependabot\.yml.*F0'

# --- F1: any backend text, comments included, case-insensitive ---
d="$(OTHER_YML="$DEFAULT_OTHER
# backend" new_root)"
expect_fail "backend comment fails (comments are scanned)" "$d" 'other\.yml line [0-9]+.*F1'

d="$(OTHER_YML="$DEFAULT_OTHER
# BACKEND" new_root)"
expect_fail "upper-case BACKEND fails (case-insensitive)" "$d" 'other\.yml.*F1'

# --- F2: removed job names, as job or needs ---
for job in backend-tests contract-tests publish-image; do
  d="$(CI_YML="$DEFAULT_CI
  $job:
    runs-on: ubuntu-24.04
    steps:
      - run: echo ok" new_root)"
  expect_fail "job $job fails" "$d" "ci\\.yml.*F2"
done

d="$(OTHER_YML='name: Promote
on: workflow_dispatch
jobs:
  promote:
    needs: [check-image]
    runs-on: ubuntu-24.04
    steps:
      - run: echo ok
  retag-image:
    runs-on: ubuntu-24.04
    steps:
      - run: echo ok' new_root)"
expect_fail "needs check-image fails" "$d" 'other\.yml.*F2'
expect_fail "retag-image job fails" "$d" 'other\.yml line [0-9]+ \(F2'

# --- F3: registry and package-write surface ---
d="$(OTHER_YML="$DEFAULT_OTHER
      - uses: docker/login-action@v3" new_root)"
expect_fail "docker/login-action fails" "$d" 'other\.yml.*F3'

d="$(OTHER_YML="$DEFAULT_OTHER
      - run: docker pull ghcr.io/x/y" new_root)"
expect_fail "ghcr.io reference fails" "$d" 'other\.yml.*F3'

d="$(OTHER_YML="$DEFAULT_OTHER
    permissions:
      packages: write" new_root)"
expect_fail "packages: write fails" "$d" 'other\.yml.*F3'

d="$(OTHER_YML="$DEFAULT_OTHER
      - run: bash scripts/test-image-foo.sh" new_root)"
expect_fail "test-image- reference fails" "$d" 'other\.yml.*F3'

# --- F4: Dependabot has no backend or docker entry ---
d="$(DEP_YML="$DEFAULT_DEPENDABOT
  - package-ecosystem: gradle
    directory: /backend" new_root)"
expect_fail "directory /backend fails" "$d" 'dependabot\.yml line [0-9]+.*F4'

d="$(DEP_YML="$DEFAULT_DEPENDABOT
  - package-ecosystem: gradle
    directories:
      - /
      - /backend" new_root)"
expect_fail "directories list with /backend fails" "$d" 'dependabot\.yml.*F4'

d="$(DEP_YML="$DEFAULT_DEPENDABOT
  - package-ecosystem: docker
    directory: /" new_root)"
expect_fail "package-ecosystem docker fails" "$d" 'dependabot\.yml.*F4'

d="$(DEP_YML="$DEFAULT_DEPENDABOT
  - package-ecosystem: \"docker\"
    directory: /" new_root)"
expect_fail "quoted package-ecosystem docker fails" "$d" 'dependabot\.yml.*F4'

# --- Real repository ---
run_checker "$root"
if [ "$status" -ne 0 ] || grep -q '^FAIL:' <<<"$out"; then
  fail "real repository is frozen ($out)"
fi

if [ "$failures" -ne 0 ]; then
  echo "$failures check(s) failed"
  exit 1
fi
echo "All backend-frozen checks passed"
