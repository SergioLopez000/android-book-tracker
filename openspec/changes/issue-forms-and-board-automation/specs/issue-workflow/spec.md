## Capability: issue-workflow

### Purpose
Structured issue intake through GitHub issue forms, and automation of only the In review and In dev (QA) transitions of the Project #2 board from pull requests into `dev`. Closing issues and every other transition stay manual. The board holds issues only. Closing policy is owned by `ci-pipeline`; this capability never closes or comments.

## ADDED Requirements

### Requirement: Feature Issue Form
`.github/ISSUE_TEMPLATE/feature.yml` MUST define a form with: a user story, acceptance criteria in Given/When/Then, a QA checklist (checkboxes: tested in dev on a device or emulator, acceptance scenarios checked, edge and error states checked, result recorded in a comment), optional QA notes, and a Definition of Done checklist with exactly these items: SDD change linked, tests written first and green in CI, local hooks plus CodeQL and secret-scan green, manual QA in dev recorded in a comment, `qa:approved` added and promoted to staging. The Definition of Done MUST NOT mention SonarCloud.

#### Scenario: Feature form fields
- GIVEN `feature.yml`
- WHEN its fields are listed
- THEN it has user story, Given/When/Then, QA checklist and Definition of Done fields

#### Scenario: QA is a checklist
- GIVEN `feature.yml`
- WHEN the QA field is read
- THEN it is a checkboxes field with the four QA items, and free-text QA notes are optional

#### Scenario: DoD matches the project workflow
- GIVEN `feature.yml`
- WHEN its Definition of Done is read
- THEN it lists SDD change linked, tests first and green in CI, hooks plus CodeQL and secret-scan green, manual QA recorded, and `qa:approved` plus promotion

#### Scenario: DoD without SonarCloud
- GIVEN `feature.yml`
- WHEN its Definition of Done is read
- THEN no item references SonarCloud

### Requirement: Bug Issue Form
`.github/ISSUE_TEMPLATE/bug.yml` MUST define a form with: steps to reproduce, expected vs actual behavior, environment, and severity.

#### Scenario: Bug form fields
- GIVEN `bug.yml`
- WHEN its fields are listed
- THEN it has steps, expected vs actual, environment and severity fields

### Requirement: Issue Template Configuration
`.github/ISSUE_TEMPLATE/config.yml` MUST set `blank_issues_enabled: false`. The forms MUST use only labels that already exist in the repository and MUST NOT contain a `projects:` key (board membership comes from auto-add).

#### Scenario: Blank issues disabled
- GIVEN `config.yml`
- WHEN it is parsed
- THEN `blank_issues_enabled` is `false`

#### Scenario: Labels and projects
- GIVEN `feature.yml` and `bug.yml`
- WHEN their `labels` and keys are inspected
- THEN every label exists in the repository and no `projects:` key is present

### Requirement: Issue Number Resolution
`scripts/resolve-issue-number.sh` MUST print the issue number for a PR from its head branch and body. A branch named `<type>/<N>-slug` MUST take precedence. Only if the branch yields no number, a `Closes`, `Fixes` or `Resolves` keyword followed by `#N` in the body MUST be used, case-insensitively. `Refs #N` MUST be ignored. With no number it MUST print nothing and exit 0. A harness `scripts/test-resolve-issue-number.sh` MUST cover these cases.

#### Scenario: Branch wins
- GIVEN branch `chore/5-issue-forms-board` and a body with `Closes #9`
- WHEN the resolver runs
- THEN it prints `5`

#### Scenario: Keyword fallback
- GIVEN branch `dependabot/foo` and a body with `fixes #7`
- WHEN the resolver runs
- THEN it prints `7`

#### Scenario: Refs ignored
- GIVEN a non-matching branch and a body with only `Refs #8`
- WHEN the resolver runs
- THEN it prints nothing and exits 0

#### Scenario: No number
- GIVEN a non-matching branch and a body without references
- WHEN the resolver runs
- THEN the output is empty and the exit code is 0

### Requirement: Board Sync Transitions
`.github/workflows/board-sync.yml` MUST run on `pull_request` to `dev` for `opened`, `reopened`, `ready_for_review` and `closed`. For a non-draft PR that is opened, reopened or ready, it MUST set the resolved issue's Status on board 2 to In review. For a PR closed with merge, it MUST set Status to In dev (QA). If the issue is not on the board, it MUST add it first. It MUST NOT move a card backwards, MUST NOT close any issue and MUST NOT post any comment.

