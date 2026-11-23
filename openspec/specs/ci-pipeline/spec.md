## Capability: ci-pipeline

### Purpose
GitHub Actions orchestration that runs the unit tests and the security scans (gitleaks secret scan, CodeQL) on every pull request and `dev` push, publishes linked test and coverage reports, exposes a single required check, cleans up per-PR reports, and ensures promotion to `main` requires a green pipeline. Quality static analysis (format, lint, detekt, commit-message) stays local-only, see `code-quality-gates`; security scanning runs in CI because it must not be bypassable. Dependabot keeps dependencies and workflow actions current. Gate semantics are NOT redefined here; CI only invokes them.

## ADDED Requirements

### Requirement: Workflow Triggers and Runner
`.github/workflows/ci.yml` MUST run on `pull_request` and on `push` to `dev`, with no path filters, on the `ubuntu-24.04` runner. It MUST NOT use `pull_request_target`. Default workflow permissions MUST be `contents: read`; write permissions MUST be granted only to the jobs that need them (report, cleanup; `security-events: write` only for CodeQL; `packages: write` only for the GHCR publish job and the promote retag job). `board-sync.yml` MUST keep `contents: read` and write the project only through the `PROJECT_TOKEN` secret, never through `GITHUB_TOKEN` write scopes. Third-party actions MUST be pinned to a major version.

#### Scenario: PR triggers the pipeline
- GIVEN a pull request is opened or updated
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs regardless of which files changed

#### Scenario: Push to dev triggers the pipeline
- GIVEN a commit is pushed to `dev`
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs

#### Scenario: Least privilege
- GIVEN `ci.yml`, `pr-cleanup.yml`, `codeql.yml`, `board-sync.yml`, the GHCR workflow and `promote.yml`
- WHEN their permissions are inspected
- THEN the workflow default is `contents: read`, write scopes appear only on the report and cleanup jobs, `security-events: write` appears only in `codeql.yml`, `packages: write` appears only on the publish and retag jobs, `board-sync.yml` requests no write scope, and no workflow uses `pull_request_target`

### Requirement: No Static Analysis in CI
CI MUST NOT run quality static analysis. `ci.yml` MUST NOT contain jobs or steps for `spotlessCheck`, `:app:lintDebug`, detekt, actionlint, shellcheck, script harnesses, commit-message validation, or PR-title validation. These quality gates run only locally through the lefthook hooks; skipping them with `--no-verify`, the GitHub web UI, or without `lefthook install` is an explicit, accepted risk. Security scanning is NOT static analysis in this sense and MUST run in CI: gitleaks (`secret-scan`) and CodeQL.

#### Scenario: No lint jobs
- GIVEN the merged `ci.yml`
- WHEN its jobs are listed
- THEN no job invokes a quality static-analysis tool or a commit/PR-title validator

#### Scenario: Bypassed hook is not caught in CI for quality
- GIVEN a commit pushed with `--no-verify` that has a formatting, lint, detekt or message-format violation
- WHEN the pipeline runs
- THEN no CI job rejects it for those reasons

#### Scenario: Bypassed hook is caught in CI for secrets
- GIVEN a commit pushed with `--no-verify` containing a secret
- WHEN the pipeline runs
- THEN `secret-scan` fails and `ci-gate` fails

### Requirement: Unit Test Job
The `unit-tests` job MUST have no `needs`, MUST run `:app:testDebugUnitTest` and generate Kover XML and HTML coverage, and MUST upload its results as artifacts per the artifact contract. It MUST NOT build or test `/backend`; that is owned by `backend-tests` and `contract-tests`.

#### Scenario: Tests run on every trigger
- GIVEN a PR or a `dev` push
- WHEN the pipeline starts
- THEN `unit-tests` runs `:app:testDebugUnitTest`, produces Kover XML and HTML, and uploads artifacts

#### Scenario: Test failure still uploads results
- GIVEN a unit test fails
- WHEN `unit-tests` finishes
- THEN the job fails and its test result artifacts are still uploaded

#### Scenario: Backend not run here
- GIVEN the `unit-tests` job
- WHEN its steps are inspected
- THEN none invoke Gradle in `/backend`

