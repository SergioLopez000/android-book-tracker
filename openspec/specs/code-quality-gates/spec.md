## Capability: code-quality-gates

### Purpose
Local quality gates (format, Android Lint, Compose lint, coverage report, secret scan, workflow lint, commit-message check) where existing debt is baselined and passes, and new violations fail with a non-zero exit code.

## ADDED Requirements

### Requirement: Formatting Gate
The build MUST provide a `spotlessCheck` task that exits 0 when every checked Kotlin and Gradle Kotlin DSL source is formatted per the repository `.editorconfig`, and exits non-zero when any checked file is misformatted. The rules MUST allow `@Composable` functions to use PascalCase names. The tree MUST pass `spotlessCheck` after the one-time format commit, which MUST contain only formatting changes and be listed in `.git-blame-ignore-revs`.

#### Scenario: Formatted tree passes
- GIVEN the repository tree after the format commit
- WHEN `./gradlew spotlessCheck` runs
- THEN it exits 0

#### Scenario: Misformatted Kotlin file fails
- GIVEN a Kotlin source file with a formatting violation (e.g. wrong indentation)
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
The build MUST provide Kover reports `koverHtmlReportDebug` and `koverXmlReportDebug` that generate from the debug unit tests. Generated code MUST be excluded from the report. The build MUST NOT enforce any coverage threshold.

#### Scenario: HTML report produced
- GIVEN the unit tests pass
- WHEN `./gradlew koverHtmlReportDebug` runs
- THEN it exits 0 and an HTML report exists under the build output

#### Scenario: XML report produced
- GIVEN the unit tests pass
- WHEN `./gradlew koverXmlReportDebug` runs
- THEN it exits 0 and an XML report exists under the build output

#### Scenario: Generated code excluded
- GIVEN generated classes (e.g. Room `*_Impl`, `ComposableSingletons`, `BuildConfig`, `R`)
- WHEN the report is produced
- THEN those classes do not appear in it

#### Scenario: No threshold
- GIVEN any measured coverage value
- WHEN Kover reports run
- THEN no task fails because of low coverage

### Requirement: Pre-commit Hook
The repository MUST provide a lefthook `pre-commit` configuration that runs (a) Spotless on staged Kotlin/Gradle files, (b) gitleaks over staged changes with redacted output, and (c) actionlint and shellcheck on workflow and shell files. Any failing check MUST make the hook exit non-zero and abort the commit. Android Lint MUST NOT run in pre-commit.

#### Scenario: Misformatted staged file blocked
- GIVEN a staged misformatted Kotlin file
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
`README.md` MUST document contributor setup (installing `lefthook`, `gitleaks`, `actionlint`, `shellcheck`; running `lefthook install`) and the commands for format check, lint, coverage report and tests, and lint-baseline regeneration. `openspec/config.yaml` MUST reflect the available formatter, linter and coverage tooling.

#### Scenario: README lists setup and commands
- GIVEN `README.md`
- WHEN it is read
- THEN it names the four binaries, `lefthook install`, `spotlessCheck`, `:app:lintDebug`, `koverHtmlReportDebug`, and `:app:testDebugUnitTest`

#### Scenario: Config reflects tooling
- GIVEN `openspec/config.yaml`
- WHEN it is read
- THEN formatter, linter and coverage are no longer marked unavailable
