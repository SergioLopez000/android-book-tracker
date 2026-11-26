## Capability: ci-pipeline

### Purpose
GitHub Actions orchestration that runs the unit tests and the security scans (gitleaks secret scan, CodeQL) on every pull request and `dev` push, publishes linked test and coverage reports, exposes a single required check, cleans up per-PR reports, and ensures promotion to `main` requires a green pipeline. Quality static analysis (format, lint, detekt, commit-message) stays local-only, see `code-quality-gates`; security scanning runs in CI because it must not be bypassable. Dependabot keeps dependencies and workflow actions current. Gate semantics are NOT redefined here; CI only invokes them.

## ADDED Requirements

### Requirement: Workflow Triggers and Runner
`.github/workflows/ci.yml` MUST run on `pull_request` and on `push` to `dev`, with no path filters, on the `ubuntu-24.04` runner. It MUST NOT use `pull_request_target`. Default workflow permissions MUST be `contents: read`; write permissions MUST be granted only to the jobs that need them (report, cleanup; `security-events: write` only for CodeQL; `issues: write` only for the `PROJECT_TOKEN` expiry reminder job). No workflow MUST request `packages: write`. The `e2e-tests` job MUST NOT be granted any write permission. `board-sync.yml` MUST keep `contents: read` and write the project only through the `PROJECT_TOKEN` secret, never through `GITHUB_TOKEN` write scopes. Every workflow MUST declare `permissions:` and reference only allow-listed secrets, per "Workflow Permissions Declared" and "Secrets Allow-List". Third-party actions MUST be pinned to a major version.
(Previously: `packages: write` was allowed for the GHCR publish job and the promote retag job.)

#### Scenario: PR triggers the pipeline
- GIVEN a pull request is opened or updated
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs regardless of which files changed

#### Scenario: Push to dev triggers the pipeline
- GIVEN a commit is pushed to `dev`
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs

#### Scenario: Least privilege
- GIVEN `ci.yml`, `pr-cleanup.yml`, `codeql.yml`, `board-sync.yml`, `promote.yml` and the expiry reminder workflow
- WHEN their permissions are inspected
- THEN the workflow default is `contents: read`, write scopes appear only on the report and cleanup jobs, `security-events: write` appears only in `codeql.yml`, `issues: write` appears only in the expiry reminder workflow, `packages: write` appears nowhere, `board-sync.yml` requests no write scope, the `e2e-tests` job holds no write scope, and no workflow uses `pull_request_target`

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
The `unit-tests` job MUST have no `needs`, MUST run `:app:testDebugUnitTest` and generate Kover XML and HTML coverage, and MUST upload its results as artifacts per the artifact contract. It MUST NOT build or test `/backend`; the backend is frozen, see "Backend Frozen in CI".
(Previously: named `backend-tests` and `contract-tests` as the owners of the backend.)

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
Each test suite MUST publish artifacts named `allure-results-<suite>` and `coverage-<suite>` (Kover reports). `allure-results-<suite>` holds JUnit XML results, except for the `e2e` suite, which holds Allure JSON results. Artifact names MUST be unique per run (no overwrite). The current suites are `unit` and `e2e` (`allure-results-e2e` only, retained for 7 days). Downstream jobs MUST discover results by these name patterns so future suites attach without changing the report job.
(Previously: also listed the `backend` and `contract` suites and the `pit-<suite>` artifact.)

#### Scenario: Unit suite artifacts
- GIVEN `unit-tests` completes
- WHEN artifacts are listed
- THEN `allure-results-unit` and `coverage-unit` exist

#### Scenario: No backend artifacts
- GIVEN a completed pipeline run
- WHEN artifacts are listed
- THEN none named `allure-results-backend`, `coverage-backend`, `allure-results-contract`, `coverage-contract` or `pit-contract` exist

#### Scenario: E2E suite artifact
- GIVEN `e2e-tests` completes
- WHEN artifacts are listed
- THEN `allure-results-e2e` exists with Allure JSON results and a 7-day retention, and no `coverage-e2e` exists

#### Scenario: Unknown future suite is merged
- GIVEN an additional artifact named `allure-results-<other>` exists
- WHEN the report job runs
- THEN its results are merged into the same report without workflow changes

