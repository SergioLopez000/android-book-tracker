#!/usr/bin/env bash
# Prints the gitleaks --log-opts value for the current CI event.
# Reads EVENT_NAME, BASE_SHA, HEAD_SHA (pull_request) or BEFORE_SHA, AFTER_SHA
# (push) from the environment. Fails closed: a malformed SHA exits 2 and a
# BEFORE that is not a commit in this clone exits 1; the scan never silently
# narrows to the head commit unless BEFORE is all zeros (new branch).
set -euo pipefail

zeros="0000000000000000000000000000000000000000"

require_sha() {
  if [[ ! "$2" =~ ^[0-9a-f]{40}$ ]]; then
    echo "::error::$1 is not a 40-character lowercase hex SHA" >&2
    exit 2
  fi
}

case "${EVENT_NAME:-}" in
  pull_request)
    require_sha BASE_SHA "${BASE_SHA:-}"
    require_sha HEAD_SHA "${HEAD_SHA:-}"
    echo "${BASE_SHA}..${HEAD_SHA}"
    ;;
  push)
    require_sha BEFORE_SHA "${BEFORE_SHA:-}"
    require_sha AFTER_SHA "${AFTER_SHA:-}"
    if [[ "$BEFORE_SHA" == "$zeros" ]]; then
      echo "-1 ${AFTER_SHA}"
    elif git cat-file -e "${BEFORE_SHA}^{commit}" 2>/dev/null; then
      echo "${BEFORE_SHA}..${AFTER_SHA}"
    else
      echo "::error::BEFORE_SHA ${BEFORE_SHA} is not a commit in this clone; refusing to scan only the head commit" >&2
      exit 1
    fi
    ;;
  *)
    echo "::error::unsupported EVENT_NAME '${EVENT_NAME:-}'" >&2
    exit 2
    ;;
esac
