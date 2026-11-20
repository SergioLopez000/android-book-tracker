# Delta for code-quality-gates

## MODIFIED Requirements

### Requirement: Formatting Gate
The build MUST provide a `spotlessCheck` task that exits 0 when every checked Kotlin and Gradle Kotlin DSL source is formatted per the repository `.editorconfig`, and exits non-zero when any checked file is misformatted. The checked sources MUST include `backend/**` Kotlin and Gradle Kotlin DSL files, through the root Spotless configuration. The rules MUST allow `@Composable` functions to use PascalCase names. The tree MUST pass `spotlessCheck` after the one-time format commit, which MUST contain only formatting changes and be listed in `.git-blame-ignore-revs`.
(Previously: only the app and root sources were checked)

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

### Requirement: Coverage Report
The build MUST provide Kover reports `koverHtmlReportDebug` and `koverXmlReportDebug` that generate from the debug unit tests. The backend build MUST provide Kover HTML and XML reports generated from its tests. Generated code MUST be excluded from the report. The build MUST NOT enforce any coverage threshold. `scripts/kover-summary.sh` MUST summarize both the Android and the JVM Kover XML.
(Previously: only Android Kover reports existed and the summary script read Android names only)

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
(Previously: spotless and detekt jobs did not state coverage of `backend/**`)

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

### Requirement: Detekt Gate With Baseline
The repository MUST provide detekt 1.23.8 configuration in `config/detekt/detekt.yml` and a baseline in `config/detekt/baseline.xml`. detekt MUST run through the detekt CLI as a lefthook pre-commit job over staged `.kt` files only, including `.kt` files under `backend/`, MUST NOT enable the ktlint-wrapper rules, and MUST have style rules duplicating ktlint disabled and Compose-friendly naming allowed. Issues in the baseline MUST NOT fail the job; any new issue, including in backend files, MUST fail it with a non-zero exit and a message that prints how to fix it.
(Previously: backend files were not stated as covered)

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
