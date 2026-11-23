# Delta for ci-pipeline

## ADDED Requirements

### Requirement: E2E Test Job
`ci.yml` MUST contain an `e2e-tests` job with no `needs`, running on `ubuntu-24.04` against an Android emulator, that installs `/e2e` dependencies with `npm ci` using the Node version from `.nvmrc`, builds the debug APK and runs the Appium happy-path suite. It MUST run on every pull request and every `dev` push with no path or label filters. It MUST have `permissions: contents: read` only and MUST NOT use any secret, so it is safe for fork PRs. It MUST upload `allure-results-e2e` with `retention-days: 7`, even when tests fail. It is non-gating: `ci-gate` MUST NOT depend on it, and its failure MUST NOT fail `ci-gate`.

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
- GIVEN `unit-tests`, `backend-tests`, `contract-tests` and `secret-scan` succeeded and `e2e-tests` failed
- WHEN `ci-gate` evaluates
- THEN it succeeds

## MODIFIED Requirements

### Requirement: Workflow Triggers and Runner
`.github/workflows/ci.yml` MUST run on `pull_request` and on `push` to `dev`, with no path filters, on the `ubuntu-24.04` runner. It MUST NOT use `pull_request_target`. Default workflow permissions MUST be `contents: read`; write permissions MUST be granted only to the jobs that need them (report, cleanup; `security-events: write` only for CodeQL; `packages: write` only for the GHCR publish job and the promote retag job). The `e2e-tests` job MUST NOT be granted any write permission. `board-sync.yml` MUST keep `contents: read` and write the project only through the `PROJECT_TOKEN` secret, never through `GITHUB_TOKEN` write scopes. Third-party actions MUST be pinned to a major version.
(Previously: no statement about `e2e-tests` permissions.)

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
- THEN the workflow default is `contents: read`, write scopes appear only on the report and cleanup jobs, `security-events: write` appears only in `codeql.yml`, `packages: write` appears only on the publish and retag jobs, `board-sync.yml` requests no write scope, the `e2e-tests` job holds no write scope, and no workflow uses `pull_request_target`

### Requirement: Artifact Contract
Each test suite MUST publish artifacts named `allure-results-<suite>`, `coverage-<suite>` (Kover reports) and, for suites that produce them, `pit-<suite>`. `allure-results-<suite>` holds JUnit XML results, except for the `e2e` suite, which holds Allure JSON results. Artifact names MUST be unique per run (no overwrite). The current suites are `unit`, `backend`, `contract` (`pit-contract` is produced by `contract`) and `e2e` (`allure-results-e2e` only, retained for 7 days). Downstream jobs MUST discover results by these name patterns so future suites attach without changing the report job.
(Previously: suites were `unit`, `backend`, `contract`; E2E was a future suite; all results were JUnit XML.)

#### Scenario: Unit suite artifacts
- GIVEN `unit-tests` completes
- WHEN artifacts are listed
- THEN `allure-results-unit` and `coverage-unit` exist

#### Scenario: Backend suites artifacts
- GIVEN `backend-tests` and `contract-tests` complete
- WHEN artifacts are listed
- THEN `allure-results-backend`, `coverage-backend`, `allure-results-contract`, `coverage-contract` and `pit-contract` exist

#### Scenario: E2E suite artifact
- GIVEN `e2e-tests` completes
- WHEN artifacts are listed
- THEN `allure-results-e2e` exists with Allure JSON results and a 7-day retention, and no `coverage-e2e` exists

#### Scenario: Unknown future suite is merged
- GIVEN an additional artifact named `allure-results-<other>` exists
- WHEN the report job runs
- THEN its results are merged into the same report without workflow changes

