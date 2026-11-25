# Delta for ci-pipeline

## ADDED Requirements

### Requirement: Workflow Permissions Declared

Every workflow under `.github/workflows/` MUST declare a top-level `permissions:` block, and every job MUST either declare its own `permissions:` block or inherit a top-level block that is `contents: read`. A static check MUST fail when a workflow lacks a top-level block or when a job in a workflow without a read-only top-level block lacks its own.

#### Scenario: All workflows declare permissions
- GIVEN every workflow under `.github/workflows/`
- WHEN the static check runs
- THEN each has a top-level `permissions:` block and the check passes

#### Scenario: Missing top-level block
- GIVEN a workflow with no top-level `permissions:`
- WHEN the static check runs
- THEN it fails naming the workflow

#### Scenario: Job without permissions under a non-read-only default
- GIVEN a workflow whose top-level permissions grant any write scope and a job with no `permissions:` block
- WHEN the static check runs
- THEN it fails naming the workflow and the job

### Requirement: Secrets Allow-List

Workflows MUST reference only allow-listed `secrets.*` names. The allow-list MUST initially contain only `PROJECT_TOKEN`, and `github.token` and `secrets.GITHUB_TOKEN` MUST be treated as automatic tokens, not stored secrets. A static check MUST fail on any other `secrets.*` reference, and adding a name to the allow-list MUST require adding it to the secret inventory in the same change.

#### Scenario: Only allowed references
- GIVEN the workflows reference only `secrets.PROJECT_TOKEN`
- WHEN the static check runs
- THEN it passes

#### Scenario: Unlisted secret
- GIVEN a workflow referencing `secrets.NEW_API_KEY`
- WHEN the static check runs
- THEN it fails naming the workflow and the secret name

#### Scenario: Secret-free job stays secret-free
- GIVEN the `e2e-tests` job
- WHEN the static check runs
- THEN no `secrets.*` reference appears in it

### Requirement: No Secret-Like Dockerfile Variables

`backend/Dockerfile` MUST NOT define an `ENV` or `ARG` instruction whose name matches a secret-like pattern (containing `PASSWORD`, `SECRET`, `TOKEN`, `KEY` or `CREDENTIAL`, case-insensitive). The existing rule that the Dockerfile has a single digest-pinned `FROM` MUST remain enforced by the same static check.

#### Scenario: Clean Dockerfile
- GIVEN a Dockerfile with no `ENV` or `ARG`
- WHEN the static check runs
- THEN it passes

#### Scenario: Secret-like ENV rejected
- GIVEN a Dockerfile containing `ENV DB_PASSWORD=x`
- WHEN the static check runs
- THEN it fails naming the instruction without printing its value

#### Scenario: Secret-like ARG rejected
- GIVEN a Dockerfile containing `ARG API_TOKEN`
- WHEN the static check runs
- THEN it fails naming the instruction

### Requirement: Static Contract Checks Self-Tested and Run Locally

The workflow-permissions, secrets allow-list and Dockerfile checks MUST live in a script under `scripts/` with a self-test harness, in the style of `scripts/test-image-config.sh`, covering one passing and one failing fixture per rule. The script MUST run through the lefthook hooks and MUST NOT be added to `ci.yml` as a job or step, consistent with "No Static Analysis in CI". `verifyPackagedArtifact` runs inside the backend `check` and therefore in `backend-tests` in CI.

#### Scenario: Self-test covers failures
- GIVEN the self-test harness
- WHEN it runs
- THEN each rule is shown to fail on a violating fixture and pass on a clean one

#### Scenario: Runs in local hooks
- GIVEN a commit touching a workflow or the Dockerfile
- WHEN the lefthook hooks run
- THEN the static contract checks execute

#### Scenario: Not a CI job
- GIVEN the merged `ci.yml`
- WHEN its jobs and steps are listed
- THEN none invokes the static contract check script

### Requirement: Secret Inventory Document

