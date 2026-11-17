# Design: Issue Forms and Board Automation

## Technical Approach

Approach B from exploration. The forms are static YAML. The board moves come from an Actions workflow on `pull_request` to `dev`, which calls two scripts:

- `scripts/resolve-issue-number.sh` turns the branch and the PR body into an issue number.
- `scripts/board-sync.sh` holds every decision: the target status, the never-regress rule and add-if-missing. It talks to GitHub only through `gh api graphql`.

The workflow is thin glue: guards, env and checkout. It authenticates with a classic PAT (`PROJECT_TOKEN`, `project` scope only). Both scripts are tested offline under Strict TDD; `board-sync.sh` uses a `gh` stub on `PATH`. Board admin steps are UI-only or `gh project`, and each one is confirmed with the user. Closing, commenting, Backlog, Ready, In progress and Done stay manual.

## Architecture Decisions

| # | Decision | Chosen | Rejected | Rationale |
|---|---|---|---|---|
| D1 | Engine | Actions workflow `board-sync.yml` | Built-in project workflows only; `actions/add-to-project`; `pull_request_target` | Built-ins act on the PR item, not on the unlinked issue, because PRs into `dev` carry no closing link. add-to-project cannot set Status. `pull_request_target` widens the injection surface for no gain. |
| D2 | Token | Classic PAT, `project` scope only, finite expiry, secret `PROJECT_TOKEN` | `GITHUB_TOKEN`; fine-grained PAT; GitHub App | `GITHUB_TOKEN` cannot reach Projects v2 (V). Fine-grained PATs cannot reach user-owned projects (V). An App is too heavy for a one-person board. |
| D3 | Resolver precedence | Branch `^[a-z]+/([1-9][0-9]*)-` first; then the first `close[sd]?\|fix(e[sd])?\|resolve[sd]?` + `#N` in the body, case-insensitive; never `Refs` | Body first; accepting `Refs #N`; `closingIssuesReferences` | The branch comes from `gh issue develop` and is authoritative. `Refs` often points to related issues. `closingIssuesReferences` is empty for PRs into `dev`. |
| D4 | Never regress | Rank map keyed by **option ID**: Backlog 0 < Ready 1 < In progress 2 < In review 3 < In dev (QA) 4 < Done 5. Move only if `rank(current) < rank(target)`. Empty status counts as -1. An unknown option ID is a no-op with a warning | Compare by name; always overwrite | IDs survive option renames. Comparing ranks makes manual moves and duplicate events safe. Failing closed on an unknown option avoids wrong moves. |
| D5 | Jumps from Backlog or Ready | Allowed: a card in Backlog or Ready goes straight to In review (or In dev (QA)) | Require In progress first | A PR exists, so work has started. Blocking the jump would leave stale cards. |
| D6 | Missing item | `addProjectV2ItemById`, then update | Skip with a notice | Every issue with a PR belongs on the board. Auto-add normally makes this rare. |
| D7 | Missing token | `::notice::` and exit 0 | Fail the job | An unset secret (before A1, or after deletion) must not turn PRs red. The check is not required. Other GraphQL errors exit non-zero, so an expired PAT stays visible. |
| D8 | Logic location | Second script `scripts/board-sync.sh` + `scripts/test-board-sync.sh` with a `gh` stub | Inline shell in the workflow; a `DRY_RUN` flag | Never-regress and the event mapping are core requirements and need RED tests (precedent D8 of security-quality-gates). A stub covers the calls without a production-only dry-run path. Cost is about 190 lines, which fits the budget. |
| D9 | IDs | Pinned in workflow `env` (project, field, six option IDs) | Resolve field and options by name at runtime | One board, verified IDs. Pinning saves a query and about 25 lines of jq. A stale ID makes the mutation fail visibly (D7). Renaming an option needs no change, because the map uses IDs. The cost: a recreated option means editing `env`. |
| D10 | Form validation | None (actionlint ignores forms) | `check-jsonschema` lefthook job | It would add Python/`uvx`. Three small files are reviewed by eye and checked live (S4). |
| D11 | Forms → board | No `projects:` key; built-in auto-add filtered to `is:issue` | `projects: ["SergioLopez000/2"]` | Undocumented for user projects and authors without access; could duplicate auto-add. |
| D12 | Built-in workflows | UI-only, done by the user: set auto-add to `is:issue`; disable "Pull request merged"; keep "Item closed" on and "Auto-close issue" off | Change them via GraphQL | GraphQL has no create/update mutation for workflows (V). |
| D13 | PR cleanup | `gh project item-archive 2 --owner SergioLopez000 --id <id>` looped over the PullRequest items | Delete the items; leave them | Archiving is reversible (`--undo`) and has no bulk form. |

