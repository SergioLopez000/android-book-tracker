# Delta for ci-pipeline

## ADDED Requirements

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

A workflow MUST build the backend jar on the runner, build the image from `backend/Dockerfile`, and push it to GHCR only on `push` to `dev`, tagged `sha-<commit>` and `dev`. It MUST NOT push on pull requests or from forks. `packages: write` MUST be granted only to the publishing job, which MUST depend on a green backend gate. The package MUST remain private; visibility is decided in #8.

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

## MODIFIED Requirements

### Requirement: Workflow Triggers and Runner
`.github/workflows/ci.yml` MUST run on `pull_request` and on `push` to `dev`, with no path filters, on the `ubuntu-24.04` runner. It MUST NOT use `pull_request_target`. Default workflow permissions MUST be `contents: read`; write permissions MUST be granted only to the jobs that need them (report, cleanup; `security-events: write` only for CodeQL; `packages: write` only for the GHCR publish job and the promote retag job). `board-sync.yml` MUST keep `contents: read` and write the project only through the `PROJECT_TOKEN` secret, never through `GITHUB_TOKEN` write scopes. Third-party actions MUST be pinned to a major version.
(Previously: `packages: write` was not an allowed write scope)

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

### Requirement: Unit Test Job
The `unit-tests` job MUST have no `needs`, MUST run `:app:testDebugUnitTest` and generate Kover XML and HTML coverage, and MUST upload its results as artifacts per the artifact contract. It MUST NOT build or test `/backend`; that is owned by `backend-tests` and `contract-tests`.
(Previously: the job had no stated boundary with the backend)

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
(Previously: only suite `unit` existed and contract/PIT suites were future)

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
(Previously: `report` ran after `unit-tests` only and read Android Kover names)

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

### Requirement: Single Required Check (ci-gate)
CI MUST define a job named exactly `ci-gate` that runs with `if: always()`, depends only on `unit-tests`, `backend-tests`, `contract-tests` and `secret-scan` (the gating jobs; `report` is not gating), and succeeds only if all four succeeded. A skipped or failed gating job MUST make `ci-gate` fail. A failure of the `report` job alone MUST NOT fail `ci-gate`. The job name MUST NOT change, as it is the sole required status check for the `dev` ruleset.
(Previously: needs were `unit-tests` and `secret-scan` only)

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

### Requirement: Out of Scope Boundaries
This change MUST NOT add E2E jobs, SonarCloud or any paid tooling, quality gates (spotless, lint, detekt, commit-msg) in CI, `workflow_run` based comments for fork PRs, or a Robolectric version upgrade, and MUST NOT modify application code or gate semantics in `code-quality-gates` beyond adding detekt and extending its existing gates to `/backend`. Backend `backend-tests`, `contract-tests` (including PIT) and the GHCR publish job are permitted; no other jobs are.
(Previously: contract and PIT jobs were forbidden)

#### Scenario: Only expected jobs
- GIVEN the merged workflows
- WHEN their jobs are listed
- THEN `ci.yml` has only `unit-tests`, `backend-tests`, `contract-tests`, `secret-scan`, `report`, `ci-gate`, and the other workflows are only CodeQL analysis, cleanup, promote, board sync and the GHCR publish

#### Scenario: No forbidden tooling
- GIVEN the merged workflows
- WHEN their steps are inspected
- THEN none run SonarCloud, E2E suites, or quality static analysis

### Requirement: CodeQL Analysis Workflow
`.github/workflows/codeql.yml` MUST analyze the `java-kotlin` language with `build-mode: manual`, building the app and the backend with Gradle using `--no-daemon --no-build-cache` (the in-process Kotlin compile flag is subject to spike S2). The backend build MUST run on JDK 21 and Kotlin files under `/backend` MUST be extracted. It MUST run on `pull_request` to `dev` with no path filters, on `push` to `dev`, and on a weekly `schedule`. Its permissions MUST be `contents: read` and `security-events: write` only. It MUST NOT use `pull_request_target`.
(Previously: only the app was built and analyzed)

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

### Requirement: Dependabot Configuration
`.github/dependabot.yml` MUST configure version updates for the `gradle` ecosystem (root and `/backend` directories), the `docker` ecosystem (`/backend`) and `github-actions`, weekly, with `target-branch: dev`, commit-message prefixes `build` (gradle, docker) and `ci` (github-actions) with `include: scope`, grouped updates, and a bounded open-PR limit. Dependabot security updates and vulnerability alerts MUST be enabled. Because security-update PRs target `main`, the README MUST document retargeting them to `dev` by hand.
(Previously: only the root `gradle` and `github-actions` ecosystems were covered)

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
