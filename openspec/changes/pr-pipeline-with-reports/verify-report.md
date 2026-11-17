```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:cd0e0cb222588178fbbdc5096a582ef28493d9ddc3f0003fc6b5be458f7584a5
verdict: fail
blockers: 1
critical_findings: 1
requirements: 13/13
scenarios: 31/35
test_command: ./gradlew :app:testDebugUnitTest --rerun
test_exit_code: 0
test_output_hash: sha256:eeabe893e117f40a4ed269dbfcc60e1e6051695fcce802095a75400c6d543a61
build_command: ./gradlew :app:assembleDebug
build_exit_code: 0
build_output_hash: sha256:2e3738530d6ae1614d4fe9516ab93482a41d213f5d3c92607c901453c542cb50
```

## Verification Report

**Change**: pr-pipeline-with-reports
**Version**: N/A
**Mode**: Strict TDD (HEAD d683059, branch docs/3-close-pipeline, run 2026-11-17)

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 36 |
| Tasks complete | 30 ([x]) |
| Tasks deliberately skipped | 6 ([-]: 2.1-2.4, 5.3, 8.1, each with rationale) |
| Tasks incomplete | 0 |

Recount of specs: ci-pipeline has 12 requirements and 32 scenarios; the code-quality-gates delta has 1 requirement and 3 scenarios. Total 13 requirements and 35 scenarios, matching the authoritative totals.

### Build & Tests Execution
**Build**: ✅ Passed
```text
./gradlew :app:assembleDebug -> BUILD SUCCESSFUL (exit 0), sha256:2e3738530d6ae1614d4fe9516ab93482a41d213f5d3c92607c901453c542cb50
```

**Tests**: ✅ 102 passed / ❌ 0 failed / ⚠️ 0 skipped
```text
./gradlew :app:testDebugUnitTest --rerun -> BUILD SUCCESSFUL (exit 0), sha256:eeabe893e117f40a4ed269dbfcc60e1e6051695fcce802095a75400c6d543a61
JUnit XML totals: tests=102 skipped=0 failures=0 errors=0
```

