# Delta for code-quality-gates

## MODIFIED Requirements

### Requirement: Pre-commit Hook
The `lefthook` `pre-commit` hook MUST run, in parallel: `spotlessCheck` (Kotlin/Gradle sources, including `backend/**`), Android Lint (`./gradlew --quiet :app:lintDebug`, only when staged files match Android sources, resources or Gradle configuration globs), detekt (staged `.kt` files, including `backend/**`, see Detekt Gate With Baseline), gitleaks on staged changes, actionlint (workflow files), shellcheck with the commit-msg script test (shell files), and a TypeScript type check (`tsc --noEmit` in `/e2e`, only when staged files match `e2e/**/*.ts`). Each job MUST exit non-zero, with a message explaining how to fix it, when its gate fails. The `commit-msg` hook MUST validate Conventional Commits. These hooks are the only enforcement point of quality static analysis; CI does not repeat them (see `ci-pipeline`), including the e2e type check. Secrets are the exception: gitleaks also runs in CI, so bypassing the hook does not let a secret through.
(Previously: no TypeScript type-check job for `/e2e`.)

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
