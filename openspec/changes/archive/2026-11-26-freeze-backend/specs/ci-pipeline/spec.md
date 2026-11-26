# Delta for ci-pipeline

## ADDED Requirements

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

## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Backend Test Job
(Reason: the backend is frozen (#91); CI no longer runs `backend-tests`. Reactivate by reverting the squash commit.)

### Requirement: Contract Test Job With PIT
(Reason: the backend is frozen (#91); CI no longer runs `contract-tests` or the PIT gate. Reactivate by reverting the squash commit.)

### Requirement: GHCR Image Publication
(Reason: the backend is frozen (#91); `publish-image` is deleted. Existing GHCR images are kept as restorable history.)

### Requirement: Promote Retags the Same Image Digest
(Reason: no image is published, so `check-image` and `retag-image` are deleted and `promote` resolves the SHA itself; "Promotion Requires Green ci-gate" is unchanged.)

### Requirement: No Secret-Like Dockerfile Variables
(Reason: D2, the Dockerfile is frozen with the backend and its checks and fixtures leave the secrets contract.)

## Notes for archive

Requirements checked and left unchanged: "No Static Analysis in CI", "Sticky PR Comment", "PR Report Cleanup", "Promotion Requires Green ci-gate", "Issue Linking and Manual Closing", "Documentation of CI and Admin Steps", "Code Scanning Merge Protection", "Secret Scan Job", "Dependabot PR Report Guard" (it names only `unit-tests`, `secret-scan`, `ci-gate`), "Workflow Permissions Declared", "Secrets Allow-List", "Secret Inventory Document", "Inventory Drift Guard", "Secrets Section in README".

`openspec/specs/backend-api/spec.md` Purpose is not a requirement, so there is no delta. At archive, append this exact sentence to the end of its Purpose paragraph (after "...out of scope."):

> Frozen since 2026-11-26 (#91): the code is kept as is but CI no longer builds, tests, scans, publishes or promotes it, and the app does not use it; revert the `freeze-backend` squash commit to reactivate.

`openspec/specs/code-quality-gates/spec.md`: no change. A grep for `backend-tests|contract-tests|publish-image|test-image-config|Dockerfile|ghcr` returns zero matches; its backend mentions (Spotless, detekt, Kover tasks) describe the untouched build and local hooks.