**Other checks (all exit 0)**: actionlint (clean), shellcheck scripts/*.sh (clean), scripts/test-kover-summary.sh (all cases passed), scripts/test-check-commit-msg.sh (31 cases passed), lefthook validate (All good).

**Coverage**: ➖ Not measured in this run (koverHtmlReportDebug not executed; informational only).

### TDD Compliance
| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | ❌ | apply-progress (Engram #92) is prose with no "TDD Cycle Evidence" table |
| All tasks have tests | ✅ | Only unit 1 (scripts) is testable code; scripts/test-kover-summary.sh exists |
| RED confirmed (tests exist) | ✅ | test-kover-summary.sh and test-check-commit-msg.sh exist |
| GREEN confirmed (tests pass) | ✅ | Both harnesses pass on execution |
| Triangulation adequate | ✅ | kover-summary harness covers missing branch counter, 0/0, report-level counters |
| Safety Net for modified files | ➖ | No pre-existing tested file was modified |

**TDD Compliance**: 4/5 applicable checks passed. The missing table is a documentation gap, not a behavioural gap.

### Test Layer Distribution
| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit (app) | 102 | n/a | JUnit4 + Robolectric (unchanged by this change) |
| Shell harness | 2 scripts | 2 | bash (test-kover-summary.sh, test-check-commit-msg.sh) |
| E2E | 0 | 0 | not installed |

### Assertion Quality
Shell harnesses assert exact output values from real script invocations. No tautologies, ghost loops or smoke-only checks found. **Assertion quality**: ✅ All assertions verify real behavior.

### Spec Compliance Matrix
Evidence kinds: RUN = executed locally in this run; LIVE = verified live on 2026-11-17 and supplied by the caller; OFFLINE = promote.yml jq filter run on 8 payloads; STATIC = workflow source inspected.

| Requirement | Scenario | Evidence | Result |
|-------------|----------|----------|--------|
| Workflow Triggers and Runner | PR triggers the pipeline | LIVE PR #40 two green runs | ✅ COMPLIANT |
| Workflow Triggers and Runner | Push to dev triggers the pipeline | LIVE dev/ report published | ✅ COMPLIANT |
| Workflow Triggers and Runner | Least privilege | RUN actionlint; STATIC contents: read default, write only on report/cleanup, no pull_request_target | ✅ COMPLIANT |
| No Static Analysis in CI | No lint jobs | STATIC ci.yml jobs: unit-tests, report, ci-gate | ✅ COMPLIANT |
| No Static Analysis in CI | Bypassed hook is not caught in CI | STATIC no CI job validates format/secrets/message (accepted risk D11) | ✅ COMPLIANT |
| Unit Test Job | Tests run on every trigger | RUN 102 tests; LIVE artifacts | ✅ COMPLIANT |
| Unit Test Job | Test failure still uploads results | STATIC upload steps use !cancelled(); not exercised with a failing run | ⚠️ PARTIAL |
| Artifact Contract | Unit suite artifacts | LIVE allure-results-unit, coverage-unit | ✅ COMPLIANT |
| Artifact Contract | Unknown future suite is merged | STATIC pattern-based discovery of allure-results-* | ✅ COMPLIANT |
| Report Generation and Publishing | PR report published | LIVE pr-40 rendered | ✅ COMPLIANT |
| Report Generation and Publishing | Dev report published | LIVE dev/allure and dev/kover render | ✅ COMPLIANT |
| Report Generation and Publishing | Report on failing tests | STATIC report if: !cancelled(); never exercised with failing tests | ⚠️ PARTIAL |
| Report Generation and Publishing | Missing results | STATIC has_results guard; no live probe | ⚠️ PARTIAL |
| Report Generation and Publishing | Fork PR | STATIC same-repo publish condition; no live probe | ⚠️ PARTIAL |
| Report Generation and Publishing | Concurrent publishes | STATIC concurrency group gh-pages; no live probe | ⚠️ PARTIAL |
| Sticky PR Comment | First run | LIVE one ci-report comment on PR #40 | ✅ COMPLIANT |
| Sticky PR Comment | Subsequent push | LIVE comment updated in place across two runs | ✅ COMPLIANT |
| Sticky PR Comment | No coverage data | RUN test-kover-summary.sh cases (missing counters, 0/0) | ✅ COMPLIANT |
| Single Required Check (ci-gate) | Everything green | LIVE ci-gate success; ruleset 27200640 requires it (integration 15368) | ✅ COMPLIANT |
| Single Required Check (ci-gate) | Test failure | STATIC needs unit-tests, if: always(), fails unless success; accepted with ruleset enforcement | ✅ COMPLIANT |
| Single Required Check (ci-gate) | Report outage | STATIC needs only unit-tests; report not gating | ✅ COMPLIANT |
| PR Report Cleanup | Closed PR cleaned | LIVE pr-38/ and pr-40/ removed | ✅ COMPLIANT |
| PR Report Cleanup | Nothing to clean | STATIC folder-missing tolerance in pr-cleanup.yml; LIVE runs succeeded | ✅ COMPLIANT |
| Promotion Requires Green ci-gate | Green SHA promoted | LIVE promote run 44042751261 green | ✅ COMPLIANT |
| Promotion Requires Green ci-gate | Missing check | OFFLINE jq filter, payload "none" | ✅ COMPLIANT |
| Promotion Requires Green ci-gate | Failed or pending check | OFFLINE payloads failure, in progress, other app | ✅ COMPLIANT |
| Promotion Requires Green ci-gate | Re-run supersedes failure | OFFLINE payloads newer failure, newer success, paginated | ✅ COMPLIANT |
| Issue Linking and Manual Closing | Branch linked to the issue | LIVE S2 spike (#39) and `gh issue develop` workflow | ✅ COMPLIANT |
| Issue Linking and Manual Closing | Promotion leaves issues open | LIVE S2 failed as recorded: #39 stayed open after promote 44042751261; promote.yml has no issues: write | ✅ COMPLIANT |
| Issue Linking and Manual Closing | Summary before closing | Process rule documented in README; user chose manual closing | ✅ COMPLIANT |
| Documentation of CI and Admin Steps | Admin steps documented | STATIC README CI section lists Pages, required-check, merge-message steps in order; LIVE Pages built, squash PR_TITLE/PR_BODY | ✅ COMPLIANT |
| Out of Scope Boundaries | No extra jobs | STATIC only unit-tests, report, ci-gate, cleanup, promote | ✅ COMPLIANT |
| Pre-commit Hook Gates | Android source change triggers Lint | RUN lefthook validate; android-lint job glob and fix hint inspected; command run once green during apply | ✅ COMPLIANT |
| Pre-commit Hook Gates | Unrelated change skips Lint | STATIC glob limits android-lint to app sources, resources, Gradle config | ✅ COMPLIANT |
| Pre-commit Hook Gates | Hook bypass is an accepted risk | STATIC no CI re-run of gates (D11) | ✅ COMPLIANT |

**Compliance summary**: 31/35 scenarios compliant, 4 partial (static guards only, never exercised live).

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|------------|--------|-------|
| Workflow Triggers and Runner | ✅ Implemented | ubuntu-24.04, no pull_request_target, default contents: read |
| No Static Analysis in CI | ✅ Implemented | Six lint jobs removed |
| Unit Test Job | ✅ Implemented | No needs; artifacts with !cancelled() |
| Artifact Contract | ✅ Implemented | allure-results-unit, coverage-unit |
| Report Generation and Publishing | ✅ Implemented | Spec and ci.yml both say !cancelled() (already aligned at HEAD) |
| Sticky PR Comment | ✅ Implemented | Stable ci-report header |
| Single Required Check (ci-gate) | ✅ Implemented | needs [unit-tests], if: always() |
| PR Report Cleanup | ✅ Implemented | |
| Promotion Requires Green ci-gate | ✅ Implemented | --paginate, filter=latest, app.id 15368 filter |
| Issue Linking and Manual Closing | ✅ Implemented | No issues: write in promote.yml |
| Documentation of CI and Admin Steps | ✅ Implemented | |
| Out of Scope Boundaries | ✅ Implemented | |
| Pre-commit Hook Gates (delta) | ⚠️ Implemented, spec header mismatch | See WARNING 2 |

### Coherence (Design)
| Decision | Followed? | Notes |
|----------|-----------|-------|
| D3 ci-gate excludes report | ✅ Yes | |
| D8 manual closing (amended after S2 failure) | ✅ Yes | Conditional tasks 2.1-2.4, 5.3 skipped with rationale |
| D9 Actions app id filter | ✅ Yes | |
| D11 static analysis local-only | ✅ Yes | lefthook android-lint added |

### Issues Found
**CRITICAL**:
1. TDD evidence missing: apply-progress (Engram `sdd/pr-pipeline-with-reports/apply-progress`, #92) has no "TDD Cycle Evidence" table, which strict-tdd-verify.md treats as CRITICAL. Behaviour is not in doubt (both harnesses exist and pass on execution, triangulated). Remedy: add the table to apply-progress for tasks 1.1-1.3 (RED/GREEN/TRIANGULATE/SAFETY NET/REFACTOR) and re-verify.

**WARNING**:
1. Four scenarios were never exercised live, only by static guards: "Report on failing tests", "Missing results", "Fork PR", "Concurrent publishes". "Test failure still uploads results" is likewise static only.
2. Archive needs to resolve a requirement-name mismatch. The delta declares MODIFIED "Pre-commit Hook Gates", but the living spec openspec/specs/code-quality-gates/spec.md names it "Pre-commit Hook". A MODIFIED block cannot match a requirement by a different name, so archive must either rename the delta header to "Pre-commit Hook" or the living requirement. Also, the MODIFIED block replaces the whole requirement and its scenarios: it intentionally or not drops all four living scenarios, including "Misformatted staged file blocked" and "Staged fake secret blocked" (and also "Broken workflow blocked" and "Clean commit passes"), and supplies three new ones (Android source change triggers Lint, Unrelated change skips Lint, Hook bypass is an accepted risk). The delta text does not state the drop is intentional; the behaviours still exist in lefthook.yml (spotless, gitleaks --redact, actionlint, shellcheck jobs), so archive would silently lose their scenario coverage unless the delta restores them. The living text also says "Android Lint MUST NOT run in pre-commit", which the delta reverses. Specs were not edited.

**SUGGESTION**:
1. Add the four dropped Pre-commit Hook scenarios to the delta before archive so coverage is preserved.
2. Run `./gradlew koverHtmlReportDebug :app:lintDebug spotlessCheck` as listed in openspec/config.yaml verify rules if a coverage figure is wanted; not measured here.
3. Already fixed at HEAD and not re-reported: spec says !cancelled() for report; tasks forecast says single-pr.

### Verdict
FAIL
One CRITICAL (strict TDD evidence table missing from apply-progress); behavioural evidence is otherwise green: 102 tests, build, actionlint, shellcheck, harnesses and lefthook all exit 0.