## Data Flow

```
PR → dev (opened|reopened|ready_for_review|closed)
  job if: same repo && not dependabot
  checkout (sparse: scripts)
  ISSUE=$(resolve-issue-number.sh "$HEAD_REF" "$PR_BODY")   # empty → notice, exit 0
  board-sync.sh:
    target = f(ACTION, DRAFT, MERGED)        # none → no-op
    token empty → notice, exit 0
    Q1 lookup(issue) → issue.id, item on PROJECT_ID?, optionId
    item missing → M1 addProjectV2ItemById → item.id, current = empty
    rank(current) >= rank(target) → no-op
    M2 updateProjectV2ItemFieldValue(target)
```

Event mapping: `opened`, `reopened` or `ready_for_review` with no draft → In review. `closed` with `merged` → In dev (QA). Drafts and closed-unmerged PRs → no-op.

## Workflow Skeleton

```yaml
# Moves the issue linked to a PR into dev on Project #2: In review on open,
# In dev (QA) on merge. Never regresses, never closes or comments.
name: Board sync
on:
  pull_request:
    types: [opened, reopened, ready_for_review, closed]
    branches: [dev]
permissions:
  contents: read
concurrency:
  group: board-sync-${{ github.event.pull_request.number }}
  cancel-in-progress: false
jobs:
  sync:
    name: sync
    if: >-
      github.event.pull_request.head.repo.full_name == github.repository &&
      github.actor != 'dependabot[bot]' &&
      github.event.pull_request.user.login != 'dependabot[bot]'
    runs-on: ubuntu-24.04
    timeout-minutes: 5
    env:
      PROJECT_ID: PVT_kwHOCe4gqc4BrdLG
      STATUS_FIELD_ID: PVTSSF_lAHOCe4gqc4BrdLGzhpv1bI
      OPT_BACKLOG: ff5e3c08
      OPT_READY: fc42ffad
      OPT_IN_PROGRESS: 6fd7fc51
      OPT_IN_REVIEW: a5f0b94c
      OPT_IN_DEV_QA: beabd43b
      OPT_DONE: cbf60025
    steps:
      - uses: actions/checkout@v7
        with: { sparse-checkout: scripts, persist-credentials: false }
      - name: Sync board status
        env:
          GH_TOKEN: ${{ secrets.PROJECT_TOKEN }}
          HEAD_REF: ${{ github.head_ref }}
          PR_BODY: ${{ github.event.pull_request.body }}
          ACTION: ${{ github.event.action }}
          DRAFT: ${{ github.event.pull_request.draft }}
          MERGED: ${{ github.event.pull_request.merged }}
        run: |
          ISSUE="$(scripts/resolve-issue-number.sh "$HEAD_REF" "$PR_BODY")"
          if [ -z "$ISSUE" ]; then echo "::notice::No linked issue; nothing to sync"; exit 0; fi
          ISSUE="$ISSUE" scripts/board-sync.sh
```

`cancel-in-progress: false` keeps the merge event from being cancelled. `github.repository` gives the owner and name. The checkout ref is the merge commit (open events) or `dev` (merged `closed`); both contain the scripts.

## Interfaces

**`scripts/resolve-issue-number.sh <branch> <body>`**
- Prints the number or nothing, and exits 0.
- A wrong argument count exits 2.
- Pure: no `eval`, no network. It only matches with `[[ =~ ]]`, line by line.
- The output always matches `^[1-9][0-9]*$`.