`docs/secrets.md` (in English) MUST exist and MUST list every secret and credential with its owner, scope, storage location and rotation policy. The owner of every row MUST be `SergioLopez000`. Rows for secrets that do not yet exist MUST be marked as planned and name the change that creates them ("created by #8" or "created by #29"). The document MUST describe the Environments model (`dev` and `staging`, with `dev` created by #8), a rotation runbook (create the new value, set it in each environment, restart or redeploy, revoke the old value, verify), per-class rotation cadence, and the stop-gap manual restriction of the shipped Google Books key with its rotation gated on #29. The document MUST remain host-agnostic and MUST NOT contain secret values.

#### Scenario: Row fields complete
- GIVEN any row of the inventory
- WHEN it is read
- THEN it has owner `SergioLopez000`, scope, location and rotation policy

#### Scenario: Planned rows marked
- GIVEN a secret that does not exist yet
- WHEN its row is read
- THEN it is marked planned and names #8 or #29 as its creator

#### Scenario: Runbook and cadence present
- GIVEN `docs/secrets.md`
- WHEN its sections are listed
- THEN it has the Environments model, the rotation runbook and a rotation cadence per secret class

#### Scenario: Google Books key gated
- GIVEN the Google Books key row or runbook entry
- WHEN it is read
- THEN it states that no client key exists (not provisioned) and that the row is superseded by `GOOGLE_BOOKS_SERVER_API_KEY` (#29)
- AND the runbook warns that an Android-app restriction would reject the direct REST calls

### Requirement: Inventory Drift Guard

A test MUST fail when a `secrets.*` name referenced by any workflow (excluding the automatic `GITHUB_TOKEN`) is not listed in `docs/secrets.md`, and when the secrets allow-list contains a name absent from the inventory. The failure MUST name the missing secret.

#### Scenario: Inventory matches workflows
- GIVEN every `secrets.*` name in workflows is listed in the inventory
- WHEN the drift guard runs
- THEN it passes

#### Scenario: Undocumented secret
- GIVEN a workflow references `secrets.PROJECT_TOKEN` and the inventory omits it
- WHEN the drift guard runs
- THEN it fails naming `PROJECT_TOKEN`

#### Scenario: Guard has a self-test
- GIVEN the drift guard
- WHEN its self-test runs
- THEN it demonstrates both a passing and a failing case

### Requirement: Secrets Section in README

`README.md` (in Spanish) MUST contain a section "Secretos y rotación" that links to `docs/secrets.md` and summarizes where secrets live and how to rotate them.

#### Scenario: Section and link
- GIVEN the README
- WHEN the section "Secretos y rotación" is read
- THEN it links to `docs/secrets.md` and states the rotation entry point

## MODIFIED Requirements

### Requirement: Workflow Triggers and Runner
`.github/workflows/ci.yml` MUST run on `pull_request` and on `push` to `dev`, with no path filters, on the `ubuntu-24.04` runner. It MUST NOT use `pull_request_target`. Default workflow permissions MUST be `contents: read`; write permissions MUST be granted only to the jobs that need them (report, cleanup; `security-events: write` only for CodeQL; `packages: write` only for the GHCR publish job and the promote retag job; `issues: write` only for the `PROJECT_TOKEN` expiry reminder job). The `e2e-tests` job MUST NOT be granted any write permission. `board-sync.yml` MUST keep `contents: read` and write the project only through the `PROJECT_TOKEN` secret, never through `GITHUB_TOKEN` write scopes. Every workflow MUST declare `permissions:` and reference only allow-listed secrets, per "Workflow Permissions Declared" and "Secrets Allow-List". Third-party actions MUST be pinned to a major version.
(Previously: no `issues: write` exception and no reference to the declaration and allow-list checks.)

#### Scenario: PR triggers the pipeline
- GIVEN a pull request is opened or updated
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs regardless of which files changed

#### Scenario: Push to dev triggers the pipeline
- GIVEN a commit is pushed to `dev`
- WHEN GitHub evaluates workflows
- THEN `ci.yml` runs

#### Scenario: Least privilege
- GIVEN `ci.yml`, `pr-cleanup.yml`, `codeql.yml`, `board-sync.yml`, the GHCR workflow, `promote.yml` and the expiry reminder workflow
- WHEN their permissions are inspected
- THEN the workflow default is `contents: read`, write scopes appear only on the report and cleanup jobs, `security-events: write` appears only in `codeql.yml`, `packages: write` appears only on the publish and retag jobs, `issues: write` appears only in the expiry reminder workflow, `board-sync.yml` requests no write scope, the `e2e-tests` job holds no write scope, and no workflow uses `pull_request_target`

### Requirement: Out of Scope Boundaries
The pipeline MUST NOT include SonarCloud or any paid tooling, quality gates (spotless, lint, detekt, commit-msg, tsc) in CI, `workflow_run` based comments for fork PRs, or a Robolectric version upgrade, and this capability MUST NOT redefine gate semantics owned by `code-quality-gates`. It MUST NOT add Trivy or gitleaks beyond the existing `secret-scan` job. Permitted jobs in `ci.yml` are `unit-tests`, `backend-tests`, `contract-tests`, `e2e-tests`, `secret-scan`, `report`, `ci-gate` and `publish-image`; no other jobs are. The `e2e-tests` job MUST NOT be a dependency of `ci-gate` until a later change makes it gating.
(Previously: no statement about Trivy or additional gitleaks usage.)

#### Scenario: Only expected jobs
- GIVEN the merged workflows
- WHEN their jobs are listed
- THEN `ci.yml` has only `unit-tests`, `backend-tests`, `contract-tests`, `e2e-tests`, `secret-scan`, `report`, `ci-gate`, `publish-image`, and the other workflows are only CodeQL analysis, cleanup, promote, board sync and the `PROJECT_TOKEN` expiry reminder

#### Scenario: No forbidden tooling
- GIVEN the merged workflows
- WHEN their steps are inspected
- THEN none run SonarCloud, Trivy, an additional gitleaks scan or quality static analysis; the only test suite beyond unit and backend is the e2e suite
