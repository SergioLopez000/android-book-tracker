## Capability: ci-pipeline

### Purpose
GitHub Actions orchestration that runs the unit tests and the security scans (gitleaks secret scan, CodeQL) on every pull request and `dev` push, publishes linked test and coverage reports, exposes a single required check, cleans up per-PR reports, and ensures promotion to `main` requires a green pipeline. Quality static analysis (format, lint, detekt, commit-message) stays local-only, see `code-quality-gates`; security scanning runs in CI because it must not be bypassable. Dependabot keeps dependencies and workflow actions current. Gate semantics are NOT redefined here; CI only invokes them.

(Previously: tests only; "static analysis stays local-only" covered security tools too, and no dependency updates existed. The Purpose is prose, not a requirement: at archive it replaces the living `### Purpose` text.)

(Dropped living scenarios: none. Rewritten living scenarios: "Least privilege" (adds codeql), "Bypassed hook is not caught in CI" (secrets are now caught), "No lint jobs" (scoped to quality tools), "Admin steps documented" (more steps), "No extra jobs" (new jobs exist).)

## ADDED Requirements

### Requirement: CodeQL Analysis Workflow
`.github/workflows/codeql.yml` MUST analyze the `java-kotlin` language with `build-mode: manual`, building the app with Gradle using `--no-daemon --no-build-cache` (the in-process Kotlin compile flag is subject to spike S2). It MUST run on `pull_request` to `dev` with no path filters, on `push` to `dev`, and on a weekly `schedule`. Its permissions MUST be `contents: read` and `security-events: write` only. It MUST NOT use `pull_request_target`.

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
`ci.yml` MUST contain a job `secret-scan` with `contents: read` and `fetch-depth: 0`. It MUST run the gitleaks v8.30.1 binary, verified against the pinned sha256 `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb` before execution, with `--redact`. The scanned range MUST be base..head for a PR, before..sha for a push, and the head commit only when `before` is all zeros. A detected secret MUST fail the job.

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

#### Scenario: Checksum mismatch
- GIVEN the downloaded binary does not match the pinned sha256
- WHEN `secret-scan` runs
- THEN the job fails before executing the binary

### Requirement: Dependabot Configuration
`.github/dependabot.yml` MUST configure version updates for the `gradle` and `github-actions` ecosystems, weekly, with `target-branch: dev`, commit-message prefixes `build` (gradle) and `ci` (github-actions) with `include: scope`, grouped updates, and a bounded open-PR limit. Dependabot security updates and vulnerability alerts MUST be enabled. Because security-update PRs target `main`, the README MUST document retargeting them to `dev` by hand.

#### Scenario: Config valid and scoped
- GIVEN `.github/dependabot.yml`
- WHEN it is parsed
- THEN both ecosystems are weekly, target `dev`, use the stated prefixes, are grouped, and set an open-PR limit

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

## MODIFIED Requirements

### Requirement: Workflow Triggers and Runner
`.github/workflows/ci.yml` MUST run on `pull_request` and on `push` to `dev`, with no path filters, on the `ubuntu-24.04` runner. It MUST NOT use `pull_request_target`. Default workflow permissions MUST be `contents: read`; write permissions MUST be granted only to the jobs that need them (report, cleanup; `security-events: write` only for CodeQL). Third-party actions MUST be pinned to a major version.
(Previously: write scopes were limited to report and cleanup; CodeQL did not exist.)

#### Scenario: PR triggers the pipeline
- GIVEN a pull request is opened or updated
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs regardless of which files changed

#### Scenario: Push to dev triggers the pipeline
- GIVEN a commit is pushed to `dev`
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs

#### Scenario: Least privilege
- GIVEN `ci.yml`, `pr-cleanup.yml` and `codeql.yml`
- WHEN their permissions are inspected
- THEN the workflow default is `contents: read`, write scopes appear only on the report and cleanup jobs, `security-events: write` appears only in `codeql.yml`, and no workflow uses `pull_request_target`

### Requirement: No Static Analysis in CI
CI MUST NOT run quality static analysis. `ci.yml` MUST NOT contain jobs or steps for `spotlessCheck`, `:app:lintDebug`, detekt, actionlint, shellcheck, script harnesses, commit-message validation, or PR-title validation. These quality gates run only locally through the lefthook hooks; skipping them with `--no-verify`, the GitHub web UI, or without `lefthook install` is an explicit, accepted risk. Security scanning is NOT static analysis in this sense and MUST run in CI: gitleaks (`secret-scan`) and CodeQL.
(Previously: gitleaks was also banned from CI and the bypass risk covered secrets.)

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

### Requirement: Single Required Check (ci-gate)
CI MUST define a job named exactly `ci-gate` that runs with `if: always()`, depends only on `unit-tests` and `secret-scan` (the gating jobs; `report` is not gating), and succeeds only if both succeeded. A skipped or failed `unit-tests` or `secret-scan` MUST make `ci-gate` fail. A failure of the `report` job alone MUST NOT fail `ci-gate`. The job name MUST NOT change, as it is the sole required status check for the `dev` ruleset.
(Previously: depended only on `unit-tests`.)

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
- GIVEN `unit-tests` and `secret-scan` passed but `report` failed (e.g. Pages push error)
- WHEN `ci-gate` evaluates
- THEN it succeeds

### Requirement: Documentation of CI and Admin Steps
`README.md` (in Spanish) MUST document the pipeline (tests, reports, secret scan and CodeQL in CI; quality static analysis local-only), a "what runs where" table, how to reach the reports, the artifact contract, an accepted-risk note stating that quality gates are bypassable while secrets are caught in CI, the SonarQube for IDE recommendation, and the manual admin steps in order: enabling Pages from `gh-pages` after the branch exists, adding `ci-gate` (Actions integration) as a required status check to the `dev` ruleset after the first green run, adding the "Require code scanning results" rule after the first green `dev` CodeQL analysis, enabling Dependabot alerts and security updates, and configuring the squash merge message to include the PR description.
(Previously: tests and reports only, `--no-verify` risk for all static analysis, three admin steps.)

#### Scenario: Admin steps documented
- GIVEN the README
- WHEN a maintainer reads the CI section
- THEN it lists the Pages, required-check, code-scanning rule, Dependabot, and merge-message steps in order

#### Scenario: What-runs-where table and risk note
- GIVEN the README
- WHEN it is read
- THEN it contains the table of which gate runs locally versus in CI, the updated accepted-risk note, and the SonarQube for IDE recommendation

### Requirement: Out of Scope Boundaries
This change MUST NOT add contract, PIT or E2E jobs, SonarCloud or any paid tooling, quality gates (spotless, lint, detekt, commit-msg) in CI, `workflow_run` based comments for fork PRs, or a Robolectric version upgrade, and MUST NOT modify application code or gate semantics in `code-quality-gates` beyond adding detekt.
(Previously: CodeQL and Dependabot were also out of scope.)

#### Scenario: No extra jobs
- GIVEN the merged workflows
- WHEN their jobs are listed
- THEN `ci.yml` has only `unit-tests`, `secret-scan`, `report`, `ci-gate`, and the other workflows are only CodeQL analysis, cleanup and promote