**`scripts/board-sync.sh`**
- Reads env: `ISSUE`, `ACTION`, `DRAFT`, `MERGED`, `GH_TOKEN`, `GITHUB_REPOSITORY`, `PROJECT_ID`, `STATUS_FIELD_ID`, `OPT_*`.
- Prints one decision line: `noop: <reason>` or `moved: #N <from> -> <to>`.
- `ISSUE` must match `^[1-9][0-9]*$` or the script exits 2.
- A GraphQL error exits non-zero. Variables are passed with `-F`/`-f` only, never by string interpolation.

**GraphQL**

```graphql
# Q1 (vars: owner, name, number)
query($owner:String!,$name:String!,$number:Int!){repository(owner:$owner,name:$name){
  issue(number:$number){id projectItems(first:10){nodes{id project{id number}
    fieldValueByName(name:"Status"){... on ProjectV2ItemFieldSingleSelectValue{optionId name}}}}}}}
# M1
mutation($p:ID!,$c:ID!){addProjectV2ItemById(input:{projectId:$p,contentId:$c}){item{id}}}
# M2
mutation($p:ID!,$i:ID!,$f:ID!,$o:String!){updateProjectV2ItemFieldValue(input:{
  projectId:$p,itemId:$i,fieldId:$f,value:{singleSelectOptionId:$o}}){projectV2Item{id}}}
```

The item is picked with jq: `.nodes[] | select(.project.id == $PROJECT_ID)`. A null `issue` (the number is a PR or does not exist) → `noop`.

## Form Skeletons

- **`config.yml`**: `blank_issues_enabled: false`.
- **`feature.yml`**:
  - `name: Feature`, `labels: [type:feature]`
  - `textarea story` (required): "As a … I want … so that …"
  - `textarea acceptance` (required): Given/When/Then
  - `textarea qa`: test notes
  - `checkboxes dod`: tests green, coverage report reviewed, README/specs updated, `qa:approved` before Done. No SonarCloud.
  - `dropdown area`: app/backend/infra. Informative only; the area label is applied by hand.
- **`bug.yml`**:
  - `name: Bug`, `labels: [type:bug]`
  - `textarea steps` (required)
  - `textarea expected` and `textarea actual` (required)
  - `input environment`: device, Android version, app version
  - `dropdown severity` (required): low/medium/high/critical
  - `textarea logs` (`render: shell`)

## Testing Strategy (Strict TDD: RED commit per script first)

| Script | Harness cases (`<input> → <output>`) |
|---|---|
| resolve | `feat/12-x`→12; `chore/5-issue-forms-board`→5; branch `fix/7-x` + `Closes #9`→7; `dependabot/gradle/x` + `Closes #9`→9; `fixes #3`→3; `RESOLVED #4`→4; `Close: #8`→8; second-line keyword→found; `Fixes #3 and closes #4`→3; `Refs #5`→empty; `Closes other/repo#5`→empty; `prefixes #4`→empty; `#0`, `#05`, `#12abc`→empty; `feat/12` (no slug)→body; `Feat/12-x`→body; branch `feat/12-$(touch p)` and a body with backticks→12/empty, no file `p` created; wrong argc→exit 2 |
| board-sync | open → In review from each of Backlog, Ready, In progress, and from empty; open when already In review, In dev (QA) or Done→noop with no mutation; merged → In dev (QA) from In review and from Backlog; merged when Done→noop; draft opened→noop; closed unmerged→noop; ready_for_review→In review; item missing→M1 then M2; item only on another project→M1; issue null→noop; unknown optionId→noop with a warning; empty token→notice, exit 0, no `gh` call; Q1 error→non-zero; `ISSUE=1;x`→exit 2 |

The `gh` stub logs its argv to a file and returns canned JSON from `STUB_DIR/<op>.json`. Assertions cover stdout and the ordered list of mutations. shellcheck and actionlint run through lefthook. Live checks are S1–S4.

## Phase 0 Spikes

