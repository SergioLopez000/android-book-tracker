# Delta for CI Pipeline

## ADDED Requirements

### Requirement: Rules Tests Job

`ci.yml` MUST contain a job `rules-tests` with no `needs`, running on `ubuntu-24.04`, that starts the Firebase Emulator Suite and runs the Firestore rules test suite defined in `firestore-security-rules`. It MUST run on every pull request and `dev` push with no path filters, MUST have `permissions: contents: read` only, and MUST NOT reference any `secrets.*` or production Firebase credentials. Its failure MUST fail `ci-gate`.

#### Scenario: Runs on every trigger
- GIVEN a documentation-only PR, or a `dev` push
- WHEN the pipeline starts
- THEN `rules-tests` runs

#### Scenario: Read-only and secret-free
- GIVEN the `rules-tests` job definition
- WHEN its permissions and environment are inspected
- THEN only `contents: read` is granted and no `secrets.*` reference appears

#### Scenario: Weakened rule blocks the gate
- GIVEN a change that relaxes a rule so a rules test fails
- WHEN the pipeline runs
- THEN `rules-tests` fails and `ci-gate` fails

### Requirement: Placeholder Firebase Configuration in CI

The repository MUST commit a placeholder `google-services.json` containing no real project credentials, and every CI build MUST succeed using only it, with no secret injected.

#### Scenario: CI builds without secrets
- GIVEN a fresh checkout in CI with no secrets
- WHEN `unit-tests` and `e2e-tests` build the app
- THEN the builds succeed using the committed placeholder

#### Scenario: Placeholder is inert
- GIVEN the committed `google-services.json`
- WHEN it is scanned by `secret-scan`
- THEN it passes and contains no real API key or project secret

### Requirement: Backend Removed From the Repository

The `backend/` directory MUST NOT exist, and no automation, hook, script, configuration, document or issue template MUST reference the old backend. This covers: workflows under `.github/workflows/`, `.github/dependabot.yml`, `lefthook.yml`, the freeze guard scripts, the image smoke and secrets-contract tests that only served the backend, the root build files (settings, Spotless and detekt configuration), `README.md`, `docs/` and the issue templates. The Android app build, the e2e suite and `secret-scan` MUST remain unaffected.

#### Scenario: Backend tree gone
- GIVEN the merged tree
- WHEN `test -e backend` runs
- THEN it reports that `backend/` does not exist

#### Scenario: No workflow or dependabot reference
- GIVEN the merged tree
- WHEN `grep -rEn 'backend|contract-tests|publish-image|check-image|retag-image|ghcr\.io|docker/login-action|packages: *write' .github/workflows/ .github/dependabot.yml` runs
- THEN it matches nothing

#### Scenario: No backend-only scripts or hooks
- GIVEN the merged tree
- WHEN the repository is searched for backend freeze guard scripts, `scripts/test-image-*.sh` and lefthook jobs naming `backend`
- THEN none exist

#### Scenario: No dangling references
- GIVEN the merged tree
- WHEN `grep -rIn 'backend/' . --exclude-dir=.git --exclude-dir=openspec --exclude-dir=node_modules` runs
- THEN it matches nothing in build files, scripts, docs or templates

#### Scenario: Workflows stay valid
- GIVEN the merged workflows
- WHEN `actionlint` runs
- THEN it exits 0, so no `needs` points at a removed job

## MODIFIED Requirements

### Requirement: Unit Test Job

The `unit-tests` job MUST have no `needs`, MUST run `:app:testDebugUnitTest` and generate Kover XML and HTML coverage, and MUST upload its results as artifacts per the artifact contract.
(Previously: also stated it MUST NOT build or test `/backend`, referencing "Backend Frozen in CI".)

#### Scenario: Tests run on every trigger
- GIVEN a PR or a `dev` push
- WHEN the pipeline starts
- THEN `unit-tests` runs `:app:testDebugUnitTest`, produces Kover XML and HTML, and uploads artifacts

#### Scenario: Test failure still uploads results
- GIVEN a unit test fails
- WHEN `unit-tests` finishes
- THEN the job fails and its test result artifacts are still uploaded

### Requirement: Artifact Contract

Each test suite MUST publish artifacts named `allure-results-<suite>` and `coverage-<suite>` (Kover reports). `allure-results-<suite>` holds JUnit XML results, except for the `e2e` suite, which holds Allure JSON results. Artifact names MUST be unique per run (no overwrite). The current suites are `unit` and `e2e` (`allure-results-e2e` only, retained for 7 days). Downstream jobs MUST discover results by these name patterns so future suites attach without changing the report job.
(Previously: the requirement text is unchanged; the "No backend artifacts" scenario is dropped because the backend no longer exists.)

