# Delta for issue-workflow

## ADDED Requirements

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

## MODIFIED Requirements

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
