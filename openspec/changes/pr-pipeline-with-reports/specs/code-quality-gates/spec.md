## Capability: code-quality-gates

### Purpose
Delta for change `pr-pipeline-with-reports`: static analysis runs only locally, so the pre-commit hook now also enforces Android Lint.

## MODIFIED Requirements

### Requirement: Pre-commit Hook Gates
The `lefthook` `pre-commit` hook MUST run, in parallel: `spotlessCheck` (Kotlin/Gradle sources), Android Lint (`./gradlew --quiet :app:lintDebug`, only when staged files match Android sources, resources or Gradle configuration globs), gitleaks on staged changes, actionlint (workflow files) and shellcheck with the commit-msg script test (shell files). Each job MUST exit non-zero, with a message explaining how to fix it, when its gate fails. The `commit-msg` hook MUST validate Conventional Commits. These hooks are the only enforcement point of static analysis; CI does not repeat them (see `ci-pipeline`).
(Previously: the pre-commit hook ran spotlessCheck, gitleaks, actionlint and shellcheck; Android Lint ran only on demand and in CI.)

#### Scenario: Android source change triggers Lint
- GIVEN a staged `.kt`, `.kts` or `.xml` file under `app/`
- WHEN `git commit` runs the pre-commit hook
- THEN `./gradlew --quiet :app:lintDebug` runs and a new non-baselined issue aborts the commit with a fix hint

#### Scenario: Unrelated change skips Lint
- GIVEN only documentation files are staged
- WHEN `git commit` runs the pre-commit hook
- THEN the Android Lint job is skipped

#### Scenario: Hook bypass is an accepted risk
- GIVEN a commit made with `--no-verify`
- WHEN it reaches the remote
- THEN no CI job re-runs the gates (accepted risk, recorded in the proposal)