#### Scenario: Unit suite artifacts
- GIVEN `unit-tests` completes
- WHEN artifacts are listed
- THEN `allure-results-unit` and `coverage-unit` exist

#### Scenario: E2E suite artifact
- GIVEN `e2e-tests` completes
- WHEN artifacts are listed
- THEN `allure-results-e2e` exists with Allure JSON results and a 7-day retention, and no `coverage-e2e` exists

#### Scenario: Unknown future suite is merged
- GIVEN an additional artifact named `allure-results-<other>` exists
- WHEN the report job runs
- THEN its results are merged into the same report without workflow changes

### Requirement: Report Generation and Publishing

The `report` job MUST run with `if: !cancelled()` after `unit-tests` and `e2e-tests` (it still runs when tests fail, but not on a cancelled run), merge all `allure-results-*` artifacts, generate an Allure 2.46.2 report from the JUnit XML and the e2e Allure JSON results, and publish the Allure report and the Kover HTML report to the `gh-pages` branch under `pr-<N>/` for pull requests and `dev/` for pushes to `dev`. The coverage summary MUST surface the app (Android) coverage. Publishing MUST preserve other folders (`keep_files`) and runs MUST be serialized with `concurrency` group `gh-pages`. Publishing MUST run only for same-repository PRs and `dev` pushes. If the Allure 2 spike shows JUnit XML or WebdriverIO JSON is not rendered acceptably, Allure 3 MAY be substituted without altering any other requirement.
(Previously: also stated the summary MUST NOT aggregate backend coverage, PIT scores or link a backend Kover report.)

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
- THEN they are exactly `unit-tests` and `e2e-tests`

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

CI MUST define a job named exactly `ci-gate` that runs with `if: always()`, depends only on `unit-tests`, `secret-scan` and `rules-tests` (the gating jobs; `report` is not gating), and succeeds only if all three succeeded. A skipped or failed gating job MUST make `ci-gate` fail. A failure of the `report` job alone MUST NOT fail `ci-gate`. The job name MUST NOT change, as it is the sole required status check for the `dev` ruleset.
(Previously: depended only on `unit-tests` and `secret-scan`.)

#### Scenario: Everything green
- GIVEN `unit-tests`, `secret-scan` and `rules-tests` succeeded
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

#### Scenario: Rules tests failure
- GIVEN `unit-tests` and `secret-scan` succeeded and `rules-tests` failed or was skipped
- WHEN `ci-gate` evaluates
- THEN it fails

#### Scenario: Report outage
- GIVEN all gating jobs passed but `report` failed (e.g. Pages push error)
- WHEN `ci-gate` evaluates
- THEN it succeeds

#### Scenario: Needs are exactly the gating jobs
- GIVEN `ci.yml`
- WHEN the `needs` of `ci-gate` are read
- THEN they are exactly `unit-tests`, `secret-scan` and `rules-tests`, and `actionlint` exits 0

### Requirement: Out of Scope Boundaries

The pipeline MUST NOT include SonarCloud or any paid tooling, quality gates (spotless, lint, detekt, commit-msg, tsc) in CI, `workflow_run` based comments for fork PRs, or a Robolectric version upgrade, and this capability MUST NOT redefine gate semantics owned by `code-quality-gates`. It MUST NOT add Trivy or gitleaks beyond the existing `secret-scan` job. Permitted jobs in `ci.yml` are `unit-tests`, `e2e-tests`, `secret-scan`, `rules-tests`, `report` and `ci-gate`; no other jobs are. The `e2e-tests` job MUST NOT be a dependency of `ci-gate` until a later change makes it gating.
(Previously: `rules-tests` was not a permitted job; also stated the pipeline MUST NOT build, test, scan, publish or promote the frozen backend.)

#### Scenario: Only expected jobs
- GIVEN the merged workflows
- WHEN their jobs are listed
- THEN `ci.yml` has only `unit-tests`, `e2e-tests`, `secret-scan`, `rules-tests`, `report` and `ci-gate`, and the other workflows are only CodeQL analysis, cleanup, promote, board sync and the `PROJECT_TOKEN` expiry reminder

#### Scenario: No forbidden tooling
- GIVEN the merged workflows
- WHEN their steps are inspected
- THEN none run SonarCloud, Trivy, an additional gitleaks scan or quality static analysis; the only test suites beyond unit are the e2e suite and the rules tests

### Requirement: CodeQL Analysis Workflow

