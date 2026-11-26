#!/usr/bin/env bash
# Static check that the server module stays frozen (#91): no workflow or Dependabot
# entry may build, test, publish or promote it. Usage: check-backend-frozen.sh [root]
# (default: the repository root). awk/grep only: no git, no network.
#
# Rules (matching is case-insensitive and scans the full text, comments included):
#   F0 non-vacuity: the tree has workflows, ci.yml has a ci-gate job, dependabot.yml
#      exists and declares package-ecosystem entries
#   F1 workflows: the word "backend"
#   F2 workflows: backend-tests, contract-tests, publish-image, check-image, retag-image
#   F3 workflows: packages:, ghcr.io, docker/login-action, test-image-
#   F4 dependabot.yml: /backend as a directory, any docker ecosystem entry
# Prints one "FAIL: frozen: <file> line <n> (<rule>)" per hit; exits non-zero on any failure.
set -euo pipefail

root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
workflows="$root/.github/workflows"
dependabot="$root/.github/dependabot.yml"

failures=0
fail() {
  echo "FAIL: frozen: $1"
  failures=$((failures + 1))
}

# --- F0: the guard cannot pass on an empty tree ---
files=()
if [ -d "$workflows" ]; then
  for f in "$workflows"/*.yml "$workflows"/*.yaml; do
    [ -f "$f" ] && files+=("$f")
  done
fi
if [ "${#files[@]}" -eq 0 ]; then
  fail ".github/workflows (F0 no workflow files found)"
fi
if [ ! -f "$workflows/ci.yml" ] || ! grep -Eq '^  ci-gate:' "$workflows/ci.yml"; then
  fail "ci.yml (F0 no ci-gate job found)"
fi
if [ ! -f "$dependabot" ]; then
  fail "dependabot.yml (F0 file missing)"
elif ! grep -Eq 'package-ecosystem:' "$dependabot"; then
  fail "dependabot.yml (F0 no package-ecosystem entry)"
fi

# --- F1 to F3: workflow text, comments included ---
for f in ${files[@]+"${files[@]}"}; do
  awk -v name="$(basename "$f")" '
    function hit(rule) { printf "FAIL: frozen: %s line %d (%s)\n", name, NR, rule; found = 1 }
    {
      line = tolower($0)
      if (line ~ /backend/) hit("F1 backend")
      if (line ~ /backend-tests|contract-tests|publish-image|check-image|retag-image/) hit("F2 removed job")
      if (line ~ /packages:|ghcr\.io|docker\/login-action|test-image-/) hit("F3 registry or package access")
    }
    END { exit found ? 1 : 0 }
  ' "$f" || failures=$((failures + 1))
done

# --- F4: Dependabot has no backend directory and no docker ecosystem ---
if [ -f "$dependabot" ]; then
  awk '
    function hit(rule) { printf "FAIL: frozen: dependabot.yml line %d (%s)\n", NR, rule; found = 1 }
    {
      line = tolower($0)
      if (line ~ /^[[:space:]]*#/) next
      if (line ~ /(directory|directories):.*\/backend/) hit("F4 backend directory")
      else if (line ~ /^[[:space:]]*-[[:space:]]*["\047]?\/backend/) hit("F4 backend directory")
      if (line ~ /package-ecosystem:[[:space:]]*["\047]?docker["\047]?[[:space:]]*$/) hit("F4 docker ecosystem")
    }
    END { exit found ? 1 : 0 }
  ' "$dependabot" || failures=$((failures + 1))
fi

if [ "$failures" -ne 0 ]; then
  echo "FAIL: frozen: $failures check(s) failed"
  exit 1
fi
echo "OK: backend is frozen in workflows and dependabot.yml"