### Requirement: Report Generation and Publishing
The `report` job MUST run with `if: !cancelled()` after `unit-tests` and `e2e-tests` (it still runs when tests fail, but not on a cancelled run), merge all `allure-results-*` artifacts, generate an Allure 2.46.2 report from the JUnit XML and the e2e Allure JSON results, and publish the Allure report and the Kover HTML report to the `gh-pages` branch under `pr-<N>/` for pull requests and `dev/` for pushes to `dev`. The coverage summary MUST surface the app (Android) coverage only and MUST NOT aggregate backend coverage, PIT scores, or link a backend Kover report. Publishing MUST preserve other folders (`keep_files`) and runs MUST be serialized with `concurrency` group `gh-pages`. Publishing MUST run only for same-repository PRs and `dev` pushes. If the Allure 2 spike shows JUnit XML or WebdriverIO JSON is not rendered acceptably, Allure 3 MAY be substituted without altering any other requirement.
(Previously: waited for `backend-tests` and `contract-tests` and surfaced backend coverage and the PIT score.)

#### Scenario: PR report published
- GIVEN a same-repo PR numbered 12 whose tests ran
- WHEN the report job completes
- THEN Allure and Kover reports are available under `pr-12/` on `gh-pages`

#### Scenario: Dev report published
- GIVEN a push to `dev`
- WHEN the report job completes
- THEN the reports are available under `dev/` and existing `pr-<N>/` folders are untouched

#### Scenario: Report on failing tests
- GIVEN `unit-tests` or `e2e-tests` failed after producing results
- WHEN the report job runs
- THEN it still generates and publishes the report

#### Scenario: Report waits for e2e
- GIVEN `e2e-tests` is still running while `unit-tests` finished
- WHEN the report job is evaluated
- THEN it does not start until `e2e-tests` completes

#### Scenario: Report needs only unit and e2e
- GIVEN `ci.yml`
- WHEN the `needs` of the `report` job are read
- THEN they are exactly `unit-tests` and `e2e-tests`, and no step references backend coverage, PIT or `pit-contract`

#### Scenario: E2E results shown
- GIVEN `e2e-tests` produced `allure-results-e2e`, with a screenshot on a failed test
- WHEN the report job completes
- THEN the e2e results and the failure screenshot appear in the merged Allure report

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
CI MUST define a job named exactly `ci-gate` that runs with `if: always()`, depends only on `unit-tests` and `secret-scan` (the gating jobs; `report` is not gating), and succeeds only if both succeeded. A skipped or failed gating job MUST make `ci-gate` fail. A failure of the `report` job alone MUST NOT fail `ci-gate`. The job name MUST NOT change, as it is the sole required status check for the `dev` ruleset.
(Previously: also depended on `backend-tests` and `contract-tests`.)

#### Scenario: Everything green
- GIVEN `unit-tests` and `secret-scan` succeeded
- WHEN `ci-gate` evaluates
- THEN it succeeds

#### Scenario: Test failure
- GIVEN `unit-tests` failed
- WHEN `ci-gate` evaluates
- THEN it fails

#### Scenario: Secret scan failure
- GIVEN `unit-tests` succeeded and `secret-scan` failed or was skipped
- WHEN `ci-gate` evaluates
- THEN it fails

#### Scenario: Report outage
- GIVEN all gating jobs passed but `report` failed (e.g. Pages push error)
- WHEN `ci-gate` evaluates
- THEN it succeeds

#### Scenario: No removed job in needs
- GIVEN `ci.yml`
- WHEN the `needs` of `ci-gate` are read
- THEN they are exactly `unit-tests` and `secret-scan`, and `actionlint` exits 0

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
The pipeline MUST NOT include SonarCloud or any paid tooling, quality gates (spotless, lint, detekt, commit-msg, tsc) in CI, `workflow_run` based comments for fork PRs, or a Robolectric version upgrade, and this capability MUST NOT redefine gate semantics owned by `code-quality-gates`. It MUST NOT add Trivy or gitleaks beyond the existing `secret-scan` job. It MUST NOT build, test, scan, publish or promote the frozen backend. Permitted jobs in `ci.yml` are `unit-tests`, `e2e-tests`, `secret-scan`, `report` and `ci-gate`; no other jobs are. The `e2e-tests` job MUST NOT be a dependency of `ci-gate` until a later change makes it gating.
(Previously: also permitted `backend-tests`, `contract-tests` and `publish-image`.)

