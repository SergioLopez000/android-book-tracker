# Proposal: Issue Forms and Board Automation

## Intent

Issue #5: issues are free text and the Project #2 board is moved by hand. PRs into `dev` carry no closing link, so built-in workflows cannot move the linked issue, and auto-add has put 18 PR cards on the board. This change adds structured issue forms and automates only the In review and In dev (QA) moves. Closing stays manual.

## Scope

### In Scope
- `.github/ISSUE_TEMPLATE/`:
  - `feature.yml`: user story, Given/When/Then, QA checklist, DoD without SonarCloud
  - `bug.yml`: steps, expected vs actual, environment, severity
  - `config.yml` with `blank_issues_enabled: false`
  - existing labels only, no `projects:` key
- `board-sync.yml` on `pull_request` to `dev` (opened, reopened, ready_for_review, closed).
  - Non-draft open/ready → In review; merged → In dev (QA).
  - Add the issue with `addProjectV2ItemById` if missing; never regress; never close or comment.
- `scripts/resolve-issue-number.sh` and its harness (Strict TDD, RED commit first).
  - Branch `<type>/<N>-slug` wins; fallback Closes/Fixes/Resolves `#N` (case-insensitive), never `Refs`.
  - No match → empty output, exit 0.
- `PROJECT_TOKEN` guard: notice and exit 0 when missing; same-repo PRs only; skip `dependabot[bot]`.
- Archive the 18 PR items on board 2 (`gh project item-archive`).
- README "Flujo de issues" (Spanish): automated vs manual transitions, forms, token setup and rotation, UI steps.

### Out of Scope
- Form validator hook (needs Python).
- Automating Backlog, Ready, In progress, Done.
- Making board-sync a required check; `ci-gate` is unchanged.

## Capabilities

### New Capabilities
- `issue-workflow`: issue forms, board sync transitions, issue number resolution, never close, never regress.

### Modified Capabilities
- `ci-pipeline`: MODIFY "Issue Linking and Manual Closing". Board status sync may be automated; closing stays manual; `promote.yml` still has no `issues: write`.

## Approach

Approach B from exploration: an Actions workflow plus a tested resolver plus `gh api graphql` with a classic PAT (`project` scope only, finite expiry). Workflow conventions: pinned majors, `permissions: contents: read`, event data via `env`, concurrency.

| # | Spike |
|---|---|
| S1 | This PR moves #5 to In review; the merge moves it to In dev (QA), with a `project`-only PAT |
| S2 | After the UI auto-add `is:issue` change, a new PR is not added |
| S3 | The archive is reversible with `--undo` |

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `.github/ISSUE_TEMPLATE/{feature,bug,config}.yml` | New | Forms |
| `.github/workflows/board-sync.yml` | New | Board sync |
| `scripts/resolve-issue-number.sh`, `scripts/test-resolve-issue-number.sh` | New | Resolver and harness |
| `README.md` | Modified | Flujo de issues |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| PAT expires or is broad | Med | `project` scope only; notice, not failure; documented rotation |
| Fork/Dependabot PRs lack the secret | High | Same-repo and actor guards |
| Race with manual moves | Low | Never-regress rule |
| Wrong issue resolved | Low | Branch first; closing keywords only |

## Rollback Plan

1. Delete `board-sync.yml` and the `PROJECT_TOKEN` secret.
2. Restore the 18 PR items with `gh project item-archive --undo`.
3. Restore the auto-add UI filter and re-enable "Pull request merged".

## Dependencies

Manual user steps:
- Create the classic PAT and the `PROJECT_TOKEN` secret.
- Auto-add filter to `is:issue` (UI only).
- Disable "Pull request merged".
- Keep "Item closed" on and "Auto-close issue" off.

## Success Criteria

- [ ] New issues use the feature or bug form; blank issues are disabled.
- [ ] Opening this PR moves #5 to In review; merging moves it to In dev (QA) (S1).
- [ ] No card regresses; no issue is closed or commented by automation.
- [ ] A missing token or a Dependabot PR does not fail.
- [ ] Resolver harness is green after a separate RED commit.
- [ ] PR items are archived and new PRs are not added (S2, S3).
- [ ] README documents transitions, forms, token, and UI steps.
