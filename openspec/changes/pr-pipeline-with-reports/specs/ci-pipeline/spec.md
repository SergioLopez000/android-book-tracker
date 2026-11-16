## Capability: ci-pipeline

### Purpose
GitHub Actions orchestration that runs the unit tests on every pull request and `dev` push (static analysis stays local-only, see `code-quality-gates`), publishes linked test and coverage reports, exposes a single required check, cleans up per-PR reports, and ensures promotion to `main` requires a green pipeline and closes referenced issues. Gate semantics are NOT redefined here; CI only invokes them.

## ADDED Requirements

### Requirement: Workflow Triggers and Runner
`.github/workflows/ci.yml` MUST run on `pull_request` and on `push` to `dev`, with no path filters, on the `ubuntu-24.04` runner. It MUST NOT use `pull_request_target`. Default workflow permissions MUST be `contents: read`; write permissions MUST be granted only to the jobs that need them (report, cleanup). Third-party actions MUST be pinned to a major version.

#### Scenario: PR triggers the pipeline
- GIVEN a pull request is opened or updated
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs regardless of which files changed

#### Scenario: Push to dev triggers the pipeline
- GIVEN a commit is pushed to `dev`
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs

#### Scenario: Least privilege
- GIVEN `ci.yml` and `pr-cleanup.yml`
- WHEN their permissions are inspected
- THEN the workflow default is `contents: read`, write scopes appear only on the report and cleanup jobs, and no workflow uses `pull_request_target`

### Requirement: No Static Analysis in CI
CI MUST NOT run static analysis. `ci.yml` MUST NOT contain jobs or steps for `spotlessCheck`, `:app:lintDebug`, actionlint, gitleaks, shellcheck, script harnesses, commit-message validation, or PR-title validation. These gates run only locally through the lefthook hooks. This is an explicit, accepted decision: commits made with `--no-verify`, through the GitHub web UI, or without `lefthook install` skip them.

#### Scenario: No lint jobs
- GIVEN the merged `ci.yml`
- WHEN its jobs are listed
- THEN no job invokes a static-analysis tool or a commit/PR-title validator

#### Scenario: Bypassed hook is not caught in CI
- GIVEN a commit pushed with `--no-verify`
- WHEN the pipeline runs
- THEN no CI job rejects it for formatting, lint, secrets, or message format

### Requirement: Unit Test Job
The `unit-tests` job MUST have no `needs`, MUST run `:app:testDebugUnitTest` and generate Kover XML and HTML coverage, and MUST upload its results as artifacts per the artifact contract.

#### Scenario: Tests run on every trigger
- GIVEN a PR or a `dev` push
- WHEN the pipeline starts
- THEN `unit-tests` runs `:app:testDebugUnitTest`, produces Kover XML and HTML, and uploads artifacts

#### Scenario: Test failure still uploads results
- GIVEN a unit test fails
- WHEN `unit-tests` finishes
- THEN the job fails and its test result artifacts are still uploaded

### Requirement: Artifact Contract
Each test suite MUST publish artifacts named `allure-results-<suite>` (JUnit XML results), `coverage-<suite>` (Kover reports) and, for suites that produce them, `pit-<suite>`. Artifact names MUST be unique per run (no overwrite). The current suite is `unit`. Downstream jobs MUST discover results by these name patterns so future suites (contract, PIT, E2E) attach without changing the report job.

#### Scenario: Unit suite artifacts
- GIVEN `unit-tests` completes
- WHEN artifacts are listed
- THEN `allure-results-unit` and `coverage-unit` exist

#### Scenario: Unknown future suite is merged
- GIVEN an additional artifact named `allure-results-<other>` exists
- WHEN the report job runs
- THEN its results are merged into the same report without workflow changes

### Requirement: Report Generation and Publishing
The `report` job MUST run with `if: always()` after `unit-tests`, merge all `allure-results-*` artifacts, generate an Allure 2.46.2 report from the JUnit XML, and publish the Allure report and the Kover HTML report to the `gh-pages` branch under `pr-<N>/` for pull requests and `dev/` for pushes to `dev`. Publishing MUST preserve other folders (`keep_files`) and runs MUST be serialized with `concurrency` group `gh-pages`. Publishing MUST run only for same-repository PRs and `dev` pushes. If the Allure 2 spike shows JUnit XML is not rendered acceptably, Allure 3 MAY be substituted without altering any other requirement.

#### Scenario: PR report published
- GIVEN a same-repo PR numbered 12 whose tests ran
- WHEN the report job completes
- THEN Allure and Kover reports are available under `pr-12/` on `gh-pages`

#### Scenario: Dev report published
- GIVEN a push to `dev`
- WHEN the report job completes
- THEN the reports are available under `dev/` and existing `pr-<N>/` folders are untouched

#### Scenario: Report on failing tests
- GIVEN `unit-tests` failed after producing results
- WHEN the report job runs
- THEN it still generates and publishes the report

#### Scenario: Missing results
- GIVEN `unit-tests` was skipped and no result artifacts exist
- WHEN the report job runs
- THEN it does not publish a report and does not fail the pipeline by itself

#### Scenario: Fork PR
- GIVEN a PR from a fork
- WHEN the report job runs
- THEN it does not push to `gh-pages` and does not post a comment

#### Scenario: Concurrent publishes
- GIVEN two report jobs start at the same time
- WHEN they reach the publish step
- THEN they run one after the other without losing either folder

