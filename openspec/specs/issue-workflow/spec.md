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
The UI-only steps MUST be performed by an admin, as they cannot be automated: create a classic PAT with the `project` scope only and a finite expiry of at most 90 days, store it as the `PROJECT_TOKEN` secret, record its owner (`SergioLopez000`), expiry date and rotation procedure in the `docs/secrets.md` inventory, set auto-add to `is:issue`, disable "Pull request merged", keep "Item closed" on and "Auto-close issue" off.
(Previously: no maximum expiry and no requirement to record the token in the inventory.)

#### Scenario: Setup verified
- GIVEN the steps are completed
- WHEN the board workflows are listed
- THEN auto-add filters `is:issue`, "Pull request merged" is off, "Item closed" is on and "Auto-close issue" is off

#### Scenario: Token recorded in the inventory
- GIVEN the PAT has been created
- WHEN `docs/secrets.md` is read
- THEN the `PROJECT_TOKEN` row lists its owner, scope `project`, location as a repository secret, expiry policy of at most 90 days and rotation procedure

#### Scenario: Rotation updates the record
- GIVEN the token is rotated
- WHEN the rotation is finished
- THEN the old token is revoked and the expiry date in the inventory is updated

### Requirement: Issue Workflow Documentation
`README.md` (in Spanish) MUST have a "Flujo de issues" section stating automated vs manual transitions, the two forms, PAT creation and rotation for `PROJECT_TOKEN`, and the manual UI steps.

#### Scenario: Section content
- GIVEN the README
- WHEN "Flujo de issues" is read
- THEN it covers transitions, forms, token creation and rotation, and UI steps

### Requirement: PROJECT_TOKEN Expiry Reminder Workflow

A workflow under `.github/workflows/` MUST check the expiry of `PROJECT_TOKEN` on a `schedule` trigger and on `workflow_dispatch`, and MUST NOT use `pull_request` or `pull_request_target` triggers. Its permissions MUST be `contents: read` and `issues: write` only, and it MUST NOT be a dependency of `ci-gate` or a required check. It MUST determine the expiry by sending an authenticated request to the GitHub API with `PROJECT_TOKEN` and reading the token expiration response header. If fewer than 14 days remain (including an already expired token), it MUST open one reminder issue, or update the existing open reminder issue, so that at most one open reminder issue exists at any time. It MUST NOT open an issue when 14 or more days remain. The workflow MUST update an existing issue by editing it, and MUST NOT close any issue or post comments on issues. If `PROJECT_TOKEN` is not set, the job MUST emit a notice and exit 0.

#### Scenario: Triggers
- GIVEN the reminder workflow
- WHEN its triggers are inspected
- THEN it has a `schedule` and `workflow_dispatch`, and no pull request trigger

#### Scenario: Plenty of time left
- GIVEN the token expires in 14 or more days
- WHEN the workflow runs
- THEN no issue is opened or edited and the job succeeds

#### Scenario: Expiry is near
- GIVEN the token expires in fewer than 14 days and no reminder issue is open
- WHEN the workflow runs
- THEN one reminder issue is opened stating the expiry date and linking to the rotation runbook in `docs/secrets.md`

#### Scenario: No duplicate issues
- GIVEN a reminder issue is already open
- WHEN the workflow runs again with fewer than 14 days remaining
- THEN that issue is updated and no second issue is created

#### Scenario: Already expired
- GIVEN the token has already expired
- WHEN the workflow runs
- THEN the reminder issue is opened or updated and states that the token has expired

#### Scenario: Missing secret
- GIVEN `PROJECT_TOKEN` is not set
- WHEN the workflow runs
- THEN a notice is emitted, no issue is opened and the job succeeds

#### Scenario: Least privilege
- GIVEN the reminder workflow
- WHEN its permissions are inspected
- THEN only `contents: read` and `issues: write` are granted

#### Scenario: No closing or commenting
- GIVEN any run of the workflow
- WHEN it finishes
- THEN no issue was closed and no comment was posted

### Requirement: Expiry Header Parsing Is Defensive

The workflow MUST parse the expiration header case-insensitively and MUST treat the date as untrusted input: it MUST validate that the value parses to a valid date before using it, and MUST NOT pass the raw value to a shell command or interpolate it into a script. A header that is absent, empty or unparseable MUST NOT be treated as "no expiry" or as success. In that case the workflow MUST open or update one warning issue, distinct from the expiry reminder issue and likewise unique while open, stating that the expiry could not be determined and asking for a manual check and for the first manual dispatch to confirm the header format.

#### Scenario: Header present and valid
- GIVEN the response carries a valid expiration header in any letter case
- WHEN the workflow parses it
- THEN the remaining days are computed from that date

#### Scenario: Header missing
- GIVEN the response carries no expiration header
- WHEN the workflow runs
- THEN a warning issue is opened and the token is not treated as non-expiring

#### Scenario: Header unparseable
- GIVEN the header value is not a valid date
- WHEN the workflow runs
- THEN the warning issue is opened or updated and no remaining-days value is computed

#### Scenario: Warning issue is not duplicated
- GIVEN a warning issue is already open
- WHEN the header is still missing on the next run
- THEN that issue is updated and no second warning issue is created

#### Scenario: Value never reaches a shell unvalidated
- GIVEN a header value containing shell metacharacters
- WHEN the workflow processes it
- THEN it is rejected as unparseable and no command is executed from it

#### Scenario: Manual first run
- GIVEN the workflow is first introduced
- WHEN a maintainer starts it by `workflow_dispatch`
- THEN the run output shows whether the header was found, so the header format can be confirmed