#### Scenario: Only expected jobs
- GIVEN the merged workflows
- WHEN their jobs are listed
- THEN `ci.yml` has only `unit-tests`, `e2e-tests`, `secret-scan`, `report` and `ci-gate`, and the other workflows are only CodeQL analysis, cleanup, promote, board sync and the `PROJECT_TOKEN` expiry reminder

#### Scenario: No forbidden tooling
- GIVEN the merged workflows
- WHEN their steps are inspected
- THEN none run SonarCloud, Trivy, an additional gitleaks scan or quality static analysis; the only test suite beyond unit is the e2e suite

### Requirement: CodeQL Analysis Workflow
`.github/workflows/codeql.yml` MUST analyze the `java-kotlin` language with `build-mode: manual`, building the app with Gradle using `--no-daemon --no-build-cache` (the in-process Kotlin compile flag is subject to spike S2). It MUST NOT install JDK 21 or build `/backend`. It MUST run on `pull_request` to `dev` with no path filters, on `push` to `dev`, and on a weekly `schedule`. Its permissions MUST be `contents: read` and `security-events: write` only. It MUST NOT use `pull_request_target`.
(Previously: also built the backend on JDK 21 and required its Kotlin files to be extracted.)

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
- THEN Kotlin source files under `app/` appear among the analyzed files

#### Scenario: Backend not built
- GIVEN `codeql.yml`
- WHEN `grep -En 'backend|java-version: *"?21' .github/workflows/codeql.yml` runs
- THEN it matches nothing

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
`.github/dependabot.yml` MUST configure version updates for the `gradle` ecosystem (root directory only), the `npm` ecosystem (`/e2e`) and `github-actions`, weekly, with `target-branch: dev`, commit-message prefixes `build` (gradle, npm) and `ci` (github-actions) with `include: scope`, grouped updates, and a bounded open-PR limit. It MUST NOT contain any `/backend` entry or a `docker` ecosystem entry. Dependabot security updates and vulnerability alerts MUST be enabled. Because security-update PRs target `main`, the README MUST document retargeting them to `dev` by hand.
(Previously: also covered the `gradle` `/backend` directory and the `docker` ecosystem for `/backend`.)

#### Scenario: Config valid and scoped
- GIVEN `.github/dependabot.yml`
- WHEN it is parsed
- THEN every ecosystem entry is weekly, targets `dev`, uses the stated prefixes, is grouped, and sets an open-PR limit

#### Scenario: Root Gradle covered
- GIVEN `.github/dependabot.yml`
- WHEN its `gradle` entries are read
- THEN exactly one entry exists and its directory is `/`

#### Scenario: No backend or docker entry
- GIVEN `.github/dependabot.yml`
- WHEN it is searched for `/backend` and the `docker` ecosystem
- THEN neither is present

#### Scenario: npm e2e covered
- GIVEN `.github/dependabot.yml`
- WHEN its `npm` entry is read
- THEN it has directory `/e2e`, is weekly and targets `dev`

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

### Requirement: E2E Test Job
`ci.yml` MUST contain an `e2e-tests` job with no `needs`, running on `ubuntu-24.04` against an Android emulator, that installs `/e2e` dependencies with `npm ci` using the Node version from `.nvmrc`, builds the debug APK and runs the Appium happy-path suite. It MUST run on every pull request and every `dev` push with no path or label filters. It MUST have `permissions: contents: read` only and MUST NOT use any secret, so it is safe for fork PRs. It MUST upload `allure-results-e2e` with `retention-days: 7`, even when tests fail. It is non-gating: `ci-gate` MUST NOT depend on it, and its failure MUST NOT fail `ci-gate`.
(Previously: the non-gating scenario also named `backend-tests` and `contract-tests`.)

#### Scenario: Runs on every trigger
- GIVEN a PR touching only documentation, or a `dev` push
- WHEN the pipeline starts
- THEN `e2e-tests` runs

#### Scenario: Failure still uploads results
- GIVEN an e2e test fails
- WHEN `e2e-tests` finishes
- THEN the job fails and `allure-results-e2e` is still uploaded with `retention-days: 7`

#### Scenario: Read-only and secret-free
- GIVEN the `e2e-tests` job definition
- WHEN its permissions and environment are inspected
- THEN only `contents: read` is granted and no `secrets.*` reference appears

#### Scenario: Fork PR is safe
- GIVEN a PR from a fork
- WHEN `e2e-tests` runs
- THEN it runs under `pull_request` without secrets or write scopes

