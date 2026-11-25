#!/usr/bin/env bash
# Static secrets contract. Usage: scripts/check-secrets-contract.sh [root]
# Checks (awk/grep only, no git, no network):
#   1. workflows reference only allow-listed secrets; no `secrets: inherit`;
#      GITHUB_TOKEN is written `github.token`; the e2e-tests job stays secret-free
#   2. every workflow and job declares `permissions:`; no write-all / read-all
#   3. backend/Dockerfile has no secret-like ENV/ARG and a single digest-pinned FROM
#   4. drift guard: docs/secrets.md lists every secrets.* name used by workflows and
#      every ALLOWED_SECRETS entry, has the required headings and owner SergioLopez000;
#      the GOOGLE_BOOKS_API_KEY row says not provisioned / superseded by
#      GOOGLE_BOOKS_SERVER_API_KEY (#29) and the runbook keeps the Android-app warning
#   5. a job that uses PROJECT_TOKEN holds no write permission; token-expiry.yml has no
#      pull_request / pull_request_target trigger
# Prints one `FAIL: ...` line per violation and exits non-zero. Never prints a secret value.
set -euo pipefail

root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
workflows="$root/.github/workflows"
dockerfile="$root/backend/Dockerfile"
inventory_doc="$root/docs/secrets.md"

ALLOWED_SECRETS=(PROJECT_TOKEN)

failures=0
fail() {
  echo "FAIL: $1"
  failures=$((failures + 1))
}

is_allowed() {
  local name
  for name in "${ALLOWED_SECRETS[@]}"; do
    [ "$name" = "$1" ] && return 0
  done
  return 1
}

# Prints a workflow without comment lines.
strip_comments() {
  grep -Ev '^[[:space:]]*#' "$1" || true
}

# Prints the body of a top-level job (two-space indent) from a workflow file.
job_body() {
  strip_comments "$1" | awk -v job="$2" '
    /^[A-Za-z]/ { in_job = 0 }
    /^  [A-Za-z0-9_-]+:[[:space:]]*$/ { in_job = ($1 == job ":") }
    in_job { print }
  '
}

# Prints the `name: write` entries of a job's own permissions block.
job_write_permissions() {
  job_body "$1" "$2" | awk '
    /^    permissions:/ { in_perm = 1; if ($0 ~ /:[[:space:]]*write[[:space:]]*$/) print; next }
    /^    [^ ]/ { in_perm = 0 }
    in_perm && /^      [A-Za-z-]+:[[:space:]]*write[[:space:]]*$/ { print }
  '
}

# Lists the job names of a workflow.
job_names() {
  strip_comments "$1" | awk '
    /^jobs:/ { in_jobs = 1; next }
    /^[A-Za-z]/ { in_jobs = 0 }
    in_jobs && /^  [A-Za-z0-9_-]+:[[:space:]]*$/ { name = $1; sub(/:$/, "", name); print name }
  '
}

