## Capability: code-quality-gates

### Purpose
Local quality gates (format, Android Lint, detekt, Compose lint, coverage report, secret scan, workflow lint, commit-message check) where existing debt is baselined and passes, and new violations fail with a non-zero exit code. Secrets are additionally scanned in CI (see `ci-pipeline`).

## ADDED Requirements

### Requirement: Formatting Gate
The build MUST provide a `spotlessCheck` task that exits 0 when every checked Kotlin and Gradle Kotlin DSL source is formatted per the repository `.editorconfig`, and exits non-zero when any checked file is misformatted. The checked sources MUST include `backend/**` Kotlin and Gradle Kotlin DSL files, through the root Spotless configuration. The rules MUST allow `@Composable` functions to use PascalCase names. The tree MUST pass `spotlessCheck` after the one-time format commit, which MUST contain only formatting changes and be listed in `.git-blame-ignore-revs`.

#### Scenario: Formatted tree passes
- GIVEN the repository tree after the format commit
- WHEN `./gradlew spotlessCheck` runs
- THEN it exits 0

#### Scenario: Misformatted Kotlin file fails
- GIVEN a Kotlin source file with a formatting violation (e.g. wrong indentation)
- WHEN `./gradlew spotlessCheck` runs
- THEN it exits non-zero and names the offending file

#### Scenario: Misformatted backend file fails
- GIVEN a Kotlin file under `backend/` with a formatting violation
- WHEN `./gradlew spotlessCheck` runs
- THEN it exits non-zero and names the offending file

#### Scenario: Composable PascalCase name allowed
- GIVEN a `@Composable` function named in PascalCase
- WHEN `./gradlew spotlessCheck` runs
- THEN no function-naming violation is reported for it

#### Scenario: Format commit is blame-ignored
- GIVEN the one-time format commit
- WHEN `.git-blame-ignore-revs` is read
- THEN it contains that commit's SHA

### Requirement: Android Lint Gate With Baseline
The build MUST configure Android Lint for the `app` module with a committed `lint-baseline.xml` and `lint.xml`, and with `abortOnError` enabled. Issues recorded in the baseline MUST NOT fail the check; any issue not in the baseline whose severity is error MUST fail it.

#### Scenario: Baselined tree passes
- GIVEN the committed `lint-baseline.xml` and unchanged sources
- WHEN `./gradlew :app:lintDebug` runs
- THEN it exits 0

#### Scenario: New issue fails
- GIVEN a newly introduced error-severity lint issue absent from the baseline
- WHEN `./gradlew :app:lintDebug` runs
- THEN it exits non-zero and reports the issue

#### Scenario: Abort on error configured
- GIVEN the lint configuration
- WHEN it is inspected
- THEN `abortOnError` is true

### Requirement: Compose Lint Checks (Conditional on Spike)
The change MUST record the outcome of a compatibility spike of slackhq compose-lints (1.5.4, then fallback 1.4.3) with AGP 9.1.1 running `:app:lintDebug`. If a version loads and runs, it MUST be wired through `lintChecks` and a Compose rule violation not in the baseline MUST fail `:app:lintDebug`. If neither version loads, the change MUST record that outcome and no compose-lints requirement is enforced.

#### Scenario: Kept - Compose violation fails lint
- GIVEN compose-lints is kept after the spike
- WHEN a Compose rule violation outside the baseline is introduced
- THEN `./gradlew :app:lintDebug` exits non-zero and reports the compose-lints rule

#### Scenario: Kept - baselined tree passes
- GIVEN compose-lints is kept and existing violations are baselined
- WHEN `./gradlew :app:lintDebug` runs
- THEN it exits 0

#### Scenario: Dropped - recorded, nothing enforced
- GIVEN neither 1.5.4 nor 1.4.3 loads with AGP 9.1.1
- WHEN the change artifacts are read
- THEN the incompatibility is recorded, and no build or lint configuration references compose-lints

### Requirement: Coverage Report
The build MUST provide Kover reports `koverHtmlReportDebug` and `koverXmlReportDebug` that generate from the debug unit tests. The backend build MUST provide Kover HTML and XML reports generated from its tests. Generated code MUST be excluded from the report. The build MUST NOT enforce any coverage threshold. `scripts/kover-summary.sh` MUST summarize both the Android and the JVM Kover XML.