#### Scenario: Non-gating
- GIVEN `unit-tests` and `secret-scan` succeeded and `e2e-tests` failed
- WHEN `ci-gate` evaluates
- THEN it succeeds

### Requirement: Workflow Permissions Declared

Every workflow under `.github/workflows/` MUST declare a top-level `permissions:` block, and every job MUST either declare its own `permissions:` block or inherit a top-level block that is `contents: read`. A static check MUST fail when a workflow lacks a top-level block or when a job in a workflow without a read-only top-level block lacks its own.

#### Scenario: All workflows declare permissions
- GIVEN every workflow under `.github/workflows/`
- WHEN the static check runs
- THEN each has a top-level `permissions:` block and the check passes

#### Scenario: Missing top-level block
- GIVEN a workflow with no top-level `permissions:`
- WHEN the static check runs
- THEN it fails naming the workflow

#### Scenario: Job without permissions under a non-read-only default
- GIVEN a workflow whose top-level permissions grant any write scope and a job with no `permissions:` block
- WHEN the static check runs
- THEN it fails naming the workflow and the job

### Requirement: Secrets Allow-List

Workflows MUST reference only allow-listed `secrets.*` names. The allow-list MUST initially contain only `PROJECT_TOKEN`, and `github.token` and `secrets.GITHUB_TOKEN` MUST be treated as automatic tokens, not stored secrets. A static check MUST fail on any other `secrets.*` reference, and adding a name to the allow-list MUST require adding it to the secret inventory in the same change.

#### Scenario: Only allowed references
- GIVEN the workflows reference only `secrets.PROJECT_TOKEN`
- WHEN the static check runs
- THEN it passes

#### Scenario: Unlisted secret
- GIVEN a workflow referencing `secrets.NEW_API_KEY`
- WHEN the static check runs
- THEN it fails naming the workflow and the secret name

#### Scenario: Secret-free job stays secret-free
- GIVEN the `e2e-tests` job
- WHEN the static check runs
- THEN no `secrets.*` reference appears in it

### Requirement: Static Contract Checks Self-Tested and Run Locally

The workflow-permissions and secrets allow-list checks MUST live in a script under `scripts/` with a self-test harness, in the style of the existing `scripts/test-*.sh` harnesses, covering one passing and one failing fixture per rule. The script MUST NOT check or carry fixtures for `backend/Dockerfile`. The script MUST run through the lefthook hooks, whose `secrets-contract` glob MUST NOT include `backend/Dockerfile`, and MUST NOT be added to `ci.yml` as a job or step, consistent with "No Static Analysis in CI".
(Previously: also covered the Dockerfile checks, took `scripts/test-image-config.sh` as its style reference, and noted `verifyPackagedArtifact` in `backend-tests`.)

#### Scenario: Self-test covers failures
- GIVEN the self-test harness `scripts/test-secrets-contract.sh`
- WHEN it runs
- THEN it exits 0, and each remaining rule is shown to fail on a violating fixture and pass on a clean one

#### Scenario: No Dockerfile checks or fixtures
- GIVEN `scripts/check-secrets-contract.sh` and `scripts/test-secrets-contract.sh`
- WHEN `grep -in 'dockerfile' scripts/check-secrets-contract.sh scripts/test-secrets-contract.sh` runs
- THEN it matches nothing

#### Scenario: Runs in local hooks
- GIVEN a commit touching a workflow, `docs/secrets.md` or a secrets-contract script
- WHEN the lefthook hooks run
- THEN the static contract checks execute, and a commit touching only `backend/Dockerfile` does not trigger them

#### Scenario: Not a CI job
- GIVEN the merged `ci.yml`
- WHEN its jobs and steps are listed
- THEN none invokes the static contract check script

### Requirement: Secret Inventory Document

