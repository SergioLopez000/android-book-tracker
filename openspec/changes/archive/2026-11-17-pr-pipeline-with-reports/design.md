# Design: PR Pipeline With Test Reports

## Technical Approach

One `ci.yml` (approach A, proposal). `unit-tests` is the only gating job (D11 removed the lint jobs). A non-blocking `report` job turns JUnit XML into Allure 2.46.2 and publishes Allure and Kover to `gh-pages`. `ci-gate` aggregates `unit-tests` and is the only required check. `promote.yml` requires the latest `ci-gate` on the promoted SHA. Parsing logic lives in `scripts/*.sh` with table harnesses, following `check-commit-msg.sh`. Three spikes run first. No app, Gradle or layering changes.

## Architecture Decisions

| # | Decision | Chosen | Rejected | Rationale |
|---|---|---|---|---|
| D1 | Workflow shape | One `ci.yml`, inline jobs | Reusable workflows | Reusable workflows prefix check names (`ci / ci-gate`). #6/#7 only add a job and two `needs:` entries. |
| D2 | `ci-gate` inputs | Gate jobs + `unit-tests` only. `report` excluded | Gate on `report` | A Pages outage, or a pending `gh-pages` run replaced by concurrency, must not block merges. `report` stays a visible, separate check. |
| D3 | Gate rule | Fail on any `failure`/`cancelled`, and when `unit-tests != success` | `contains(needs.*.result,'failure')` only | Skipped tests must never count as green. |
| D4 | Report source | Gradle JUnit XML → Allure CLI | `allure-junit4`/`allure-kotlin` | No test or build changes. Fallback is Allure 3 (S1). |
| D5 | Publishing | `peaceiris/actions-gh-pages@v4`, `destination_dir`, `keep_files: true` | `actions/deploy-pages` | `deploy-pages` replaces the whole site, which loses per-PR folders. |
| D6 | Fork PRs | Upload `report-site` artifact only | `pull_request_target`/`workflow_run` | Fork tokens are read-only. Never run untrusted code with write scopes. |
| D7 | Closing issues | Squash setting "title + description" puts `Closes #N` in dev commits, so a fast-forward to `main` closes them. **Disproved by S2 (2026-11-17, issue #39): the promote fast-forward did not close the issue** | Default branch = `dev` | `dev` as default closes issues before QA, which breaks the DoD. |
| D8 | Closing fallback (only if S2 fails) | **Manual closing** (user decision 2026-11-17): after `qa:approved`, a summary comment and promotion, the issue is closed by hand and moved to Done | `promote.yml` parses `main..SHA` and closes issues | Keeps `promote.yml` without `issues: write`, and the summary comment must precede closing anyway. |
| D9 | Check trust | `promote.yml` filters `check_name=ci-gate`, `filter=latest`, `app.id` = Actions id | Any check named `ci-gate` | Another app could spoof the check name. |
| D10 | Gradle cache | `setup-gradle` with `cache-read-only: ${{ github.ref != 'refs/heads/dev' }}` | Default (writes on `main` only) | CI never runs on `main`, so the cache would never be written. PRs read caches from their base branch. |
| D11 | Static analysis local-only (supersedes the lint-job topology, D3 inputs and the commit-messages/shell job contracts) | Remove the six lint jobs from CI; add `:app:lintDebug` to the lefthook pre-commit | Keep lint jobs in CI | Explicit user decision 2026-11-16. Accepted risk: `--no-verify`/web commits skip all gates. `ci-gate` depends only on `unit-tests`. |

## Data Flow

```
                         unit-tests ── allure-results-unit, coverage-unit
                    ┌─────────┴──────────┐
                 ci-gate               report (!cancelled, unit-tests ran)
          (required check)      Allure + Kover → site/ → gh-pages/{pr-N|dev}/
                                        └→ sticky comment (same-repo PR)
```

`gh-pages` layout: `.nojekyll`, `dev/{allure,kover}/`, `pr-<N>/{allure,kover}/`. Links follow `https://sergiolopez000.github.io/android-book-tracker/pr-<N>/allure/`.

## Job Contracts

- **Triggers**: `pull_request` and `push: [dev]`. No `paths:` filters. Runner `ubuntu-24.04`. JDK temurin 17.
- **Concurrency**: workflow group `ci-${{ github.event.pull_request.number || github.ref }}`, with `cancel-in-progress` on PRs only. The `report` job and `pr-cleanup` share group `gh-pages`, with `cancel-in-progress: false`.
- **Permissions**: `contents: read` at workflow level. `report` gets `contents: write` and `pull-requests: write`. `pr-cleanup` gets `contents: write`.
- **commit-messages** (SUPERSEDED by D11, removed from CI): on PRs, runs every `git rev-list --no-merges base.sha..head.sha` message through `check-commit-msg.sh`. The PR title is passed through `env:`, written to a temp file, and checked. On pushes it is a no-op success.
- **shell** (SUPERSEDED by D11, removed from CI): `shellcheck scripts/*.sh`, then every `scripts/test-*.sh`.
- **unit-tests**: no `needs`. `./gradlew :app:testDebugUnitTest :app:koverXmlReportDebug :app:koverHtmlReportDebug --continue`. Uploads run with `if: !cancelled()`, so failing runs still report.
- **report**: download `allure-results-*` (merged) and `coverage-*` (per suite). Install the pinned Allure tgz and verify its SHA-256. Run `allure generate`. Read `widgets/summary.json` and `kover-summary.sh`. Write to the step summary. Then publish and add the sticky comment (header `ci-report`) for pushes and same-repo PRs. Always upload `report-site`.
- **pr-cleanup.yml**: on `pull_request: closed` for same-repo PRs. Check out `gh-pages` (exit 0 if it is missing), `git rm -r --ignore-unmatch pr-<N>`, commit, and push only if something changed.
- **promote.yml**: add `--paginate`, a ci-gate query (D9), and fail on a missing, in-progress or non-success ci-gate. Remove the "#3" warning. Use `ubuntu-24.04` and `checkout@v7`.

## Interfaces / Contracts

| Artifact | Content | Consumer |
|---|---|---|
| `allure-results-<suite>` | Flat JUnit XML or Allure result files with unique names across suites | `report`, `pattern` + `merge-multiple` |
| `coverage-<suite>` | `report.xml` (Kover/JaCoCo XML) + `html/` | `report`, per-suite folder. Only `unit` is summarized |
| `pit-<suite>` | Reserved for #6 | — |

Retention is 14 days. `scripts/kover-summary.sh <xml>` prints `line=<pct> branch=<pct>` from the report-level counters. `scripts/closing-issues.sh <msgfile>` (D8 only) prints unique same-repo issue numbers.

## File Changes

| File | Action | Description |
|---|---|---|
| `.github/workflows/ci.yml` | Create | `unit-tests`, `report`, `ci-gate` (D11: no lint jobs) |
| `lefthook.yml` | Modify | Android Lint pre-commit job (D11) |
| `.github/workflows/pr-cleanup.yml` | Create | Removes the PR folder |
| `.github/workflows/promote.yml` | Modify | ci-gate requirement. D8 step only if S2 fails |
| `scripts/kover-summary.sh`, `scripts/test-kover-summary.sh` | Create | Coverage parser and its harness |
| `scripts/closing-issues.sh`, `scripts/test-closing-issues.sh` | Create (conditional) | D8 parser and its harness |
| `README.md` | Modify | CI, report links, admin steps, rollback |

Estimate: about 330 authored lines after D11 (ci.yml shrank by ~120 lines; +60 with D8). One PR within the 800-line budget.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit (shell, Strict TDD) | `kover-summary.sh`, `closing-issues.sh` | Write the harness first (RED). Cases: missing branch counter, 0/0, `Fixes`/`closes`/`RESOLVED`, `owner/repo#N` ignored, duplicates, `#0`/non-numeric. |
| Static | Workflows | `actionlint` locally (pre-commit); not in CI (D11) |
| Live probes | AC2, cleanup, promote | (Spotless/lint probe PRs dropped by D11.) Green PR: one comment, links resolve. Close PR: folder removed. Promote a SHA with a red ci-gate: fails. |

## Threat Matrix

| Boundary | Applicability | Design response | RED tests |
|---|---|---|---|
| Documentation-like paths | N/A: nothing is classified or executed by name | — | — |
| Git repository selection | Applicable | The range comes only from `pull_request.base.sha`/`head.sha` with `fetch-depth: 0`. No `git -C`. | Probe PR with a bad commit message → red |
| Commit state | N/A: CI never commits app code. The cleanup commit is gated on `git diff --cached --quiet` | — | — |
| Push state | Applicable | Explicit refspecs only: `SHA:refs/heads/main`, `gh-pages`. No force push. Cleanup push failure fails the job. | Promote on a non-ancestor SHA → fails (existing guard) |
| PR commands | Applicable | Title, branch and body text pass only through `env:`, never `${{ }}` in `run:`. Issue numbers match `^[1-9][0-9]*$`, same repo, open issues only (not PRs). | Harness cases above; title with `$(…)` probe stays inert |

## Migration / Rollout

1. **Spikes.** S1: Allure 2 on local `test-results` must list all 102 tests with suites, else fall back to Allure 3 (Node 22.18+). S2: throwaway issue plus a docs commit with `Closes #X`, promote, and observe the issue close. If it does not, enable D8. S3: read `app.id` from the first ci-gate check-run.
2. Land the PR. The first `dev` push creates `gh-pages`.
3. Admin: enable Pages (`gh-pages` `/`). Add `required_status_checks` `ci-gate` (`integration_id` from S3, `strict: false`) to ruleset 27200640 through GET, append and PUT. Set `squash_merge_commit_title=PR_TITLE` and `squash_merge_commit_message=PR_BODY`.
4. **Rollback order**: remove the ruleset check, revert the PR, then optionally disable Pages, delete `gh-pages` and restore the merge settings.

## Spike Results

- **S1 (Allure 2.46.2, 2026-11-16): PASS.** Local `app/build/test-results/testDebugUnitTest/*.xml` (21 files) -> `allure generate` listed 102 passed / 102 total, 21 suites (`widgets/summary.json`, `data/suites.json`). Allure 2 stays (D4 fallback to Allure 3 not needed). Asset: `allure-2.46.2.tgz` from the allure2 GitHub release, SHA-256 `83021e2c02e9a3487df249ef7bbf51c1b9f23b722bd288c6d1785609303e0df8`, identical to Maven Central's published `.sha256`. Pinned in `ci.yml`.
- Apply notes: Kover output is `app/build/reports/kover/reportDebug.xml` (not `report.xml`) plus `htmlDebug/`; `coverage-unit` uploads the whole `app/build/reports/kover` dir. Per-spec, `commit-messages` also validates the pushed range on `dev` pushes (design said no-op). `kover-summary.sh` forces `LC_ALL=C` (comma decimals under some locales).
- S2/S3 pending: `promote.yml` uses Actions app id 15368; confirm with the first real `ci-gate` check-run (task 0.2).

## Open Questions

- [ ] Verify at apply: the Allure tgz asset name and checksum, whether the peaceiris `destination_dir` publish leaves sibling folders untouched, Pages propagation delay, and the squash setting field names.