### Requirement: Report Generation and Publishing
The `report` job MUST run with `if: !cancelled()` after `unit-tests`, `backend-tests`, `contract-tests` and `e2e-tests` (it still runs when tests fail, but not on a cancelled run), merge all `allure-results-*` artifacts, generate an Allure 2.46.2 report from the JUnit XML and the e2e Allure JSON results, and publish the Allure report and the Kover HTML report to the `gh-pages` branch under `pr-<N>/` for pull requests and `dev/` for pushes to `dev`. The coverage summary MUST recognize both Android and JVM Kover report names, and MUST surface backend coverage and the PIT score. Publishing MUST preserve other folders (`keep_files`) and runs MUST be serialized with `concurrency` group `gh-pages`. Publishing MUST run only for same-repository PRs and `dev` pushes. If the Allure 2 spike shows JUnit XML or WebdriverIO JSON is not rendered acceptably, Allure 3 MAY be substituted without altering any other requirement.
(Previously: `report` did not wait for an e2e job and the report was generated from JUnit XML only.)

#### Scenario: PR report published
- GIVEN a same-repo PR numbered 12 whose tests ran
- WHEN the report job completes
- THEN Allure and Kover reports are available under `pr-12/` on `gh-pages`

#### Scenario: Dev report published
- GIVEN a push to `dev`
- WHEN the report job completes
- THEN the reports are available under `dev/` and existing `pr-<N>/` folders are untouched

#### Scenario: Report on failing tests
- GIVEN `unit-tests`, `backend-tests`, `contract-tests` or `e2e-tests` failed after producing results
- WHEN the report job runs
- THEN it still generates and publishes the report

#### Scenario: Report waits for e2e
- GIVEN `e2e-tests` is still running while the other test jobs finished
- WHEN the report job is evaluated
- THEN it does not start until `e2e-tests` completes

#### Scenario: Backend results shown
- GIVEN `backend-tests` and `contract-tests` produced results
- WHEN the report job completes
- THEN backend results appear in the merged Allure report and backend coverage figures are computed from JVM Kover output

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

### Requirement: Out of Scope Boundaries
The pipeline MUST NOT include SonarCloud or any paid tooling, quality gates (spotless, lint, detekt, commit-msg, tsc) in CI, `workflow_run` based comments for fork PRs, or a Robolectric version upgrade, and this capability MUST NOT redefine gate semantics owned by `code-quality-gates`. Permitted jobs in `ci.yml` are `unit-tests`, `backend-tests`, `contract-tests`, `e2e-tests`, `secret-scan`, `report`, `ci-gate` and `publish-image`; no other jobs are. The `e2e-tests` job MUST NOT be a dependency of `ci-gate` until a later change makes it gating.
(Previously: "This change MUST NOT add E2E jobs"; change-scoped wording about application code is dropped; `e2e-tests` is now permitted.)

#### Scenario: Only expected jobs
- GIVEN the merged workflows
- WHEN their jobs are listed
- THEN `ci.yml` has only `unit-tests`, `backend-tests`, `contract-tests`, `e2e-tests`, `secret-scan`, `report`, `ci-gate`, `publish-image`, and the other workflows are only CodeQL analysis, cleanup, promote, board sync

#### Scenario: No forbidden tooling
- GIVEN the merged workflows
- WHEN their steps are inspected
- THEN none run SonarCloud or quality static analysis; the only test suite beyond unit and backend is the e2e suite

### Requirement: Dependabot Configuration
`.github/dependabot.yml` MUST configure version updates for the `gradle` ecosystem (root and `/backend` directories), the `docker` ecosystem (`/backend`), the `npm` ecosystem (`/e2e`) and `github-actions`, weekly, with `target-branch: dev`, commit-message prefixes `build` (gradle, docker, npm) and `ci` (github-actions) with `include: scope`, grouped updates, and a bounded open-PR limit. Dependabot security updates and vulnerability alerts MUST be enabled. Because security-update PRs target `main`, the README MUST document retargeting them to `dev` by hand.
(Previously: ecosystems were gradle, docker and github-actions only.)

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
