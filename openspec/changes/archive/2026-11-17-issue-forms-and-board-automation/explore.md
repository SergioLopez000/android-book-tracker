# Exploration: issue-forms-and-board-automation

Issue #5, branch `chore/5-issue-forms-board`. Mirrored from Engram `sdd/issue-forms-and-board-automation/explore` (obs #116), plus orchestrator live checks (2026-11-17).

## Current State

- `.github/` has `ci.yml`, `codeql.yml`, `promote.yml`, `pr-cleanup.yml` and `dependabot.yml`. There is no `ISSUE_TEMPLATE/`.
- **Workflow conventions**:
  - pinned majors
  - top-level `permissions: contents: read`, with job-level escalation only where needed
  - event data passed through `env:`
  - the same-repo fork guard (`pr-cleanup.yml`)
  - concurrency groups
- **Script conventions**: `scripts/*.sh`, each with a table-driven `scripts/test-*.sh` harness under Strict TDD. The RED commit lands separately.
- **Living spec `ci-pipeline`, requirement "Issue Linking and Manual Closing"**:
  - the branch is created with `gh issue develop N --base dev`
  - the PR body references the issue
  - a summary comment is posted before the manual close
  - `promote.yml` MUST NOT request `issues: write`
- **README "Flujo de issues"** describes a manual board flow.
- **PRs into `dev`** carry no closing-keyword link (`closingIssuesReferences` is empty).
- **Project #2** (user-owned, `PVT_kwHOCe4gqc4BrdLG`, Status field `PVTSSF_lAHOCe4gqc4BrdLGzhpv1bI`). Options:

  | Status | Option ID |
  |---|---|
  | Backlog | ff5e3c08 |
  | Ready | fc42ffad |
  | In progress | 6fd7fc51 |
  | In review | a5f0b94c |
  | In dev (QA) | beabd43b |
  | Done | cbf60025 |

- **Built-in project workflows**, checked live via GraphQL on 2026-11-17:
  - enabled:
    - Auto-add to project
    - Auto-add sub-issues to project
    - Item added to project
    - Item closed
    - Pull request merged
  - disabled:
    - Auto-close issue
    - Pull request linked to issue
- **Board contents** (live): 32 issues and **18 pull requests**. "Auto-add to project" adds PRs too, and PR items sit in "In dev (QA)".
- **Labels** (live): type:chore/feature/ci/test/bug, area:app/backend/infra, test:contract/unit/e2e/mutation/manual, qa:approved, tech-debt, idea, dependencies, java, plus the GitHub defaults.
- **`gh` token scopes locally**: `gist, project, read:org, repo, workflow`.

## Findings

1. **Issue forms**
   - Top-level keys:
     - required: `name` (unique), `description`, `body`
     - optional: `title`, `labels` (applied only if they exist), `assignees`, `type`, `projects` (`"OWNER/NUMBER"`, submitter needs project write access)
   - Body types:
     - `markdown`: not submitted, and cannot be the only element
     - `input`
     - `textarea`: `render`, `value`, `validations.required`
     - `dropdown`
     - `checkboxes`: `required` per option
     - `upload`
   - Every input needs a unique `id`.
   - `config.yml` takes `blank_issues_enabled` and `contact_links`.
   - Forms are in public preview.
   - No official validator exists. `check-jsonschema` vendors issue-form and config schemas (names unconfirmed).
2. **Built-in workflows**
   - They act on the item that triggered them, the PR item or the issue itself, and not on a linked issue unless GitHub knows the link (it does not for PRs into `dev`).
   - "Issue status changed" closes issues, so it must stay off.
   - Free plan: 1 auto-add workflow.
3. **Token**
   - `GITHUB_TOKEN` cannot access Projects v2.
   - For user-owned projects the docs recommend a classic PAT with the `project` scope (`repo` only for private repos).
   - Fine-grained PATs reportedly do not work for user-owned Projects GraphQL. This comes from secondary sources and is unverified.
   - The classic `project` scope covers all of the user's projects and expires.
4. **Secrets**
   - Same-repo `pull_request` runs get secrets; fork PRs get none.
   - Dependabot PRs use a separate secret store and their branches have no issue number, so guard and skip them.
   - Use `pull_request`, not `pull_request_target`, and never check out PR code in a privileged job.
