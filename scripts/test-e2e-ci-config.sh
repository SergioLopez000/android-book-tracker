#!/usr/bin/env bash
# Static checks for the non-gating e2e-tests job, its report wiring and the npm Dependabot entry.
# Reads .github/workflows/ci.yml and .github/dependabot.yml; needs no emulator or network.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ci="$root/.github/workflows/ci.yml"
dependabot="$root/.github/dependabot.yml"

failures=0
check() {
  local name="$1"
  shift
  if ! "$@" >/dev/null 2>&1; then
    echo "FAIL: $name"
    failures=$((failures + 1))
  fi
}

check_not() {
  local name="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "FAIL: $name"
    failures=$((failures + 1))
  fi
}

# Text predicates: has TEXT FIXED-STRING and has_re TEXT ERE (both quiet).
has() { grep -Fq -- "$2" <<<"$1"; }
has_re() { grep -Eq -- "$2" <<<"$1"; }

# Prints the body of a top-level job (two-space indent) from a workflow file.
job_body() {
  awk -v job="$2" '
    /^  [A-Za-z0-9_-]+:[[:space:]]*$/ { in_job = ($1 == job ":") }
    in_job { print }
  ' "$1"
}

# Prints the body of the step whose "- name:" line equals $3 inside job $2.
step_body() {
  job_body "$1" "$2" | awk -v step="$3" '
    /^      - / { in_step = ($0 == "      - name: " step) }
    in_step { print }
  '
}

# Prints one Dependabot update entry selected by its package-ecosystem.
update_entry() {
  awk -v eco="$2" '
    /^  - package-ecosystem:/ { in_entry = ($3 == eco) }
    in_entry { print }
  ' "$1"
}

e2e="$(job_body "$ci" e2e-tests)"
report="$(job_body "$ci" report)"
gate="$(job_body "$ci" ci-gate)"

# --- e2e-tests job ---
check "e2e-tests job exists" test -n "$e2e"
check "e2e-tests runs on ubuntu-24.04" has_re "$e2e" '^    runs-on: ubuntu-24\.04$'
check_not "e2e-tests has no needs" has_re "$e2e" '^    needs:'
check_not "e2e-tests has no job-level if (runs on every trigger)" has_re "$e2e" '^    if:'
check "e2e-tests permissions are contents: read only" \
  test "$(awk '/^    permissions:/{p=1;next} p&&/^      /{print;next} p{exit}' <<<"$e2e")" = "      contents: read"
check_not "e2e-tests uses no secrets" has "$e2e" 'secrets.'
check "e2e-tests API 34" has "$e2e" 'api-level: 34'
check "e2e-tests default target" has "$e2e" 'target: default'
check "e2e-tests x86_64" has "$e2e" 'arch: x86_64'
check "e2e-tests node-version-file: .nvmrc" has "$e2e" 'node-version-file: .nvmrc'
check "e2e-tests npm cache keyed by the e2e lockfile" has "$e2e" 'cache-dependency-path: e2e/package-lock.json'
check "e2e-tests runs the release proof script" has "$e2e" 'scripts/test-release-no-test-tags.sh'
check "e2e-tests builds the debug APK" has "$e2e" ':app:assembleDebug'
check "e2e-tests enables airplane mode" has "$e2e" 'airplane-mode enable'
check "e2e-tests runs npm run e2e" has "$e2e" 'npm run e2e'
check "e2e-tests restores the AVD cache" has "$e2e" 'actions/cache/restore@v6'
check "e2e-tests saves the AVD cache" has "$e2e" 'actions/cache/save@v6'
check "e2e-tests saves the AVD cache only on dev" \
  has "$(step_body "$ci" e2e-tests "Save AVD cache")" "github.ref == 'refs/heads/dev'"

upload="$(step_body "$ci" e2e-tests "Upload e2e results")"
check "e2e upload step exists" test -n "$upload"
# shellcheck disable=SC2016 # the expression is workflow text, not a shell expansion
check "e2e upload uses if: always()" has "$upload" 'if: ${{ always() }}'
check "e2e upload artifact is allure-results-e2e" has "$upload" 'name: allure-results-e2e'
check "e2e upload retention-days: 7" has_re "$upload" 'retention-days: 7$'

# --- non-gating ---
gate_needs="$(grep -E '^    needs:' <<<"$gate" || true)"
check "ci-gate has a needs line" test -n "$gate_needs"
check_not "ci-gate does not need e2e-tests" has "$gate_needs" 'e2e-tests'

# --- report wiring ---
report_needs="$(grep -E '^    needs:' <<<"$report" || true)"
check "report needs e2e-tests" has "$report_needs" 'e2e-tests'
detect="$(step_body "$ci" report "Detect results")"
check "Detect accepts *.xml" has "$detect" 'allure-results/*.xml'
check "Detect accepts *-result.json" has "$detect" 'allure-results/*-result.json'
check "Detect summary says no test results" has "$detect" 'no test results'

# --- Dependabot npm /e2e ---
npm_entry="$(update_entry "$dependabot" npm)"
check "dependabot npm entry exists" test -n "$npm_entry"
check "dependabot npm directory /e2e" has_re "$npm_entry" '^    directory: /e2e$'
check "dependabot npm weekly" has_re "$npm_entry" '^      interval: weekly$'
check "dependabot npm targets dev" has_re "$npm_entry" '^    target-branch: dev$'
check "dependabot npm prefix build" has_re "$npm_entry" '^      prefix: build$'
check "dependabot npm include scope" has_re "$npm_entry" '^      include: scope$'
check "dependabot npm open-pull-requests-limit 5" has_re "$npm_entry" '^    open-pull-requests-limit: 5$'
for group in wdio appium typescript; do
  check "dependabot npm group $group" has_re "$npm_entry" "^      $group:\$"
done
for dep in appium appium-uiautomator2-driver webdriverio '@wdio/*'; do
  check "dependabot npm ignores $dep" has "$npm_entry" "dependency-name: \"$dep\""
done
check "dependabot npm ignores semver-major" has "$npm_entry" 'version-update:semver-major'

if [[ "$failures" -gt 0 ]]; then
  echo "$failures e2e CI check(s) failed"
  exit 1
fi
echo "e2e CI config checks passed"