| # | Probe | Pass | Fallback |
|---|---|---|---|
| S1 | This PR: open → #5 In review; merge → In dev (QA). PAT created **before** the PR opens | The run logs `moved` both times and the board matches | No secret yet: close and reopen the PR. Auth error with `project` only: escalate (option `public_repo`). Merge event missing: escalate |
| S2 | After the auto-add filter is set to `is:issue`, this PR is not added to the board | No PR item for this PR | Re-check the filter; escalate |
| S3 | Archive 1 item, `--undo`, archive again | The item reappears, then disappears | Stop the bulk archive; escalate |
| S4 | The New issue page shows only Feature and Bug, with labels applied | As stated | Fix the YAML in the same PR |

## Admin Steps (in order, each confirmed with the user)

1. **A1 (user).** Create a classic PAT with the `project` scope only and an expiry (90 days or less). Add it as the repo secret `PROJECT_TOKEN`. Do this before the PR opens.
2. **A2 (user, UI).** Set the auto-add filter to `is:issue`. Disable "Pull request merged". Keep "Item closed" on and "Auto-close issue" off. Do this before the PR opens, so S2 can use this PR.
3. **A3 (assistant, after A2 and S3).** Re-list with `gh project item-list 2 --owner SergioLopez000 --format json --limit 200`, filtered to `PullRequest`. Confirm the count (18 expected), then loop `item-archive`.

**Rollback order:** (1) revert `board-sync.yml` (forms and scripts are independent); (2) delete `PROJECT_TOKEN` and revoke the PAT; (3) `item-archive --undo` for the PR items, if wanted; (4) restore the auto-add filter and "Pull request merged". Card moves already made stay as they are.

## Threat Matrix

| Boundary | Applicability | Design response | RED tests |
|---|---|---|---|
| Documentation-like paths | N/A: forms are data; nothing executes by name | — | — |
| Git repository selection | N/A: no git commands; repo comes from `GITHUB_REPOSITORY` | — | — |
| Commit state | N/A: no commits made | — | — |
| Push state | N/A: no pushes | — | — |
| PR commands | Applicable | Branch and body go only through `env`, into argv, then into regex. `ISSUE` is validated. GraphQL takes typed variables. No `${{ }}` in `run` | Resolver injection cases; board-sync `ISSUE=1;x` |

| Risk | Mitigation |
|---|---|
| PAT scope, expiry or leak | `project` only; expiry with rotation in the README; secret only in step env; `persist-credentials: false`; same-repo guard (authors already have write access) |
| Forks and Dependabot | Job `if` on repo, actor and author; an empty token gives a notice |
| Races with manual moves or duplicate events | Rank check (D4); per-PR concurrency |
| Built-in "Item added" sets a status after M1 | Low (issues are auto-added at creation). Observe in S1; worst case is a manual fix |
| Wrong issue resolved | D3 regex with word boundary, first match only |

## File Changes and Size (authored lines; budget 800)

| File | Action | Lines |
|---|---|---|
| `.github/ISSUE_TEMPLATE/feature.yml`, `bug.yml`, `config.yml` | Create | ~110 |
| `.github/workflows/board-sync.yml` | Create | ~45 |
| `scripts/resolve-issue-number.sh` + `test-` | Create | ~30 + ~75 |
| `scripts/board-sync.sh` + `test-` | Create | ~80 + ~130 |
| `README.md` "Flujo de issues" + admin steps | Modify | ~45 |
| **Total** | | **≈515** |

OpenSpec artifacts are not counted (precedent). If needed, drop the board-sync harness triangulation first.

## Living Specs Interaction

- New capability `issue-workflow`.
- MODIFY these `ci-pipeline` requirements:
  - "Issue Linking and Manual Closing": status sync may be automated, closing stays manual, and `promote.yml` gets no `issues: write`.
  - "Least privilege" scenario: add `board-sync.yml`.
  - "Out of Scope Boundaries" scenario: "other workflows are only…" must list board-sync.
  - "Documentation of CI and Admin Steps": add the PAT and UI steps.
- "No Static Analysis in CI" stays satisfied: the harnesses run locally only.

## Open Questions

- [ ] Is the repo public? `project` alone can read public issues; a private repo needs `repo` (S1 decides).
- [ ] Should the new harnesses run in the lefthook shellcheck job? The precedent (`test-secret-scan-range.sh`) does not.