#### Scenario: HTML report produced
- GIVEN the unit tests pass
- WHEN `./gradlew koverHtmlReportDebug` runs
- THEN it exits 0 and an HTML report exists under the build output

#### Scenario: XML report produced
- GIVEN the unit tests pass
- WHEN `./gradlew koverXmlReportDebug` runs
- THEN it exits 0 and an XML report exists under the build output

#### Scenario: Backend report produced
- GIVEN the backend tests pass
- WHEN the backend Kover HTML and XML tasks run in `/backend`
- THEN each exits 0 and its report exists under the backend build output

#### Scenario: Summary reads JVM output
- GIVEN a JVM Kover XML report
- WHEN `scripts/kover-summary.sh` runs on it
- THEN it prints line and branch coverage figures

#### Scenario: Generated code excluded
- GIVEN generated classes (e.g. Room `*_Impl`, `ComposableSingletons`, `BuildConfig`, `R`)
- WHEN the report is produced
- THEN those classes do not appear in it

#### Scenario: No threshold
- GIVEN any measured coverage value
- WHEN Kover reports run
- THEN no task fails because of low coverage

### Requirement: Pre-commit Hook
The `lefthook` `pre-commit` hook MUST run, in parallel: `spotlessCheck` (Kotlin/Gradle sources, including `backend/**`), Android Lint (`./gradlew --quiet :app:lintDebug`, only when staged files match Android sources, resources or Gradle configuration globs), detekt (staged `.kt` files, including `backend/**`, see Detekt Gate With Baseline), gitleaks on staged changes, actionlint (workflow files) and shellcheck with the commit-msg script test (shell files). Each job MUST exit non-zero, with a message explaining how to fix it, when its gate fails. The `commit-msg` hook MUST validate Conventional Commits. These hooks are the only enforcement point of quality static analysis; CI does not repeat them (see `ci-pipeline`). Secrets are the exception: gitleaks also runs in CI, so bypassing the hook does not let a secret through.

#### Scenario: Misformatted staged file blocked
- GIVEN a staged misformatted Kotlin file
- WHEN the pre-commit hook runs
- THEN it exits non-zero

#### Scenario: Misformatted staged backend file blocked
- GIVEN a staged misformatted Kotlin file under `backend/`
- WHEN the pre-commit hook runs
- THEN it exits non-zero

#### Scenario: Staged fake secret blocked
- GIVEN a staged file containing a fake secret matching a gitleaks rule
- WHEN the pre-commit hook runs
- THEN it exits non-zero and the secret value is redacted in the output

#### Scenario: Broken workflow blocked
- GIVEN a staged `.github/workflows/*.yml` file with an actionlint error
- WHEN the pre-commit hook runs
- THEN it exits non-zero and reports the actionlint error

#### Scenario: Clean commit passes
- GIVEN staged files that are formatted, secret-free and valid
- WHEN the pre-commit hook runs
- THEN it exits 0

#### Scenario: Android source change triggers Lint
- GIVEN a staged `.kt`, `.kts` or `.xml` file under `app/`
- WHEN `git commit` runs the pre-commit hook
- THEN `./gradlew --quiet :app:lintDebug` runs and a new non-baselined issue aborts the commit with a fix hint

#### Scenario: Unrelated change skips Lint
- GIVEN only documentation files are staged
- WHEN `git commit` runs the pre-commit hook
- THEN the Android Lint job is skipped

#### Scenario: Staged Kotlin triggers detekt
- GIVEN a staged `.kt` file
- WHEN `git commit` runs the pre-commit hook
- THEN the detekt job runs in parallel with the other jobs

#### Scenario: Hook bypass is an accepted risk for quality only
- GIVEN a commit made with `--no-verify`
- WHEN it reaches the remote
- THEN no CI job re-runs the quality gates (accepted risk), but `secret-scan` still scans it for secrets

### Requirement: Commit-message Hook
The repository MUST provide a lefthook `commit-msg` hook backed by `scripts/check-commit-msg.sh` that validates the first line of the message. A valid first line is `type(scope)!: description`, where `type` is exactly one of `feat fix docs style refactor perf test build ci chore revert`, `(scope)` is optional, `!` is optional, and the description is non-empty. Messages beginning with `Merge`, `Revert`, or `fixup!`/`squash!`/`amend!` (git autosquash prefixes) MUST also be accepted. Other messages MUST be rejected with a non-zero exit and an explanatory message.

