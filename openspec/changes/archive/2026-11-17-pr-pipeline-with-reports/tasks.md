# Tasks: PR Pipeline With Test Reports

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~450-510 (incl. D8 +60 if needed) |
| 400-line budget risk | Medium (within project budget of 800) |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Scripts + harnesses | PR 1 | `bash scripts/test-kover-summary.sh` | same | `scripts/kover-summary.sh`, `scripts/test-kover-summary.sh` |
| 2 | Workflows + promote + README | PR 1 | `actionlint` | probe PRs (7.x) | `.github/workflows/*`, `README.md` |

Commits follow work-unit-commits: one conventional commit per unit. All paths stay inside `/Users/sergio.lopezm/Desktop/libros-leidos`.

## Phase 0: Spikes (before any workflow code)

- [x] 0.1 S1: run Allure 2.46.2 on local `app/build/test-results`; must list all 102 tests with suites, else switch to Allure 3 (D4). Record result in design.md.
- [x] 0.2 S3: ci-gate check-run `app.id`=15368, slug `github-actions`, confirmed on run 43886351939 (matches promote.yml).
- [x] 0.3 S2 (OUTWARD, confirm first): create throwaway issue, push docs commit `Closes #X` to `dev`, promote, observe closing. Run only when `dev` holds harmless commits. Result: FAILED. Issue #39, PR #40 squash `c39d0b0`, promote run 44042751261 green, issue stayed open.
- [x] 0.4 If S2 fails, enable conditional tasks 2.3-2.4 and 5.3 (D8). User chose manual closing instead (D8 amended); conditional tasks skipped.

## Phase 1: TDD scripts

- [x] 1.1 RED: `scripts/test-kover-summary.sh` cases: missing branch counter, 0/0, report-level counters only.
- [x] 1.2 GREEN: `scripts/kover-summary.sh <xml>` prints `line=<pct> branch=<pct>`; shellcheck clean.
- [x] 1.3 REFACTOR: tidy both scripts; re-run harness.

## Phase 2: Conditional D8 (only if S2 fails)

- [-] 2.1 (SKIPPED, D8 manual) Mark threat row "PR commands" RED cases: `$(...)` title inert, `#0`/non-numeric rejected.
- [-] 2.2 (SKIPPED, D8 manual) RED `scripts/test-closing-issues.sh`: `Fixes`/`closes`/`RESOLVED`, `owner/repo#N` ignored, duplicates, `#0`.
- [-] 2.3 (SKIPPED, D8 manual) GREEN `scripts/closing-issues.sh <msgfile>`: unique same-repo numbers, `^[1-9][0-9]*$`.
- [-] 2.4 (SKIPPED, D8 manual) Wire into `promote.yml` (see 5.3).

## Phase 3: Workflows

- [x] 3.1 `.github/workflows/ci.yml`: triggers, `ubuntu-24.04`, JDK 17, `contents: read`, concurrency, `setup-gradle` cache rule (D10).
- [x] 3.2 (SUPERSEDED by 9.1, jobs removed) Lint jobs: `lint-format`, `lint-android`, `lint-workflows`, `secrets` (no PR comments), `commit-messages` (range + title via `env:`, `fetch-depth: 0`), `shell` (shellcheck + all `scripts/test-*.sh`).
- [x] 3.3 `unit-tests` (`needs` lint dropped by 9.1); Gradle + Kover; uploads `allure-results-unit`, `coverage-unit` with `if: !cancelled()`, 14-day retention.
- [x] 3.4 `report`: not gating; merge artifacts, verified Allure tgz (SHA-256), step summary, peaceiris publish (same-repo only), sticky comment `ci-report`, always upload `report-site`; group `gh-pages`.
- [x] 3.5 `ci-gate` (D3, `if: always()`, excludes `report`).
- [x] 3.6 `.github/workflows/pr-cleanup.yml`: remove `pr-<N>/`, tolerate missing branch/folder.
- [x] 3.7 Run `actionlint` locally on all workflows.

## Phase 4: First run

- [x] 4.1 (OUTWARD) First run green; `gh-pages` created with `pr-38/`; sticky comment posted.

## Phase 5: promote.yml

- [x] 5.1 Edit `.github/workflows/promote.yml`: `--paginate`, ci-gate query (`filter=latest`, Actions `app.id`, D9); fail on missing, pending or non-success.
- [x] 5.2 Remove "#3" warning; set `ubuntu-24.04`, `checkout@v7`.
- [-] 5.3 (SKIPPED, D8 manual) Add D8 close step with `issues: write`.

## Phase 6: Docs

- [x] 6.1 `README.md`: pipeline, report links, artifact contract.
- [x] 6.2 `README.md`: ordered admin steps and rollback order.

## Phase 7: Admin (OUTWARD, user confirmation required for each)

- [x] 7.1 Enable Pages from branch `gh-pages`, root folder. Verified: Pages status `built`, source `gh-pages` `/`.
- [x] 7.2 Add `ci-gate` required check to ruleset 27200640 (GET, append, PUT; `integration_id` from 0.2, `strict: false`), only after the first green run. Verified: `ci-gate` with `integration_id` 15368.
- [x] 7.3 Set `squash_merge_commit_title=PR_TITLE`, `squash_merge_commit_message=PR_BODY`. Verified via repo API.

## Phase 8: Live probes (OUTWARD)

- [-] 8.1 (SUPERSEDED by D11) Spotless-failing PR probe: no lint in CI.
- [x] 8.2 Green PR: one sticky comment, links resolve (AC2). Verified on PR #40: two runs, one sticky comment updated in place, Allure/Kover links resolved while open. "Bad commit message red" and "`$(...)` title inert" are obsolete after D11 (no title/commit checks in CI).
- [x] 8.3 Close PR: folder removed (`pr-38/`, `pr-40/` removed by pr-cleanup). Promote SHA with red `ci-gate`: fails. Not reachable live (the dev ruleset requires a green `ci-gate`, and promote only fast-forwards from `main`), so verified offline: the promote.yml jq filter was run on 8 simulated check-run payloads (none, failure, in progress, other app, newer failure, newer success, paginated, success) and all matched.

## Phase 9: Scope change D11 - static analysis local-only (2026-11-16)

- [x] 9.1 `ci.yml`: remove commit-messages, secrets, lint-format, shell, lint-workflows, lint-android; `unit-tests` drops `needs`; `ci-gate` needs only `unit-tests`; header/messages updated. `actionlint` clean.
- [x] 9.2 `lefthook.yml`: add `android-lint` pre-commit job (`./gradlew --quiet :app:lintDebug`, glob on app sources/resources/Gradle config, fix hint). `lefthook validate` OK; hook command run once green.
- [x] 9.3 `promote.yml`: comment clarifying `ci-gate` means tests passed; requirement kept.
- [x] 9.4 `README.md` (Spanish): CI = tests + reports; static analysis local via lefthook (incl. lintDebug); accepted `--no-verify`/web-commit risk.
- [x] 9.5 SDD artifacts: proposal (Scope Change), ci-pipeline spec (No Static Analysis in CI, Unit Test Job, ci-gate), delta spec `code-quality-gates`, design D11.
- [x] 9.6 Verify: actionlint, shellcheck, `test-kover-summary.sh`, `lefthook validate`, `:app:testDebugUnitTest`.