### Requirement: Artifact Contract
Each test suite MUST publish artifacts named `allure-results-<suite>` (JUnit XML results), `coverage-<suite>` (Kover reports) and, for suites that produce them, `pit-<suite>`. Artifact names MUST be unique per run (no overwrite). The current suites are `unit`, `backend` and `contract` (`pit-contract` is produced by `contract`). Downstream jobs MUST discover results by these name patterns so future suites (E2E) attach without changing the report job.

#### Scenario: Unit suite artifacts
- GIVEN `unit-tests` completes
- WHEN artifacts are listed
- THEN `allure-results-unit` and `coverage-unit` exist

#### Scenario: Backend suites artifacts
- GIVEN `backend-tests` and `contract-tests` complete
- WHEN artifacts are listed
- THEN `allure-results-backend`, `coverage-backend`, `allure-results-contract`, `coverage-contract` and `pit-contract` exist

#### Scenario: Unknown future suite is merged
- GIVEN an additional artifact named `allure-results-<other>` exists
- WHEN the report job runs
- THEN its results are merged into the same report without workflow changes

### Requirement: Report Generation and Publishing
The `report` job MUST run with `if: !cancelled()` after `unit-tests`, `backend-tests` and `contract-tests` (it still runs when tests fail, but not on a cancelled run), merge all `allure-results-*` artifacts, generate an Allure 2.46.2 report from the JUnit XML, and publish the Allure report and the Kover HTML report to the `gh-pages` branch under `pr-<N>/` for pull requests and `dev/` for pushes to `dev`. The coverage summary MUST recognize both Android and JVM Kover report names, and MUST surface backend coverage and the PIT score. Publishing MUST preserve other folders (`keep_files`) and runs MUST be serialized with `concurrency` group `gh-pages`. Publishing MUST run only for same-repository PRs and `dev` pushes. If the Allure 2 spike shows JUnit XML is not rendered acceptably, Allure 3 MAY be substituted without altering any other requirement.

#### Scenario: PR report published
- GIVEN a same-repo PR numbered 12 whose tests ran
- WHEN the report job completes
- THEN Allure and Kover reports are available under `pr-12/` on `gh-pages`

#### Scenario: Dev report published
- GIVEN a push to `dev`
- WHEN the report job completes
- THEN the reports are available under `dev/` and existing `pr-<N>/` folders are untouched

#### Scenario: Report on failing tests
- GIVEN `unit-tests`, `backend-tests` or `contract-tests` failed after producing results
- WHEN the report job runs
- THEN it still generates and publishes the report

#### Scenario: Backend results shown
- GIVEN `backend-tests` and `contract-tests` produced results
- WHEN the report job completes
- THEN backend results appear in the merged Allure report and backend coverage figures are computed from JVM Kover output

#### Scenario: Missing results
- GIVEN all test jobs were skipped and no result artifacts exist
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
CI MUST define a job named exactly `ci-gate` that runs with `if: always()`, depends only on `unit-tests`, `backend-tests`, `contract-tests` and `secret-scan` (the gating jobs; `report` is not gating), and succeeds only if all four succeeded. A skipped or failed gating job MUST make `ci-gate` fail. A failure of the `report` job alone MUST NOT fail `ci-gate`. The job name MUST NOT change, as it is the sole required status check for the `dev` ruleset.

#### Scenario: Everything green
- GIVEN `unit-tests`, `backend-tests`, `contract-tests` and `secret-scan` succeeded
- WHEN `ci-gate` evaluates
- THEN it succeeds

#### Scenario: Test failure
- GIVEN `unit-tests` failed
- WHEN `ci-gate` evaluates
- THEN it fails

#### Scenario: Backend or contract failure
- GIVEN `backend-tests` or `contract-tests` failed or was skipped
- WHEN `ci-gate` evaluates
- THEN it fails

#### Scenario: Secret scan failure
- GIVEN the test jobs succeeded and `secret-scan` failed or was skipped
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