shopt -s nullglob
used_secrets=("${ALLOWED_SECRETS[@]}")
files=("$workflows"/*.yml "$workflows"/*.yaml)

for file in "${files[@]}"; do
  wf="$(basename "$file")"
  content="$(strip_comments "$file")"

  # 1. Allow-list
  if grep -Eq 'secrets:[[:space:]]*inherit' <<<"$content"; then
    fail "allow-list: $wf uses secrets: inherit"
  fi
  if grep -Eq 'secrets\.GITHUB_TOKEN([^A-Za-z0-9_]|$)' <<<"$content"; then
    fail "allow-list: $wf uses secrets.GITHUB_TOKEN (write github.token instead)"
  fi
  while IFS= read -r name; do
    [ -z "$name" ] || [ "$name" = "GITHUB_TOKEN" ] && continue
    is_allowed "$name" || fail "allow-list: $wf references unlisted secret $name"
    used_secrets+=("$name")
  done < <(grep -Eo '(^|[^A-Za-z0-9_/.-])secrets\.[A-Za-z0-9_]+' <<<"$content" | sed 's/.*secrets\.//' | sort -u)

  if [ "$wf" = "ci.yml" ]; then
    while IFS= read -r name; do
      [ -n "$name" ] && fail "allow-list: $wf job e2e-tests references secrets.$name (must stay secret-free)"
    done < <(job_body "$file" e2e-tests | grep -Eo '(^|[^A-Za-z0-9_/.-])secrets\.[A-Za-z0-9_]+' | sed 's/.*secrets\.//' | sort -u || true)
  fi

  # 2. Permissions
  if grep -Eq 'permissions:[[:space:]]*(write-all|read-all)' <<<"$content"; then
    fail "permissions: $wf uses write-all or read-all"
  fi
  if ! grep -Eq '^permissions:' <<<"$content"; then
    fail "permissions: $wf has no top-level permissions block"
  fi
  while IFS= read -r job; do
    [ -z "$job" ] && continue
    if ! job_body "$file" "$job" | grep -Eq '^    permissions:'; then
      fail "permissions: $wf job $job has no permissions block"
    fi
    # 5. PROJECT_TOKEN and a write-capable token never share a job.
    if job_body "$file" "$job" | grep -Eq 'secrets\.PROJECT_TOKEN([^A-Za-z0-9_]|$)' &&
      [ -n "$(job_write_permissions "$file" "$job")" ]; then
      fail "permissions: $wf job $job uses PROJECT_TOKEN and also has a write permission"
    fi
  done < <(job_names "$file")

  if [ "$wf" = "token-expiry.yml" ] && grep -Eq 'pull_request(_target)?([^A-Za-z0-9_]|$)' <<<"$content"; then
    fail "triggers: $wf must not use pull_request or pull_request_target"
  fi
done

# 3. Dockerfile
if [ ! -f "$dockerfile" ] ||
  [ "$(grep -c '^FROM ' "$dockerfile" || true)" != 1 ] ||
  ! grep -Eq '^FROM [^[:space:]]+@sha256:[0-9a-f]{64}([[:space:]]+AS[[:space:]]+[A-Za-z0-9_-]+)?$' "$dockerfile"; then
  fail "Dockerfile: expected a single digest-pinned FROM in backend/Dockerfile"
fi
if [ -f "$dockerfile" ]; then
  # Prints "<INSTRUCTION> <NAME>" for every ENV/ARG variable whose name looks secret-like.
  while IFS= read -r hit; do
    [ -n "$hit" ] && fail "Dockerfile: $hit has a secret-like name"
  done < <(awk '
    /^(ENV|ARG)[[:space:]]/ {
      instr = $1
      for (i = 2; i <= NF; i++) {
        name = $i
        if (instr == "ENV" && i > 2 && index(name, "=") == 0) continue
        sub(/=.*/, "", name)
        if (tolower(name) ~ /(pass|secret|token|key|credential|datasource)/) print instr " " name
        if (instr == "ENV" && index($2, "=") == 0) break
      }
    }
  ' "$dockerfile")
fi

# 4. Inventory drift guard
if [ ! -f "$inventory_doc" ]; then
  fail "inventory: docs/secrets.md is missing"
else
  for heading in Inventory Environments "Rotation cadence" Runbook; do
    grep -Eq "^## ${heading}[[:space:]]*$" "$inventory_doc" || fail "inventory: docs/secrets.md lacks the heading ## $heading"
  done
  # Table rows of the ## Inventory section, minus the header and separator.
  rows="$(awk '
    /^## / { in_inv = ($0 ~ /^## Inventory[[:space:]]*$/); next }
    in_inv && /^\|/ && $0 !~ /^\| *Secret *\|/ && $0 !~ /^\|[-| :]+\|?$/ { print }
  ' "$inventory_doc")"
  listed="$(awk -F'|' '{ n = $2; gsub(/[ `]/, "", n); print n }' <<<"$rows")"
  for name in $(printf '%s\n' "${used_secrets[@]}" | sort -u); do
    grep -Fxq "$name" <<<"$listed" || fail "inventory: secret $name is missing from the docs/secrets.md inventory"
  done
  # shellcheck disable=SC2016
  owner_token='`SergioLopez000`'
  while IFS= read -r row; do
    [ -z "$row" ] && continue
    owner="$(awk -F'|' '{ print $4 }' <<<"$row")"
    name="$(awk -F'|' '{ n = $2; gsub(/[ `]/, "", n); print n }' <<<"$row")"
    grep -Fq "$owner_token" <<<"$owner" || fail "inventory: row $name lacks the owner \`SergioLopez000\`"
  done <<<"$rows"
  # Google Books key gated: no client key exists, superseded by the server key (#29).
  # shellcheck disable=SC2016
  gb_key='`GOOGLE_BOOKS_API_KEY`'
  # shellcheck disable=SC2016
  gb_superseded='superseded by `GOOGLE_BOOKS_SERVER_API_KEY` (#29)'
  gb_row="$(grep -F "$gb_key" <<<"$rows" || true)"
  if [ -z "$gb_row" ]; then
    fail "inventory: GOOGLE_BOOKS_API_KEY row is missing from docs/secrets.md"
  elif ! grep -Fq 'not provisioned' <<<"$gb_row" ||
    ! grep -Fq "$gb_superseded" <<<"$gb_row"; then
    fail "inventory: row GOOGLE_BOOKS_API_KEY must state not provisioned and superseded by GOOGLE_BOOKS_SERVER_API_KEY (#29)"
  fi
  grep -Fq 'Android-app restriction would reject' "$inventory_doc" ||
    fail "inventory: docs/secrets.md runbook lost the Android-app restriction warning for the Google Books key"
fi

if [ "$failures" -ne 0 ]; then
  exit 1
fi
echo "secrets contract OK"
