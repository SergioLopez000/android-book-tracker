#!/usr/bin/env bash
# Reads the PROJECT_TOKEN expiry header and appends state/days/expires to $GITHUB_OUTPUT.
# Env: PROJECT_TOKEN (may be empty), GITHUB_OUTPUT. An empty token yields state=skipped
# without any network call. The token travels on curl's stdin config, never in argv.
set -euo pipefail

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
out="${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"

if [ -z "${PROJECT_TOKEN-}" ]; then
  echo "::notice::PROJECT_TOKEN is not set; skipping the expiry check."
  echo "state=skipped" >>"$out"
  exit 0
fi
printf 'header = "Authorization: Bearer %s"\n' "$PROJECT_TOKEN" |
  curl -sS -D - -o /dev/null --max-time 20 -K - https://api.github.com/rate_limit |
  bash "$dir/token-expiry.sh" |
  tee -a "$out"