#### Scenario: Conventional message accepted
- GIVEN the message `feat(ui): add filter chips`
- WHEN the script runs on it
- THEN it exits 0

#### Scenario: Message without scope accepted
- GIVEN the message `fix: handle empty title`
- WHEN the script runs on it
- THEN it exits 0

#### Scenario: Breaking-change marker accepted
- GIVEN the message `refactor(db)!: drop legacy column`
- WHEN the script runs on it
- THEN it exits 0

#### Scenario: Every allowed type accepted
- GIVEN a message for each of `feat fix docs style refactor perf test build ci chore revert`
- WHEN the script runs on each
- THEN each exits 0

#### Scenario: Merge, revert and fixup accepted
- GIVEN messages `Merge branch 'x' into main`, `Revert "feat: x"`, and `fixup! feat: x`
- WHEN the script runs on each
- THEN each exits 0

#### Scenario: Invalid message rejected
- GIVEN the message `updated stuff`
- WHEN the script runs on it
- THEN it exits non-zero with an explanatory message

#### Scenario: Unknown type rejected
- GIVEN the message `feature: add x`
- WHEN the script runs on it
- THEN it exits non-zero

#### Scenario: Empty description rejected
- GIVEN the message `feat: `
- WHEN the script runs on it
- THEN it exits non-zero

### Requirement: Unit Tests Remain Green
Adding the quality tooling MUST NOT change runtime behavior or break existing tests.

#### Scenario: Unit tests pass
- GIVEN the tooling is applied
- WHEN `./gradlew :app:testDebugUnitTest` runs
- THEN it exits 0

### Requirement: Documented Setup
`README.md` MUST document contributor setup (installing `lefthook`, `gitleaks`, `actionlint`, `shellcheck`, `detekt`; running `lefthook install`) and the commands for format check, lint, detekt, coverage report and tests, and lint-baseline regeneration. `openspec/config.yaml` MUST reflect the available formatter, linter, static analyzer and coverage tooling.

#### Scenario: README lists setup and commands
- GIVEN `README.md`
- WHEN it is read
- THEN it names the five binaries, `lefthook install`, `spotlessCheck`, `:app:lintDebug`, the detekt command, `koverHtmlReportDebug`, and `:app:testDebugUnitTest`

#### Scenario: Config reflects tooling
- GIVEN `openspec/config.yaml`
- WHEN it is read
- THEN formatter, linter, detekt and coverage are no longer marked unavailable

### Requirement: Detekt Gate With Baseline
The repository MUST provide detekt 1.23.8 configuration in `config/detekt/detekt.yml` and a baseline in `config/detekt/baseline.xml`. detekt MUST run through the detekt CLI as a lefthook pre-commit job over staged `.kt` files only, including `.kt` files under `backend/`, MUST NOT enable the ktlint-wrapper rules, and MUST have style rules duplicating ktlint disabled and Compose-friendly naming allowed. Issues in the baseline MUST NOT fail the job; any new issue, including in backend files, MUST fail it with a non-zero exit and a message that prints how to fix it.

#### Scenario: Baselined tree passes
- GIVEN staged `.kt` files whose only detekt issues are in the baseline
- WHEN the pre-commit hook runs
- THEN the detekt job exits 0

#### Scenario: New issue fails with fix hint
- GIVEN a staged `.kt` file with a detekt issue absent from the baseline
- WHEN the pre-commit hook runs
- THEN the detekt job exits non-zero, reports the issue and prints a fix hint

#### Scenario: Backend file analyzed
- GIVEN a staged `.kt` file under `backend/` with a new detekt issue
- WHEN the pre-commit hook runs
- THEN the detekt job exits non-zero and reports the issue

#### Scenario: Non-Kotlin change skips detekt
- GIVEN only non-`.kt` files are staged
- WHEN the pre-commit hook runs
- THEN the detekt job is skipped

#### Scenario: Composable PascalCase name allowed
- GIVEN a staged `@Composable` function named in PascalCase
- WHEN detekt runs
- THEN no naming issue is reported for it

#### Scenario: Not repeated in CI
- GIVEN the merged workflows
- WHEN their steps are inspected
- THEN no workflow runs detekt