### Requirement: Sticky PR Comment
For same-repo PRs, the `report` job MUST create or update exactly one sticky comment (stable header) containing links to the live Allure and Kover reports and the Kover line and branch coverage figures. Re-runs and new pushes MUST update the same comment rather than add one.

#### Scenario: First run
- GIVEN a same-repo PR with no CI comment
- WHEN the report job completes
- THEN one comment exists with Allure link, Kover link, line coverage and branch coverage

#### Scenario: Subsequent push
- GIVEN the PR already has the CI comment
- WHEN a new commit triggers CI
- THEN the same comment is updated and no second comment is created

#### Scenario: No coverage data
- GIVEN Kover XML is unavailable (tests skipped)
- WHEN the comment is written
- THEN it states coverage is unavailable and does not fabricate figures

### Requirement: Single Required Check (ci-gate)
CI MUST define a job named exactly `ci-gate` that runs with `if: always()`, depends only on `unit-tests` (the sole gating job; `report` is not gating), and succeeds only if `unit-tests` succeeded. A skipped or failed `unit-tests` MUST make `ci-gate` fail. A failure of the `report` job alone MUST NOT fail `ci-gate`. The job name MUST NOT change, as it is the sole required status check for the `dev` ruleset.

#### Scenario: Everything green
- GIVEN `unit-tests` succeeded
- WHEN `ci-gate` evaluates
- THEN it succeeds

#### Scenario: Test failure
- GIVEN `unit-tests` failed
- WHEN `ci-gate` evaluates
- THEN it fails

#### Scenario: Report outage
- GIVEN `unit-tests` passed but `report` failed (e.g. Pages push error)
- WHEN `ci-gate` evaluates
- THEN it succeeds

### Requirement: PR Report Cleanup
`.github/workflows/pr-cleanup.yml` MUST run when a PR is closed (merged or not), with write access only to the `gh-pages` content, and MUST remove the `pr-<N>/` folder for that PR, serialized under the `gh-pages` concurrency group. It MUST NOT remove other folders and MUST succeed when the folder does not exist.

#### Scenario: Closed PR cleaned
- GIVEN `pr-12/` exists on `gh-pages`
- WHEN PR 12 is closed
- THEN `pr-12/` is removed and `dev/` and other `pr-<N>/` folders remain

#### Scenario: Nothing to clean
- GIVEN `pr-13/` does not exist (e.g. fork PR)
- WHEN PR 13 is closed
- THEN the workflow succeeds without changes

### Requirement: Promotion Requires Green ci-gate
`promote.yml` MUST, before promoting a SHA to `main`, query the check-runs of that SHA for `check_name=ci-gate` with `filter=latest` and MUST fail the promotion unless the latest `ci-gate` check-run has conclusion `success`. A missing `ci-gate` check-run MUST fail the promotion (replacing the previous warning-only behavior).

#### Scenario: Green SHA promoted
- GIVEN the SHA has a latest `ci-gate` check-run with conclusion `success`
- WHEN `promote.yml` runs
- THEN promotion proceeds

#### Scenario: Missing check
- GIVEN the SHA has no `ci-gate` check-run
- WHEN `promote.yml` runs
- THEN it fails and `main` is not updated

#### Scenario: Failed or pending check
- GIVEN the latest `ci-gate` check-run is failed, cancelled or in progress
- WHEN `promote.yml` runs
- THEN it fails and `main` is not updated

#### Scenario: Re-run supersedes failure
- GIVEN an earlier `ci-gate` run failed and the latest run succeeded
- WHEN `promote.yml` runs
- THEN promotion proceeds

### Requirement: Issue Closing on Promotion
A `Closes #N` (or equivalent closing keyword) present in a commit message on `dev` MUST result in issue N being closed once the commit reaches `main` through promotion. The change MUST verify with a throwaway issue whether the fast-forward promotion closes issues natively. If it does not, `promote.yml` MUST close the issues referenced by closing keywords in the promoted commits as part of promotion. PR-body keywords MUST NOT be relied upon, since PRs target `dev`.

#### Scenario: Native close verified
- GIVEN a commit on `dev` with `Closes #N` for an open throwaway issue
- WHEN it is promoted to `main`
- THEN the issue is closed

#### Scenario: Fallback close
- GIVEN the spike shows promotion does not close issues natively
- WHEN `promote.yml` promotes commits containing `Closes #N`
- THEN `promote.yml` closes issue N and the promotion succeeds

#### Scenario: Already closed issue
- GIVEN a referenced issue is already closed
- WHEN promotion runs the fallback
- THEN promotion does not fail

### Requirement: Documentation of CI and Admin Steps
`README.md` MUST document the pipeline (tests and reports only; static analysis local-only with the accepted `--no-verify` risk), how to reach the reports, the artifact contract, and the manual admin steps: enabling Pages from `gh-pages` after the branch exists, adding `ci-gate` (Actions integration) as a required status check to the `dev` ruleset after the first green run, and configuring the squash merge message to include the PR description.

#### Scenario: Admin steps documented
- GIVEN the README
- WHEN a maintainer reads the CI section
- THEN it lists the Pages, required-check, and merge-message steps in order

### Requirement: Out of Scope Boundaries
This change MUST NOT add contract, PIT or E2E jobs, SonarCloud, CodeQL or Dependabot, `workflow_run` based comments for fork PRs, or a Robolectric version upgrade, and MUST NOT modify application code or gate semantics in `code-quality-gates`.

#### Scenario: No extra jobs
- GIVEN the merged workflows
- WHEN their jobs are listed
- THEN only `unit-tests`, `report`, `ci-gate` (and cleanup/promote workflows) exist