#### Scenario: PR opened
- GIVEN a non-draft same-repo PR to `dev` resolving to issue 5 in Backlog, Ready or In progress
- WHEN the PR is opened
- THEN issue 5 moves to In review

#### Scenario: Draft PR
- GIVEN a draft PR
- WHEN it is opened
- THEN no move occurs, and a later `ready_for_review` moves the issue to In review

#### Scenario: PR merged
- GIVEN a PR merged into `dev` resolving to issue 5
- WHEN the `closed` event fires
- THEN issue 5 moves to In dev (QA)

#### Scenario: Closed without merge
- GIVEN a PR closed without merging
- WHEN the `closed` event fires
- THEN no Status change occurs

#### Scenario: Never regress
- GIVEN issue 5 is already In dev (QA) or Done
- WHEN a reopened PR would set In review
- THEN the Status is unchanged

#### Scenario: Missing item added
- GIVEN the resolved issue is not on board 2
- WHEN the workflow runs
- THEN the issue is added to board 2 and then moved

#### Scenario: No side effects on the issue
- GIVEN any board-sync run
- WHEN it finishes
- THEN the issue state is unchanged and no comment was posted

### Requirement: Board Sync Guards and Permissions
The workflow MUST read the `PROJECT_TOKEN` secret. If it is missing, the job MUST emit a notice and exit 0. It MUST run only for same-repository PRs and MUST skip `dependabot[bot]`. Its permissions MUST be `contents: read`, event data MUST reach scripts through `env`, and actions MUST be pinned to a major version. `board-sync` MUST NOT be a required check and `ci-gate` MUST NOT depend on it.

#### Scenario: Missing token
- GIVEN `PROJECT_TOKEN` is not set
- WHEN the workflow runs
- THEN a notice is emitted, no board change occurs and the job succeeds

#### Scenario: Fork or Dependabot PR
- GIVEN a fork PR or a PR by `dependabot[bot]`
- WHEN the workflow runs
- THEN the sync step is skipped and the run does not fail

#### Scenario: Least privilege
- GIVEN `board-sync.yml`
- WHEN its permissions and steps are inspected
- THEN the permissions are `contents: read` and PR title, body and branch are passed via `env`, not interpolated into scripts

#### Scenario: Not a required check
- GIVEN `ci-gate`
- WHEN its dependencies are inspected
- THEN `board-sync` is not among them

### Requirement: Manual Board Transitions
Backlog, Ready and In progress MUST be set by hand. Done MUST be set only by the built-in "Item closed" workflow when an issue is closed by hand; "Auto-close issue" MUST stay disabled.

#### Scenario: Manual close moves to Done
- GIVEN an issue in In dev (QA) with a summary comment
- WHEN the maintainer closes it
- THEN "Item closed" sets its Status to Done

#### Scenario: Automation does not set manual states
- GIVEN the repository workflows
- WHEN they are inspected
- THEN none sets Backlog, Ready, In progress or Done

### Requirement: Board Holds Issues Only
Board 2 MUST contain issues only. The single auto-add workflow MUST use the filter `is:issue`, "Pull request merged" MUST be disabled, and the existing PR items MUST be archived (reversible with `--undo`).

#### Scenario: New PR not added
- GIVEN auto-add filtered to `is:issue`
- WHEN a new PR is opened
- THEN it is not added to the board

#### Scenario: PR items archived
- GIVEN the 18 existing PR items
- WHEN the archive step is done
- THEN the board shows no PR items and each can be restored with `--undo`

### Requirement: Admin Setup Steps
The UI-only steps MUST be performed by an admin, as they cannot be automated: create a classic PAT with the `project` scope only and a finite expiry, store it as the `PROJECT_TOKEN` secret, set auto-add to `is:issue`, disable "Pull request merged", keep "Item closed" on and "Auto-close issue" off.

#### Scenario: Setup verified
- GIVEN the steps are completed
- WHEN the board workflows are listed
- THEN auto-add filters `is:issue`, "Pull request merged" is off, "Item closed" is on and "Auto-close issue" is off

### Requirement: Issue Workflow Documentation
`README.md` (in Spanish) MUST have a "Flujo de issues" section stating automated vs manual transitions, the two forms, PAT creation and rotation for `PROJECT_TOKEN`, and the manual UI steps.

#### Scenario: Section content
- GIVEN the README
- WHEN "Flujo de issues" is read
- THEN it covers transitions, forms, token creation and rotation, and UI steps
