# Delta for Code Quality Gates

## MODIFIED Requirements

### Requirement: Formatting Gate

The build MUST provide a `spotlessCheck` task that exits 0 when every checked Kotlin and Gradle Kotlin DSL source is formatted per the repository `.editorconfig`, and exits non-zero when any checked file is misformatted. The rules MUST allow `@Composable` functions to use PascalCase names. The tree MUST pass `spotlessCheck` after the one-time format commit, which MUST contain only formatting changes and be listed in `.git-blame-ignore-revs`.
(Previously: the checked sources also included `backend/**`, which is deleted.)

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

### Requirement: Coverage Report

The build MUST provide Kover reports `koverHtmlReportDebug` and `koverXmlReportDebug` that generate from the debug unit tests. Generated code MUST be excluded from the report, and the exclusion configuration MUST NOT reference Room (no Room-specific patterns such as `*_Impl` DAOs). The build MUST NOT enforce any coverage threshold. `scripts/kover-summary.sh` MUST summarize the Kover XML.
(Previously: the backend build also had to provide Kover reports, `kover-summary.sh` had to summarize both Android and JVM XML, and the excluded generated classes included Room `*_Impl`.)

#### Scenario: HTML report produced
- GIVEN the unit tests pass
- WHEN `./gradlew koverHtmlReportDebug` runs
- THEN it exits 0 and an HTML report exists under the build output

#### Scenario: XML report produced
- GIVEN the unit tests pass
- WHEN `./gradlew koverXmlReportDebug` runs
- THEN it exits 0 and an XML report exists under the build output

#### Scenario: Summary reads Kover output
- GIVEN a Kover XML report
- WHEN `scripts/kover-summary.sh` runs on it
- THEN it prints line and branch coverage figures

#### Scenario: Generated code excluded
- GIVEN generated classes (e.g. `ComposableSingletons`, `BuildConfig`, `R`)
- WHEN the report is produced
- THEN those classes do not appear in it

#### Scenario: No Room exclusions
- GIVEN the Kover configuration
- WHEN it is searched for `Room`, `_Impl` or `androidx.room`
- THEN nothing is found

#### Scenario: No threshold
- GIVEN any measured coverage value
- WHEN Kover reports run
- THEN no task fails because of low coverage

### Requirement: Pre-commit Hook

The `lefthook` `pre-commit` hook MUST run, in parallel: `spotlessCheck` (Kotlin/Gradle sources), Android Lint (`./gradlew --quiet :app:lintDebug`, only when staged files match Android sources, resources or Gradle configuration globs), detekt (staged `.kt` files, see Detekt Gate With Baseline), gitleaks on staged changes, actionlint (workflow files), shellcheck with the commit-msg script test (shell files), and a TypeScript type check (`tsc --noEmit` in `/e2e`, only when staged files match `e2e/**/*.ts`). Each job MUST exit non-zero, with a message explaining how to fix it, when its gate fails. The `commit-msg` hook MUST validate Conventional Commits. These hooks are the only enforcement point of quality static analysis; CI does not repeat them (see `ci-pipeline`), including the e2e type check. Secrets are the exception: gitleaks also runs in CI, so bypassing the hook does not let a secret through.
(Previously: spotless and detekt also covered `backend/**`.)

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

#### Scenario: Staged e2e TypeScript with a type error blocked
- GIVEN a staged `e2e/**/*.ts` file with a type error
- WHEN the pre-commit hook runs
- THEN the type-check job exits non-zero, reports the error and prints a fix hint

#### Scenario: Staged e2e TypeScript clean passes
- GIVEN a staged `e2e/**/*.ts` file that type-checks
- WHEN the pre-commit hook runs
- THEN the type-check job exits 0

#### Scenario: Non-e2e change skips type check
- GIVEN no staged file matches `e2e/**/*.ts`
- WHEN the pre-commit hook runs
- THEN the type-check job is skipped

#### Scenario: Type check not repeated in CI
- GIVEN the merged workflows
- WHEN their steps are inspected
- THEN no CI job runs `tsc` as a static-analysis gate

### Requirement: Documented Setup

`README.md` MUST document contributor setup (installing `lefthook`, `gitleaks`, `actionlint`, `shellcheck`, `detekt`; running `lefthook install`) and the commands for format check, lint, detekt, coverage report and tests, and lint-baseline regeneration. It MUST also document Firebase setup: the committed placeholder `google-services.json`, how to supply a real one locally, and how to run the Firestore rules tests with the emulator. `openspec/config.yaml` MUST reflect the available formatter, linter, static analyzer and coverage tooling, and the Firebase stack instead of Room. Neither `README.md`, `docs/` nor `openspec/config.yaml` MUST describe the old backend as present or frozen.
(Previously: no Firebase documentation; config listed Room and KSP; README, `docs/secrets.md` and config documented the frozen backend.)

#### Scenario: README lists setup and commands
- GIVEN `README.md`
- WHEN it is read
- THEN it names the five binaries, `lefthook install`, `spotlessCheck`, `:app:lintDebug`, the detekt command, `koverHtmlReportDebug`, and `:app:testDebugUnitTest`

#### Scenario: README documents Firebase
- GIVEN `README.md`
- WHEN it is read
- THEN it explains the placeholder `google-services.json`, how to use a real one, and the rules-test command

#### Scenario: Config reflects tooling
- GIVEN `openspec/config.yaml`
- WHEN it is read
- THEN formatter, linter, detekt and coverage are no longer marked unavailable

#### Scenario: Config drops Room
- GIVEN `openspec/config.yaml`
- WHEN it is searched for `Room` and `KSP`
- THEN neither appears as part of the current stack and Firebase Auth and Firestore are listed

#### Scenario: No backend description
- GIVEN `README.md`, `docs/` and `openspec/config.yaml`
- WHEN `grep -Eil 'backend|frozen|congelad'` runs on them
- THEN no file describes a backend as present, frozen or part of the stack

### Requirement: Detekt Gate With Baseline

The repository MUST provide detekt 1.23.8 configuration in `config/detekt/detekt.yml` and a baseline in `config/detekt/baseline.xml`. detekt MUST run through the detekt CLI as a lefthook pre-commit job over staged `.kt` files only, MUST NOT enable the ktlint-wrapper rules, and MUST have style rules duplicating ktlint disabled and Compose-friendly naming allowed. Issues in the baseline MUST NOT fail the job; any new issue MUST fail it with a non-zero exit and a message that prints how to fix it.
(Previously: also covered `.kt` files under `backend/`, with a "Backend file analyzed" scenario.)

#### Scenario: Baselined tree passes
- GIVEN staged `.kt` files whose only detekt issues are in the baseline
- WHEN the pre-commit hook runs
- THEN the detekt job exits 0

#### Scenario: New issue fails with fix hint
- GIVEN a staged `.kt` file with a detekt issue absent from the baseline
- WHEN the pre-commit hook runs
- THEN the detekt job exits non-zero, reports the issue and prints a fix hint

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
