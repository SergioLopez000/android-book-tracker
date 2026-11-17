# Verify Report: pr-pipeline-with-reports

Verdict: PASS WITH WARNINGS. Critical 0, Warnings 2, Suggestions 2.

## Local checks (run 2026-11-17, branch docs/3-close-pipeline @ 177d61f)
- actionlint .github/workflows: clean (rc 0)
- shellcheck scripts/*.sh: clean (rc 0)
- scripts/test-kover-summary.sh: all cases passed; scripts/test-check-commit-msg.sh: 31 cases passed
- lefthook validate: All good
- ./gradlew :app:testDebugUnitTest (--rerun): BUILD SUCCESSFUL, 102 tests, 0 failures, 0 skipped
- Tasks: all [x] or deliberately [-] (2.1-2.4, 5.3, 8.1 skipped/superseded with rationale); none open.

## Spec coverage (ci-pipeline, code-quality-gates delta)
All 13 requirements compliant. Static evidence from ci.yml, promote.yml, pr-cleanup.yml, lefthook.yml and README. Runtime evidence from the live facts supplied by the caller.
- Triggers/Runner: PR + dev push, ubuntu-24.04, no pull_request_target, default contents:read, major-pinned actions. OK
- No Static Analysis in CI: only unit-tests, report, ci-gate jobs exist. OK
- Unit Test Job: no needs, artifacts with !cancelled(). OK
- Artifact Contract: allure-results-unit, coverage-unit. OK
- Report/Publishing: gh-pages concurrency, keep_files, same-repo only, has_results guard. OK (live: pr-38/pr-40/dev render)
- Sticky comment: one comment updated in place (PR #40). OK
- ci-gate: name fixed, needs only unit-tests, if always(); ruleset 27200640 requires it (integration 15368). OK
- PR cleanup: pr-38/pr-40 removed. OK
- Promotion requires green ci-gate: paginate + filter=latest + Actions app filter; missing/pending/failed fail. Live red path unreachable; verified offline with 8 payloads. OK (accepted evidence)
- Issue linking/manual closing: matches S2 result; promote.yml has no issues:write. OK
- Docs: README CI section and ordered admin steps present. OK
- Out of scope: no extra jobs or app code changes. OK
- code-quality-gates delta: android-lint job with glob, fix hint, in lefthook. OK

Scenarios: all covered. Behavioural ones are backed by live runs or offline payloads. The "Fork PR" and "Missing results" scenarios are covered only by static guards in the workflow, with no live probe.

## Findings
- WARNING 1: Fork PR, Missing results, Concurrent publishes and "Report on failing tests" scenarios were never exercised live. Only static guards were reviewed (same-repo condition, has_results, gh-pages concurrency).
- WARNING 2: Spec text says report runs `if: always()` but ci.yml uses `!cancelled()`. Intent is met (runs on failure), but the wording drifts. Align the spec or the workflow.
- SUGGESTION 1: Local `testDebugUnitTest` first reported UP-TO-DATE. A forced rerun confirmed green, so no action needed beyond using --rerun in future checks.
- SUGGESTION 2: Tasks forecast table still says delivery "ask-on-risk" while the session uses single-pr. Update it on archive.
- Accepted risk (not a finding): static analysis is local-only, so `--no-verify` and web commits bypass it (D11).

Next: sdd-archive.