`docs/secrets.md` (in English) MUST exist and MUST list every secret and credential with its owner, scope, storage location and rotation policy. The owner of every row MUST be `SergioLopez000`. Rows for secrets that do not yet exist MUST be marked as planned and name the change that creates them ("created by #8" or "created by #29"). The document MUST describe the Environments model (`dev` and `staging`, with `dev` created by #8), a rotation runbook (create the new value, set it in each environment, restart or redeploy, revoke the old value, verify), per-class rotation cadence, and the stop-gap manual restriction of the shipped Google Books key with its rotation gated on #29. The document MUST remain host-agnostic and MUST NOT contain secret values.

#### Scenario: Row fields complete
- GIVEN any row of the inventory
- WHEN it is read
- THEN it has owner `SergioLopez000`, scope, location and rotation policy

#### Scenario: Planned rows marked
- GIVEN a secret that does not exist yet
- WHEN its row is read
- THEN it is marked planned and names #8 or #29 as its creator

#### Scenario: Runbook and cadence present
- GIVEN `docs/secrets.md`
- WHEN its sections are listed
- THEN it has the Environments model, the rotation runbook and a rotation cadence per secret class

#### Scenario: Google Books key gated
- GIVEN the Google Books key row or runbook entry
- WHEN it is read
- THEN it states that no client key exists (not provisioned) and that the row is superseded by `GOOGLE_BOOKS_SERVER_API_KEY` (#29)
- AND the runbook warns that an Android-app restriction would reject the direct REST calls

### Requirement: Inventory Drift Guard

A test MUST fail when a `secrets.*` name referenced by any workflow (excluding the automatic `GITHUB_TOKEN`) is not listed in `docs/secrets.md`, and when the secrets allow-list contains a name absent from the inventory. The failure MUST name the missing secret.

#### Scenario: Inventory matches workflows
- GIVEN every `secrets.*` name in workflows is listed in the inventory
- WHEN the drift guard runs
- THEN it passes

#### Scenario: Undocumented secret
- GIVEN a workflow references `secrets.PROJECT_TOKEN` and the inventory omits it
- WHEN the drift guard runs
- THEN it fails naming `PROJECT_TOKEN`

#### Scenario: Guard has a self-test
- GIVEN the drift guard
- WHEN its self-test runs
- THEN it demonstrates both a passing and a failing case

### Requirement: Secrets Section in README

`README.md` (in Spanish) MUST contain a section "Secretos y rotación" that links to `docs/secrets.md` and summarizes where secrets live and how to rotate them.

#### Scenario: Section and link
- GIVEN the README
- WHEN the section "Secretos y rotación" is read
- THEN it links to `docs/secrets.md` and states the rotation entry point

### Requirement: Backend Frozen in CI

The `backend/` tree MUST be kept unchanged by the freeze (`git diff --stat origin/dev...HEAD -- backend/ app/` empty), and no automation MUST build, test, scan, publish, promote or update dependencies for it. No workflow under `.github/workflows/` MUST reference `/backend`, `backend/`, `backend-tests`, `contract-tests`, `publish-image`, `check-image`, `retag-image`, `ghcr.io`, `docker/login-action`, or `packages: write`. `.github/dependabot.yml` MUST contain no entry whose `directory` is `/backend` and no `docker` ecosystem entry. `scripts/test-image-config.sh` MUST NOT exist and no file MUST reference it. The local Spotless and detekt hooks on `backend/` and the `backend/` build itself are untouched. Reactivation is a single `git revert` of the squash commit of this change.

#### Scenario: Backend and app bytes untouched
- GIVEN the branch of this change
- WHEN `git diff --stat origin/dev...HEAD -- backend/ app/` runs
- THEN its output is empty

#### Scenario: No workflow touches the backend
- GIVEN the merged workflows
- WHEN `grep -rEn 'backend|contract-tests|publish-image|check-image|retag-image|ghcr\.io|docker/login-action|packages: *write' .github/workflows/` runs
- THEN it matches nothing

#### Scenario: Workflows stay valid
- GIVEN the merged workflows
- WHEN `actionlint` runs
- THEN it exits 0, so no `needs` points at a removed job

#### Scenario: Dependabot has no backend entry
- GIVEN `.github/dependabot.yml`
- WHEN `grep -En '/backend|package-ecosystem: *"?docker' .github/dependabot.yml` runs
- THEN it matches nothing

#### Scenario: Image-config harness retired
- GIVEN the merged tree
- WHEN `test -e scripts/test-image-config.sh` and `grep -rn 'test-image-config' . --exclude-dir=.git --exclude-dir=openspec --exclude-dir=node_modules` run
- THEN the file does not exist and the grep matches nothing

#### Scenario: Frozen state documented
- GIVEN `README.md`, `docs/secrets.md` and `openspec/config.yaml`
- WHEN `grep -Eil 'frozen|congelad' README.md docs/secrets.md openspec/config.yaml` runs
- THEN it lists all three files
