# Research: issue-forms-and-board-automation

Status: done (2026-11-17). Tags: V = verified (source or live check), I = inferred.

## Lane 1: issue forms `projects:`

- **Format and permission (V).** The key format is `PROJECT-OWNER/PROJECT-NUMBER`. The author "must have write permissions for the specified projects" ([syntax for issue forms](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms)).
  - The docs only show org examples.
  - Behaviour for authors without access is undocumented.
  - The docs suggest auto-add for authors without write access.
- **Duplicates (I).** The `projects:` key plus built-in auto-add is redundant here, and whether the two deduplicate is undocumented.
- **Recommendation:** drop `projects:` from the forms and rely on auto-add filtered to `is:issue`.

## Lane 2: tokens

- **`GITHUB_TOKEN` (V).** It "is scoped to the repository level and cannot access projects" ([automating projects using Actions](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/automating-projects-using-actions)). That page recommends a PAT for user projects.
- **Fine-grained PATs (V).** They cannot access user-owned Projects. This is listed as a known gap in [managing PATs](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens).
- **Scopes (V).** `read:project` covers queries and `project` covers queries and mutations ([using the API](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-api-to-manage-projects)). The workflow needs `project`.
- **Blast radius (V).** A classic PAT reaches every repo the user can access, so give it the `project` scope only and a finite expiry, and rotate it.

## Lane 3: built-in workflows

- **Auto-add filter (V).** The `is` qualifier supports `issue` and `pr` ([adding items automatically](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/adding-items-automatically)).
  - The Free plan allows **one** auto-add workflow.
  - Setup is **UI-only**: GraphQL has no create, update or enable mutation, only `deleteProjectV2Workflow`.
- **"Pull request merged" (V).** It sets merged PR items in the project to Done. With no PR items on the board it does nothing (I). Disable it to avoid confusion.
- **Bulk archive (V).** Use the `archiveProjectV2Item` mutation, or `gh project item-archive 2 --owner SergioLopez000 --id <item>`, which supports `--undo`. There is no bulk form, so loop over `gh project item-list` filtered to PullRequest.
- **Live state (V, GraphQL 2026-11-17).**
  - Enabled: Auto-add to project, Auto-add sub-issues, Item added, Item closed, Pull request merged.
  - Disabled: Auto-close issue, PR linked to issue.
  - The board holds 32 issues and 18 PRs.

## Lane 3b: GraphQL

- **Query shape (V, live 2026-11-17).** `repository{issue(number:5){projectItems(first:10){nodes{id project{id number} fieldValueByName(name:"Status"){... on ProjectV2ItemFieldSingleSelectValue{optionId name}}}}}}` returned the item `PVTI_lAHOCe4gqc4BrdLGzhIdFfw` on project 2 with Status `In progress` (`6fd7fc51`). The local token scopes include `project` and `repo`.
- **Update (V).** `updateProjectV2ItemFieldValue` takes `{projectId, itemId, fieldId, value:{singleSelectOptionId}}` ([using the API](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-api-to-manage-projects)).
- **Missing item.** When an issue is not on the board, add it with `addProjectV2ItemById`, which is idempotent (I), or skip it.
- **Rate limits (V).** 5,000 points per hour per PAT, with at least 1 s between mutations. This is far above our needs.

## Lane 4: validation

- **Schemas (V).** The `check-jsonschema` builtin schemas are `vendor.github-issue-forms` and `vendor.github-issue-config`. The pre-commit hook ids are `check-github-issue-forms` and `check-github-issue-config` ([check-jsonschema](https://check-jsonschema.readthedocs.io/en/latest/precommit_usage.html)).
- **lefthook (I).** There is no lefthook example. A job would need a Python/`uvx` dependency.
- **actionlint (I).** It does not validate issue forms.

## Lane 5: `pull_request` workflow location

- **Event refs (V).** For `pull_request`, `GITHUB_REF` is `refs/pull/N/merge`. For `closed` + merged, `GITHUB_REF` points to the base branch ([events that trigger workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)).
- **Branch-only workflows (V, prior live evidence).** `codeql.yml` existed only on the PR branch and ran on PR #42 into `dev`, so a workflow file that is not on `main` runs for PRs.
- **Secrets (V).** Same-repo PRs get secrets. Dependabot PRs are treated like forks and read Dependabot secrets instead, so `PROJECT_TOKEN` is empty for them. Guard the job.

## Conflicts with user decisions

None blocking.
- Fine-grained PATs are impossible, which confirms the classic PAT decision.
- The auto-add `is:issue` filter is a UI-only manual step.
- Drop `projects:` from the forms.

## Remaining spike items

1. A live run on this change's own PR moves #5 to In review, and the merge moves it to In dev (QA). This confirms the `closed` + merged workflow version and the PAT with `project` scope only.
2. After the UI change to auto-add `is:issue`, a new PR is not added to the board.
3. Bulk archive of the 18 PR items, which can be undone with `--undo`.
