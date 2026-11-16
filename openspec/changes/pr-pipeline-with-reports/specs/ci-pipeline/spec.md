## Capability: ci-pipeline

### Purpose
GitHub Actions orchestration that enforces the existing quality gates (`code-quality-gates`) on every pull request and `dev` push, publishes linked test and coverage reports, exposes a single required check, cleans up per-PR reports, and ensures promotion to `main` requires a green pipeline and closes referenced issues. Gate semantics are NOT redefined here; CI only invokes them.

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

### Requirement: Parallel Lint Jobs
CI MUST run these lint jobs in parallel with no dependency between them: `spotlessCheck`, `:app:lintDebug`, actionlint, gitleaks, commit validation, and shellcheck with the commit-msg script test. Each job MUST invoke the corresponding existing gate and fail when that gate exits non-zero. Gitleaks and commit validation MUST check out full history (`fetch-depth: 0`); gitleaks MUST disable PR comments.

#### Scenario: Lint jobs run concurrently
- GIVEN a PR run
- WHEN the jobs start
- THEN all lint jobs are scheduled without `needs` on one another

#### Scenario: Formatting violation
- GIVEN a misformatted Kotlin file
- WHEN the formatting job runs
- THEN the job fails

#### Scenario: Secret detected
- GIVEN a commit in the PR range containing a secret
- WHEN the gitleaks job runs
- THEN the job fails

#### Scenario: Workflow or shell script defect
- GIVEN an invalid workflow file or a shellcheck violation in a hook script
- WHEN the actionlint or shellcheck job runs
- THEN that job fails

### Requirement: Commit and PR Title Validation
The commit job MUST validate every commit in the PR range against the repository commit-message rules and MUST validate the PR title against the same conventional-commit format. On `push` to `dev` (no PR), it MUST validate the pushed range, and the PR title check MUST be skipped.

#### Scenario: Non-conforming commit
- GIVEN a PR containing a commit whose message violates the rules
- WHEN the commit job runs
- THEN it fails and names the commit

#### Scenario: Non-conforming PR title
- GIVEN a PR whose title is not a valid conventional-commit title
- WHEN the commit job runs
- THEN it fails

#### Scenario: Conforming PR passes
- GIVEN all commits and the title are valid
- WHEN the commit job runs
- THEN it succeeds

### Requirement: Unit Test Job Gated by Lint
The `unit-tests` job MUST declare `needs` on every lint job, MUST run `:app:testDebugUnitTest` and generate Kover XML and HTML coverage, and MUST upload its results as artifacts per the artifact contract. If any lint job fails, `unit-tests` MUST be skipped.

#### Scenario: Lint failure skips tests
- GIVEN one lint job fails
- WHEN the pipeline completes
- THEN `unit-tests` is skipped

#### Scenario: All lint green runs tests
- GIVEN all lint jobs succeed
- WHEN `unit-tests` runs
- THEN it executes `:app:testDebugUnitTest`, produces Kover XML and HTML, and uploads artifacts

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
CI MUST define a job named exactly `ci-gate` that runs with `if: always()`, depends on all lint jobs and `unit-tests` (the gating jobs; `report` is not gating), and succeeds only if every gating job succeeded. A skipped or failed gating job MUST make `ci-gate` fail. A failure of the `report` job alone MUST NOT fail `ci-gate`. The job name MUST NOT change, as it is the sole required status check for the `dev` ruleset.

#### Scenario: Everything green
- GIVEN all lint jobs and `unit-tests` succeeded
- WHEN `ci-gate` evaluates
- THEN it succeeds

#### Scenario: Lint failure
- GIVEN any lint job failed
- WHEN `ci-gate` evaluates
- THEN it fails (not skipped), even though `unit-tests` was skipped

#### Scenario: Test failure
- GIVEN `unit-tests` failed
- WHEN `ci-gate` evaluates
- THEN it fails

#### Scenario: Report outage
- GIVEN all gating jobs passed but `report` failed (e.g. Pages push error)
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
`README.md` MUST document the pipeline, how to reach the reports, the artifact contract, and the manual admin steps: enabling Pages from `gh-pages` after the branch exists, adding `ci-gate` (Actions integration) as a required status check to the `dev` ruleset after the first green run, and configuring the squash merge message to include the PR description.

#### Scenario: Admin steps documented
- GIVEN the README
- WHEN a maintainer reads the CI section
- THEN it lists the Pages, required-check, and merge-message steps in order

### Requirement: Out of Scope Boundaries
This change MUST NOT add contract, PIT or E2E jobs, SonarCloud, CodeQL or Dependabot, `workflow_run` based comments for fork PRs, or a Robolectric version upgrade, and MUST NOT modify application code or gate semantics in `code-quality-gates`.

#### Scenario: No extra jobs
- GIVEN the merged workflows
- WHEN their jobs are listed
- THEN only lint, `unit-tests`, `report`, `ci-gate` (and cleanup/promote workflows) exist