`.github/workflows/codeql.yml` MUST analyze the `java-kotlin` language with `build-mode: manual`, building the app with Gradle using `--no-daemon --no-build-cache` (the in-process Kotlin compile flag is subject to spike S2). It MUST run on `pull_request` to `dev` with no path filters, on `push` to `dev`, and on a weekly `schedule`. Its permissions MUST be `contents: read` and `security-events: write` only. It MUST NOT use `pull_request_target`.
(Previously: also stated it MUST NOT install JDK 21 or build `/backend`.)

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

### Requirement: Dependabot Configuration

`.github/dependabot.yml` MUST configure version updates for the `gradle` ecosystem (root directory only), the `npm` ecosystem (`/e2e`) and `github-actions`, weekly, with `target-branch: dev`, commit-message prefixes `build` (gradle, npm) and `ci` (github-actions) with `include: scope`, grouped updates, and a bounded open-PR limit. Dependabot security updates and vulnerability alerts MUST be enabled. Because security-update PRs target `main`, the README MUST document retargeting them to `dev` by hand.
(Previously: also stated it MUST NOT contain a `/backend` entry or a `docker` ecosystem entry; that is now covered by "Backend Removed From the Repository".)

#### Scenario: Config valid and scoped
- GIVEN `.github/dependabot.yml`
- WHEN it is parsed
- THEN every ecosystem entry is weekly, targets `dev`, uses the stated prefixes, is grouped, and sets an open-PR limit

#### Scenario: Root Gradle covered
- GIVEN `.github/dependabot.yml`
- WHEN its `gradle` entries are read
- THEN exactly one entry exists and its directory is `/`

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

`ci.yml` MUST contain an `e2e-tests` job with no `needs`, running on `ubuntu-24.04` against an Android emulator, that installs `/e2e` dependencies with `npm ci` using the Node version from `.nvmrc`, builds the APK of the `e2e` build type (`assembleE2e`) and runs the Appium happy-path suite. It MUST run on every pull request and every `dev` push with no path or label filters. It MUST have `permissions: contents: read` only and MUST NOT use any secret, so it is safe for fork PRs. It MUST upload `allure-results-e2e` with `retention-days: 7`, even when tests fail. It is non-gating: `ci-gate` MUST NOT depend on it, and its failure MUST NOT fail `ci-gate`.
(Previously: built the debug APK.)

#### Scenario: Runs on every trigger
- GIVEN a PR touching only documentation, or a `dev` push
- WHEN the pipeline starts
- THEN `e2e-tests` runs

#### Scenario: Builds the e2e build type
- GIVEN the `e2e-tests` job definition
- WHEN its build step is read
- THEN it runs `assembleE2e` and installs that APK, not the debug or release APK

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
- GIVEN `unit-tests`, `secret-scan` and `rules-tests` succeeded and `e2e-tests` failed
- WHEN `ci-gate` evaluates
- THEN it succeeds

### Requirement: Static Contract Checks Self-Tested and Run Locally

The workflow-permissions and secrets allow-list checks MUST live in a script under `scripts/` with a self-test harness, in the style of the existing `scripts/test-*.sh` harnesses, covering one passing and one failing fixture per rule. The script MUST run through the lefthook hooks and MUST NOT be added to `ci.yml` as a job or step, consistent with "No Static Analysis in CI".
(Previously: also stated the script MUST NOT check or carry fixtures for `backend/Dockerfile` and that the `secrets-contract` glob MUST NOT include it; the Dockerfile no longer exists.)

#### Scenario: Self-test covers failures
- GIVEN the self-test harness `scripts/test-secrets-contract.sh`
- WHEN it runs
- THEN it exits 0, and each rule is shown to fail on a violating fixture and pass on a clean one

#### Scenario: Runs in local hooks
- GIVEN a commit touching a workflow, `docs/secrets.md` or a secrets-contract script
- WHEN the lefthook hooks run
- THEN the static contract checks execute

#### Scenario: Not a CI job
- GIVEN the merged `ci.yml`
- WHEN its jobs and steps are listed
- THEN none invokes the static contract check script

## REMOVED Requirements

### Requirement: Backend Frozen in CI
(Reason: the `backend/` tree is deleted by this change, replaced by Firebase, firebase-cloud-sync. The freeze guarantees no longer apply; "Backend Removed From the Repository" takes their place.)

## Open Questions

- `docs/secrets.md` rows that describe backend-era secrets (for example the planned rows created by #8 and #29 and the `GOOGLE_BOOKS_SERVER_API_KEY` supersession) may no longer apply once the backend is deleted. The "Secret Inventory Document" requirement is left unchanged here; design or tasks must decide whether those rows are removed.
