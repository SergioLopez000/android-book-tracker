## Capability: ci-pipeline

### Purpose
Delta: only "Issue Linking and Manual Closing" changes. Board Status sync (In review, In dev (QA)) may now be automated by `board-sync` (see `issue-workflow`); closing stays manual and `promote.yml` still has no `issues: write`. All three living scenarios still apply and are carried over unchanged; none is dropped or rewritten. Two scenarios are added.

This delta also modifies "Workflow Triggers and Runner" (its Least privilege scenario now includes `board-sync.yml` with no write scopes) and "Out of Scope Boundaries" (the No extra jobs scenario now lists board sync among the other workflows). No living scenario is dropped. "Documentation of CI and Admin Steps" is unchanged: the board admin steps are documented under the new `issue-workflow` capability.

## MODIFIED Requirements

### Requirement: Issue Linking and Manual Closing
Promotion MUST NOT be relied upon to close issues: the S2 spike (issue #39, 2026-11-17) showed that a fast-forward promotion of a commit with `Closes #N` does not close the issue, and the user chose manual closing over a `promote.yml` fallback. Each issue's branch MUST be created from the issue (`gh issue develop <N> --base dev`), so it is linked in the issue's Development section, and the PR body MUST reference the issue. Board Status MAY be updated automatically by `board-sync` (In review on PR open or ready, In dev (QA) on merge), but no automation MUST close an issue or comment on it. Before an issue is closed and moved to Done, a summary comment of the work MUST be posted on it, and the issue MUST be closed by hand. `promote.yml` MUST NOT request `issues: write`.
(Previously: board status was implicitly fully manual; the requirement did not allow automated board status sync.)

#### Scenario: Branch linked to the issue
- GIVEN work starts on issue N
- WHEN the branch is created with `gh issue develop N --base dev`
- THEN the branch appears in issue N's Development section

#### Scenario: Promotion leaves issues open
- GIVEN a promoted commit contains `Closes #N`
- WHEN `promote.yml` fast-forwards `main`
- THEN issue N stays open and the promotion succeeds

#### Scenario: Summary before closing
- GIVEN issue N has `qa:approved` and its commit is promoted
- WHEN it is moved to Done
- THEN a summary comment exists on issue N and the issue is closed by hand

#### Scenario: Board status automated, closing manual
- GIVEN a PR for issue N is opened or merged and `board-sync` runs
- WHEN the run finishes
- THEN issue N's board Status is In review or In dev (QA) and the issue is still open with no automated comment

#### Scenario: No issues write in promote
- GIVEN `promote.yml`
- WHEN its permissions are inspected
- THEN `issues: write` is not requested

### Requirement: Workflow Triggers and Runner
`.github/workflows/ci.yml` MUST run on `pull_request` and on `push` to `dev`, with no path filters, on the `ubuntu-24.04` runner. It MUST NOT use `pull_request_target`. Default workflow permissions MUST be `contents: read`; write permissions MUST be granted only to the jobs that need them (report, cleanup; `security-events: write` only for CodeQL). `board-sync.yml` MUST keep `contents: read` and write the project only through the `PROJECT_TOKEN` secret, never through `GITHUB_TOKEN` write scopes. Third-party actions MUST be pinned to a major version.

#### Scenario: PR triggers the pipeline
- GIVEN a pull request is opened or updated
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs regardless of which files changed

#### Scenario: Push to dev triggers the pipeline
- GIVEN a commit is pushed to `dev`
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs

#### Scenario: Least privilege
- GIVEN `ci.yml`, `pr-cleanup.yml`, `codeql.yml` and `board-sync.yml`
- WHEN their permissions are inspected
- THEN the workflow default is `contents: read`, write scopes appear only on the report and cleanup jobs, `security-events: write` appears only in `codeql.yml`, `board-sync.yml` requests no write scope, and no workflow uses `pull_request_target`

### Requirement: Out of Scope Boundaries
This change MUST NOT add contract, PIT or E2E jobs, SonarCloud or any paid tooling, quality gates (spotless, lint, detekt, commit-msg) in CI, `workflow_run` based comments for fork PRs, or a Robolectric version upgrade, and MUST NOT modify application code or gate semantics in `code-quality-gates` beyond adding detekt.

#### Scenario: No extra jobs
- GIVEN the merged workflows
- WHEN their jobs are listed
- THEN `ci.yml` has only `unit-tests`, `secret-scan`, `report`, `ci-gate`, and the other workflows are only CodeQL analysis, cleanup, promote and board sync