### Requirement: Issue Linking and Manual Closing
Promotion MUST NOT be relied upon to close issues: the S2 spike (issue #39, 2026-11-17) showed that a fast-forward promotion of a commit with `Closes #N` does not close the issue, and the user chose manual closing over a `promote.yml` fallback. Each issue's branch MUST be created from the issue (`gh issue develop <N> --base dev`), so it is linked in the issue's Development section, and the PR body MUST reference the issue. Board Status MAY be updated automatically by `board-sync` (In review on PR open or ready, In dev (QA) on merge), but no automation MUST close an issue or comment on it. Before an issue is closed and moved to Done, a summary comment of the work MUST be posted on it, and the issue MUST be closed by hand. `promote.yml` MUST NOT request `issues: write`.

#### Scenario: Branch linked to the issue
- GIVEN work starts on issue N
- WHEN the branch is created with `gh issue develop N --base dev`
- THEN the branch appears in issue N's Development section

#### Scenario: Promotion leaves issues open
- GIVEN a promoted commit contains `Closes #N`
- WHEN `promote.yml` fast-forwards `main`
- THEN issue N stays open and the promotion succeeds

#### Scenario: Summary before closing
- GIVEN issue N has `qa:approved` and its commit is promoted
- WHEN it is moved to Done
- THEN a summary comment exists on issue N and the issue is closed by hand

#### Scenario: Board status automated, closing manual
- GIVEN a PR for issue N is opened or merged and `board-sync` runs
- WHEN the run finishes
- THEN issue N's board Status is In review or In dev (QA) and the issue is still open with no automated comment

#### Scenario: No issues write in promote
- GIVEN `promote.yml`
- WHEN its permissions are inspected
- THEN `issues: write` is not requested

### Requirement: Documentation of CI and Admin Steps
`README.md` (in Spanish) MUST document the pipeline (tests, reports, secret scan and CodeQL in CI; quality static analysis local-only), a "what runs where" table, how to reach the reports, the artifact contract, an accepted-risk note stating that quality gates are bypassable while secrets are caught in CI, the SonarQube for IDE recommendation, and the manual admin steps in order: enabling Pages from `gh-pages` after the branch exists, adding `ci-gate` (Actions integration) as a required status check to the `dev` ruleset after the first green run, adding the "Require code scanning results" rule after the first green `dev` CodeQL analysis, enabling Dependabot alerts and security updates, and configuring the squash merge message to include the PR description.

#### Scenario: Admin steps documented
- GIVEN the README
- WHEN a maintainer reads the CI section
- THEN it lists the Pages, required-check, code-scanning rule, Dependabot, and merge-message steps in order

#### Scenario: What-runs-where table and risk note
- GIVEN the README
- WHEN it is read
- THEN it contains the table of which gate runs locally versus in CI, the updated accepted-risk note, and the SonarQube for IDE recommendation

### Requirement: Out of Scope Boundaries
This change MUST NOT add E2E jobs, SonarCloud or any paid tooling, quality gates (spotless, lint, detekt, commit-msg) in CI, `workflow_run` based comments for fork PRs, or a Robolectric version upgrade, and MUST NOT modify application code or gate semantics in `code-quality-gates` beyond adding detekt and extending its existing gates to `/backend`. Backend `backend-tests`, `contract-tests` (including PIT) and the GHCR publish job are permitted; no other jobs are.

#### Scenario: Only expected jobs
- GIVEN the merged workflows
- WHEN their jobs are listed
- THEN `ci.yml` has only `unit-tests`, `backend-tests`, `contract-tests`, `secret-scan`, `report`, `ci-gate`, `publish-image`, and the other workflows are only CodeQL analysis, cleanup, promote, board sync

#### Scenario: No forbidden tooling
- GIVEN the merged workflows
- WHEN their steps are inspected
- THEN none run SonarCloud, E2E suites, or quality static analysis

### Requirement: CodeQL Analysis Workflow
`.github/workflows/codeql.yml` MUST analyze the `java-kotlin` language with `build-mode: manual`, building the app and the backend with Gradle using `--no-daemon --no-build-cache` (the in-process Kotlin compile flag is subject to spike S2). The backend build MUST run on JDK 21 and Kotlin files under `/backend` MUST be extracted. It MUST run on `pull_request` to `dev` with no path filters, on `push` to `dev`, and on a weekly `schedule`. Its permissions MUST be `contents: read` and `security-events: write` only. It MUST NOT use `pull_request_target`.

#### Scenario: PR to dev is analyzed
- GIVEN a pull request targeting `dev` that changes only documentation
- WHEN GitHub evaluates workflows
- THEN `codeql.yml` runs and uploads an analysis

#### Scenario: Push and schedule trigger
- GIVEN a push to `dev`, or the weekly schedule firing
- WHEN GitHub evaluates workflows
- THEN `codeql.yml` runs

#### Scenario: Minimal permissions
- GIVEN `codeql.yml`
- WHEN its permissions are inspected
- THEN only `contents: read` and `security-events: write` are granted

#### Scenario: Kotlin is actually extracted
- GIVEN a completed analysis on `dev`
- WHEN its results are inspected
- THEN Kotlin source files appear among the analyzed files

#### Scenario: Backend module analyzed
- GIVEN a completed analysis
- WHEN its analyzed files are inspected
- THEN Kotlin files under `backend/src/main` appear among them

### Requirement: Code Scanning Merge Protection
The `dev` ruleset (27200640) MUST include the rule "Require code scanning results" for the CodeQL tool, with thresholds Alerts = Errors and Security alerts = High or higher. The rule MUST be added only after the first green CodeQL analysis on `dev`. A new alert at or above the thresholds MUST block merging the PR that introduces it.

#### Scenario: Qualifying alert blocks merge
- GIVEN a PR introducing an alert of severity error or high security severity
- WHEN merging is attempted
- THEN the ruleset blocks the merge

#### Scenario: Clean PR merges
- GIVEN a PR whose CodeQL analysis finishes with no qualifying alert
- WHEN merging is attempted
- THEN the code-scanning rule does not block the merge

#### Scenario: Rule added after a green dev analysis
- GIVEN no successful CodeQL analysis exists on `dev`
- WHEN the admin steps are followed
- THEN the code-scanning rule is not yet added to the ruleset

### Requirement: Secret Scan Job
`ci.yml` MUST contain a job `secret-scan` with `contents: read` and `fetch-depth: 0`. It MUST run the gitleaks v8.30.1 binary, verified against the pinned sha256 `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb` before execution, with `--redact`. The scanned range MUST be base..head for a PR, before..sha for a push, and the head commit only when `before` is all zeros. If `before` is not all zeros but is unreachable or malformed, the range selection MUST fail closed (non-zero exit with a clear error) instead of narrowing the scan. A detected secret MUST fail the job.

#### Scenario: Secret pushed with --no-verify fails
- GIVEN a commit with a fake secret pushed with `--no-verify`
- WHEN `secret-scan` runs
- THEN it fails and the secret value is redacted in the log

#### Scenario: Clean range passes
- GIVEN a PR range with no secrets
- WHEN `secret-scan` runs
- THEN it succeeds

#### Scenario: New branch push
- GIVEN a push whose `before` SHA is all zeros
- WHEN `secret-scan` selects its range
- THEN it scans the head commit only

#### Scenario: Unreachable before fails closed
- GIVEN a push whose `before` SHA is not all zeros and is not present in the fetched history
- WHEN `secret-scan` selects its range
- THEN it exits non-zero with a clear error and does not fall back to scanning the head commit only

#### Scenario: Checksum mismatch
- GIVEN the downloaded binary does not match the pinned sha256
- WHEN `secret-scan` runs
- THEN the job fails before executing the binary

### Requirement: Dependabot Configuration
`.github/dependabot.yml` MUST configure version updates for the `gradle` ecosystem (root and `/backend` directories), the `docker` ecosystem (`/backend`) and `github-actions`, weekly, with `target-branch: dev`, commit-message prefixes `build` (gradle, docker) and `ci` (github-actions) with `include: scope`, grouped updates, and a bounded open-PR limit. Dependabot security updates and vulnerability alerts MUST be enabled. Because security-update PRs target `main`, the README MUST document retargeting them to `dev` by hand.

#### Scenario: Config valid and scoped
- GIVEN `.github/dependabot.yml`
- WHEN it is parsed
- THEN every ecosystem entry is weekly, targets `dev`, uses the stated prefixes, is grouped, and sets an open-PR limit

#### Scenario: Backend Gradle covered
- GIVEN `.github/dependabot.yml`
- WHEN its `gradle` entries are read
- THEN one entry has directory `/backend`

#### Scenario: Docker base image covered
- GIVEN `.github/dependabot.yml`
- WHEN its `docker` entry is read
- THEN it has directory `/backend` so the pinned base digest is updated

#### Scenario: Security features enabled
- GIVEN the repository settings
- WHEN they are inspected after the admin step
- THEN vulnerability alerts and Dependabot security updates are enabled

#### Scenario: Retargeting documented
- GIVEN the README
- WHEN a maintainer reads the Dependabot section
- THEN it states that security PRs target `main` and must be retargeted to `dev`

### Requirement: Dependabot PR Report Guard
The `report` job publish steps (`gh-pages` push and sticky comment) MUST be skipped when the actor is `dependabot[bot]`. Tests, `secret-scan` and `ci-gate` MUST still run for Dependabot PRs.

#### Scenario: Dependabot PR skips publishing
- GIVEN a PR opened by `dependabot[bot]`
- WHEN the pipeline runs
- THEN no report is published and no comment is posted, and the pipeline does not fail because of it

#### Scenario: Dependabot PR still gated
- GIVEN a PR opened by `dependabot[bot]`
- WHEN the pipeline runs
- THEN `unit-tests`, `secret-scan` and `ci-gate` run

### Requirement: Backend Test Job

`ci.yml` MUST contain a `backend-tests` job with no `needs`, on JDK 21, that runs the backend unit and Testcontainers integration tests from `/backend`, generates Kover XML and HTML coverage, and uploads `allure-results-backend` and `coverage-backend` artifacts even when tests fail. It MUST NOT use services or stubs in place of the Testcontainers PostgreSQL.

#### Scenario: Backend tests run on every trigger
- GIVEN a PR or a `dev` push
- WHEN the pipeline starts
- THEN `backend-tests` runs the backend tests against PostgreSQL via Testcontainers and uploads its artifacts

#### Scenario: Failure still uploads results
- GIVEN a backend test fails
- WHEN `backend-tests` finishes
- THEN the job fails and `allure-results-backend` is still uploaded

### Requirement: Contract Test Job With PIT

`ci.yml` MUST contain a `contract-tests` job with no `needs` that runs the REST Assured contract tests and the PIT mutation gate (threshold 80) from `/backend`, and uploads `allure-results-contract`, `coverage-contract` and `pit-contract`. A contract failure or a PIT score below the threshold MUST fail the job.

#### Scenario: Contract tests and PIT run
- GIVEN a PR or a `dev` push
- WHEN the pipeline starts
- THEN `contract-tests` runs contract tests and PIT and uploads its artifacts

#### Scenario: PIT below threshold
- GIVEN the mutation score is below 80
- WHEN `contract-tests` finishes
- THEN the job fails and `pit-contract` is still uploaded

#### Scenario: Contract drift
- GIVEN a response deviates from `openapi.yaml`
- WHEN `contract-tests` finishes
- THEN the job fails

### Requirement: GHCR Image Publication

The `publish-image` job in `.github/workflows/ci.yml` MUST build the backend jar on the runner, build the image from `backend/Dockerfile`, and push it to GHCR only on `push` to `dev`, tagged `sha-<commit>` and `dev`. It MUST NOT push on pull requests or from forks. `packages: write` MUST be granted only to the publishing job, which MUST depend on a green backend gate. The package MUST remain private; visibility is decided in #8.

#### Scenario: Dev push publishes
- GIVEN a push to `dev` with green backend jobs
- WHEN the publish job runs
- THEN the image is pushed with tags `sha-<commit>` and `dev`

#### Scenario: PR does not publish
- GIVEN a pull request
- WHEN workflows run
- THEN no image is pushed and no job holds `packages: write`

#### Scenario: Failed gate blocks publish
- GIVEN `backend-tests` or `contract-tests` failed on `dev`
- WHEN the publish job is evaluated
- THEN it does not push

### Requirement: Promote Retags the Same Image Digest

`promote.yml` MUST, when promoting a SHA to `main`, retag the existing GHCR image `sha-<commit>` as `staging` without rebuilding, so that the `staging` digest equals the `sha-<commit>` digest. `packages: write` MUST be granted only to that job. Promotion MUST fail if the `sha-<commit>` image does not exist.

#### Scenario: Same digest retagged
- GIVEN image `sha-<commit>` exists in GHCR
- WHEN `promote.yml` promotes that commit
- THEN `staging` points to the identical digest

#### Scenario: Missing image fails
- GIVEN no image `sha-<commit>` exists
- WHEN `promote.yml` runs
- THEN promotion fails and `main` is not updated

#### Scenario: Scoped permission
- GIVEN `promote.yml`
- WHEN its permissions are inspected
- THEN `packages: write` appears only on the retag job and `issues: write` is not requested
