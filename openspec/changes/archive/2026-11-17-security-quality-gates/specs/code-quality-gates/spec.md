## Capability: code-quality-gates

### Purpose
Local quality gates (format, Android Lint, detekt, Compose lint, coverage report, secret scan, workflow lint, commit-message check) where existing debt is baselined and passes, and new violations fail with a non-zero exit code. Secrets are additionally scanned in CI (see `ci-pipeline`).

(Previously: no detekt. Prose, replaces the living `### Purpose` at archive.)

(Dropped living scenarios: none. Rewritten living scenarios: "Hook bypass is an accepted risk" (the bypass is accepted only for quality; secrets are caught in CI), "README lists setup and commands" (five binaries plus detekt).)

## ADDED Requirements

### Requirement: Detekt Gate With Baseline
The repository MUST provide detekt 1.23.8 configuration in `config/detekt/detekt.yml` and a baseline in `config/detekt/baseline.xml`. detekt MUST run through the detekt CLI as a lefthook pre-commit job over staged `.kt` files only, MUST NOT enable the ktlint-wrapper rules, and MUST have style rules duplicating ktlint disabled and Compose-friendly naming allowed. Issues in the baseline MUST NOT fail the job; any new issue MUST fail it with a non-zero exit and a message that prints how to fix it.

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

## MODIFIED Requirements

### Requirement: Pre-commit Hook
The `lefthook` `pre-commit` hook MUST run, in parallel: `spotlessCheck` (Kotlin/Gradle sources), Android Lint (`./gradlew --quiet :app:lintDebug`, only when staged files match Android sources, resources or Gradle configuration globs), detekt (staged `.kt` files, see Detekt Gate With Baseline), gitleaks on staged changes, actionlint (workflow files) and shellcheck with the commit-msg script test (shell files). Each job MUST exit non-zero, with a message explaining how to fix it, when its gate fails. The `commit-msg` hook MUST validate Conventional Commits. These hooks are the only enforcement point of quality static analysis; CI does not repeat them (see `ci-pipeline`). Secrets are the exception: gitleaks also runs in CI, so bypassing the hook does not let a secret through.
(Previously: no detekt job; hooks were the only enforcement of all static analysis, secrets included.)

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

### Requirement: Documented Setup
`README.md` MUST document contributor setup (installing `lefthook`, `gitleaks`, `actionlint`, `shellcheck`, `detekt`; running `lefthook install`) and the commands for format check, lint, detekt, coverage report and tests, and lint-baseline regeneration. `openspec/config.yaml` MUST reflect the available formatter, linter, static analyzer and coverage tooling.
(Previously: four binaries and no detekt command.)

#### Scenario: README lists setup and commands
- GIVEN `README.md`
- WHEN it is read
- THEN it names the five binaries, `lefthook install`, `spotlessCheck`, `:app:lintDebug`, the detekt command, `koverHtmlReportDebug`, and `:app:testDebugUnitTest`

#### Scenario: Config reflects tooling
- GIVEN `openspec/config.yaml`
- WHEN it is read
- THEN formatter, linter, detekt and coverage are no longer marked unavailable
