#!/usr/bin/env bash
# Static checks for the backend image and the workflows that publish and promote it.
# Reads backend/Dockerfile and .github/workflows/*.yml; needs no Docker or network.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dockerfile="$root/backend/Dockerfile"
ci="$root/.github/workflows/ci.yml"
promote="$root/.github/workflows/promote.yml"

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

# Prints the body of a top-level job (two-space indent) from a workflow file.
job_body() {
  awk -v job="$2" '
    /^  [A-Za-z0-9_-]+:[[:space:]]*$/ { in_job = ($1 == job ":") }
    in_job { print }
  ' "$1"
}

# Lists the jobs of a workflow that declare "packages: write".
write_jobs() {
  awk '
    /^  [A-Za-z0-9_-]+:[[:space:]]*$/ { job = $1; sub(/:$/, "", job) }
    /^[[:space:]]+packages:[[:space:]]*write/ { print (job == "" ? "<workflow>" : job) }
  ' "$1" | sort -u | paste -sd, -
}

# --- Dockerfile ---
check "dockerfile exists" test -f "$dockerfile"
check "single FROM pinned by digest" \
  bash -c "[ \"\$(grep -c '^FROM ' '$dockerfile')\" = 1 ] && grep -Eq '^FROM gcr\.io/distroless/java21-debian13:nonroot@sha256:[0-9a-f]{64}\$' '$dockerfile'"
check "USER nonroot" grep -Eq '^USER nonroot$' "$dockerfile"
check "COPY build/libs/backend.jar" grep -Eq '^COPY build/libs/backend\.jar ' "$dockerfile"
check "OCI source label" grep -Eq 'org\.opencontainers\.image\.source=' "$dockerfile"
check_not "no ENV or ARG" grep -Eq '^(ENV|ARG) ' "$dockerfile"
check "dockerignore exists" test -f "$root/backend/.dockerignore"

# --- publish-image in ci.yml ---
publish="$(job_body "$ci" publish-image)"
check "publish-image exists" test -n "$publish"
check "publish-image only on dev push" \
  grep -Fq "if: github.event_name == 'push' && github.ref == 'refs/heads/dev'" <<<"$publish"
check "publish-image needs ci-gate" grep -Eq 'needs: (\[?ci-gate\]?)$' <<<"$publish"
check_not "ci-gate does not need publish-image" \
  grep -Eq 'needs: \[.*publish-image' "$ci"

# --- promote.yml ---
check "check-image job" test -n "$(job_body "$promote" check-image)"
check "check-image reads packages" grep -Eq 'packages: read' <<<"$(job_body "$promote" check-image)"
check "promote needs check-image" grep -Eq 'needs: (\[?check-image\]?)$' <<<"$(job_body "$promote" promote)"
retag="$(job_body "$promote" retag-image)"
check "retag-image needs promote" grep -Eq 'needs: (\[?promote\]?)$' <<<"$retag"
check "retag-image contents read" grep -Eq 'contents: read' <<<"$retag"
check_not "retag-image no issues write" grep -q 'issues: write' <<<"$retag"
check "crane pinned by sha256" grep -Eq '[0-9a-f]{64}' <<<"$retag"
check "retag asserts digest equality" grep -Eq 'crane digest' <<<"$retag"

# --- least privilege ---
check "packages: write only in publish-image" test "$(write_jobs "$ci")" = "publish-image"
check "packages: write only in retag-image" test "$(write_jobs "$promote")" = "retag-image"
check_not "no pull_request_target" grep -q 'pull_request_target' "$root"/.github/workflows/*.yml

if [[ "$failures" -gt 0 ]]; then
  echo "$failures image check(s) failed"
  exit 1
fi
echo "image config checks passed"