5. **`actions/add-to-project`** (MIT) only adds items and cannot set Status. Auto-add already exists, so it is not needed.
6. **Transitions**
   - Automate:
     - PR opened/reopened/ready_for_review → In review
     - PR merged into `dev` → In dev (QA)
   - Keep manual:
     - Done: needs `qa:approved` + promotion + summary + manual close. The built-in "Item closed" sets Done when the issue is closed by hand.
     - In progress: a `create` event only runs default-branch workflows, and `gh issue develop` is local.
     - Backlog and Ready.
   - Automation must never close or comment on issues, and must never regress a card.

## Approaches

| Approach | Pros | Cons |
|---|---|---|
| A. Built-in workflows only | Free, no secret | Acts on PR items, not linked issues; cannot set In dev (QA) on the issue; not reviewable or testable |
| **B. Actions workflow (`pull_request`, base `dev`) + TDD parser + `gh api graphql` with a classic PAT secret** | Exact transitions on the issue; reviewable and testable; matches repo conventions | Broad classic PAT that expires |
| C. B + `actions/add-to-project` | — | Extra dependency, no gain |
| D. B on `pull_request_target` | Workflow from base | Larger injection surface, no gain |

## Recommendation

Approach B plus issue forms.

- **Forms**:
  - `feature.yml`: user story, Given/When/Then, QA checkboxes, DoD without SonarCloud, existing labels, `projects: ["SergioLopez000/2"]`.
  - `bug.yml`: steps, expected vs actual, environment.
  - `config.yml`: `blank_issues_enabled: false`.
- **Parser** (`scripts/resolve-issue-number.sh <branch> <body>`, tests first):
  - Read the number from the branch with `^[a-z]+/([0-9]+)-`.
  - Fall back to the body: Closes/Fixes/Resolves `#N`.
  - Print nothing and exit 0 when no number is found.
- **Workflow** `board-sync.yml`:
  - `pull_request` with types `[opened, reopened, ready_for_review, closed]` and `branches: [dev]`.
  - Run only for same-repo PRs, skip `dependabot[bot]`, and skip drafts for the In review move.
  - Look up the issue's project item and its current Status.
  - Never regress a card, then call `updateProjectV2ItemFieldValue`.
  - If `PROJECT_TOKEN` is missing, print a notice and exit 0. The workflow is not a required check.
- **Specs**:
  - New capability `issue-workflow`.
  - Delta on `ci-pipeline` "Issue Linking and Manual Closing": board sync may be automated, but issues are still never closed by automation.
- **README**: describe the automated In review / In dev (QA) moves, the steps that stay manual, the `PROJECT_TOKEN` admin step with rotation, and the forms.

## Affected Areas

- `.github/ISSUE_TEMPLATE/{feature,bug,config}.yml` (new)
- `.github/workflows/board-sync.yml` (new)
- `scripts/resolve-issue-number.sh` and its test (new)
- `README.md`
- the `ci-pipeline` delta and the new `issue-workflow` spec
- Size: about 300 lines.

## Risks

- **PAT**: broad scope and expiry. If it expires, board-sync goes silent, mitigated by the notice and the README rotation step.
- **Secrets on Dependabot and fork PRs**: neither gets the secret, so both must be guarded.
- **PR items on the board** (live: 18). The built-in "Pull request merged" sets merged PR items to a status. That duplicates the issue cards and can confuse the board.
- **`Refs #N`** may point to an unrelated issue.
- **Races with manual moves**: covered by the never-regress rule.
- **Labels**: a missing label is dropped silently.
- **Unverified**:
  - form `projects:` on a user project
  - the fine-grained PAT limitation
  - the `check-jsonschema` schema names
  - `pull_request` with the workflow only on `dev` (should work; needs a probe)

## Open questions

1. Body fallback: only Closes/Fixes/Resolves, or also `Refs #N`?
2. Is a classic PAT with the `project` scope, stored as `PROJECT_TOKEN`, acceptable?
3. Should PRs stay on the board as cards, or should auto-add be limited to issues?
4. Done and In progress stay manual (recommended).

## Research

The remaining unverified items need live checks, and a narrow research phase is possible.
