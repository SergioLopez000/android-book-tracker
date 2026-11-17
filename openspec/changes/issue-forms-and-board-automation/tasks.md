# Tasks: Issue Forms and Board Automation

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~480-560 authored (design estimate 515; OpenSpec artifacts excluded) |
| 400-line budget risk | Medium (within project budget of 800) |
| Chained PRs recommended | No |
| Suggested split | Single PR (work-unit commits) |
| Delivery strategy | single-pr |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

Issue #5, branch `chore/5-issue-forms-board`. The project budget is 800 lines, so no `size:exception` is needed. Trim the board-sync triangulation first if the diff grows. Harnesses use POSIX/bash-3.2-safe syntax only (no `${var^^}`, no associative arrays, no `mapfile`). Per precedent, shellcheck covers the harnesses through the hook; they run in local verify only.

### Suggested Work Units (commits of the single PR, in order)

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Issue forms | PR 1 | structural readback of YAML | S4 on the New issue page | `.github/ISSUE_TEMPLATE/*` |
| 2 | RED: resolver harness only | PR 1 | `bash scripts/test-resolve-issue-number.sh` (must FAIL) | same | `scripts/test-resolve-issue-number.sh` |
| 3 | GREEN+TRIANGULATE+REFACTOR: resolver | PR 1 | same + `shellcheck scripts/*.sh` | harness incl. injection cases | `scripts/resolve-issue-number.sh` |
| 4 | RED: board-sync harness only | PR 1 | `bash scripts/test-board-sync.sh` (must FAIL) | same | `scripts/test-board-sync.sh` |
| 5 | GREEN+TRIANGULATE+REFACTOR: board-sync | PR 1 | same + `shellcheck scripts/*.sh` | `gh` stub on `PATH` | `scripts/board-sync.sh` |
| 6 | `board-sync.yml` | PR 1 | `actionlint` | S1 on this PR | `.github/workflows/board-sync.yml` |
| 7 | README and openspec config | PR 1 | structural readback | N/A (docs only) | `README.md`, `openspec/config.yaml` |

## Phase 0: Spikes

- [ ] 0.1 S1 (OUTWARD, needs A1 and A2 first): on this PR, open -> #5 "In review", merge -> "In dev (QA)"; run logs `moved` both times. Public repo, so `project`-only PAT should suffice; auth error means escalate (option `public_repo`). No secret yet: close and reopen. Merge event missing: escalate. Resolves both design Open Questions.
- [ ] 0.2 S2 (OUTWARD, after A2): this PR has no PullRequest item on Project #2. Fallback: re-check the filter, escalate.
- [ ] 0.3 S3 (OUTWARD, before A3): `gh project item-archive 2 --owner SergioLopez000 --id <id>` on 1 PR item, then `--undo`, then archive again. Pass: reappears, then disappears. Fail: stop the bulk archive, escalate.
- [ ] 0.4 S4 (OUTWARD, after push): New issue page shows only Feature and Bug, labels applied (`type:feature`, `type:bug`). Fallback: fix the YAML in this PR.

## Phase 1: Issue forms

- [ ] 1.1 Create `.github/ISSUE_TEMPLATE/config.yml` with `blank_issues_enabled: false`, no `projects:` key (D11).
- [ ] 1.2 Create `.github/ISSUE_TEMPLATE/feature.yml`: `labels: [type:feature]`, required `story` and `acceptance`, `qa`, `dod` checkboxes (no SonarCloud), `area` dropdown.
- [ ] 1.3 Create `.github/ISSUE_TEMPLATE/bug.yml`: `labels: [type:bug]`, required `steps`, `expected`, `actual`, `severity` (low/medium/high/critical), `environment`, `logs` (`render: shell`).

## Phase 2: Resolver (TDD, `scripts/resolve-issue-number.sh`)

- [ ] 2.1 RED: create `scripts/test-resolve-issue-number.sh` (style of `scripts/test-secret-scan-range.sh`) with the design case table: branch wins, keyword fallback (`fixes`, `RESOLVED`, `Close:`, second line, first match only), `Refs #5`, `other/repo#5`, `prefixes #4`, `#0`, `#05`, `#12abc`, `feat/12` no slug, `Feat/12-x`, injection (`feat/12-$(touch p)` creates no file), wrong argc -> exit 2.
- [ ] 2.2 Run the harness with no script; confirm it FAILS; capture output for the evidence table.
- [ ] 2.3 Commit RED alone (`test(ci): add failing harness for resolve-issue-number`). Script MUST NOT be in it.
- [ ] 2.4 GREEN: create `scripts/resolve-issue-number.sh` (D3 precedence, `[[ =~ ]]` line by line, no `eval`, output `^[1-9][0-9]*$`). Run harness; all pass; capture output.
- [ ] 2.5 TRIANGULATE: second inputs per branch (other numbers, CRLF body, multiple branches prefixes); mutant check (body-first order) is caught. REFACTOR: shellcheck clean, re-run. Commit GREEN+REFACTOR (`ci: add issue number resolver`).

## Phase 3: board-sync script (TDD, `scripts/board-sync.sh`)

- [ ] 3.1 RED: create `scripts/test-board-sync.sh` with a `gh` stub on `PATH` (logs argv, returns `STUB_DIR/<op>.json`) and the design cases: open -> In review from Backlog, Ready, In progress, empty; noop when In review/In dev (QA)/Done; merged -> In dev (QA) from In review and Backlog; merged when Done noop; draft noop; closed unmerged noop; `ready_for_review`; item missing -> M1 then M2; item only on another project -> M1; issue null noop; unknown optionId noop + warning; empty token -> notice, exit 0, no `gh` call; Q1 error non-zero; `ISSUE=1;x` exit 2.
- [ ] 3.2 Run the harness with no script; confirm it FAILS; capture output.
- [ ] 3.3 Commit RED alone (`test(ci): add failing harness for board-sync`). Script MUST NOT be in it.
- [ ] 3.4 GREEN: create `scripts/board-sync.sh` (env contract, rank map by option ID, Q1/M1/M2 via `gh api graphql -F/-f`, one `noop:`/`moved:` line). Run harness; all pass.
- [ ] 3.5 TRIANGULATE: second case per rank and event; assert ordered mutation list; mutant (compare by name, or always overwrite) is caught. REFACTOR: shellcheck clean, re-run. Commit GREEN+REFACTOR (`ci: add board sync script`).

## Phase 4: Workflow

- [ ] 4.1 Create `.github/workflows/board-sync.yml` per the design skeleton (pinned IDs, same-repo and non-Dependabot guard, `concurrency` per PR, `contents: read`, `PROJECT_TOKEN` in step env only, no `${{ }}` in `run`). `actionlint` clean.

## Phase 5: Docs

- [ ] 5.1 `README.md` (Spanish): "Flujo de issues" (forms, branch/`Closes #N` convention, automatic moves, manual states, board holds issues only), admin steps A1-A3 in order, PAT expiry and rotation, rollback order.
- [ ] 5.2 `openspec/config.yaml`: if absent, add the two shell harness commands to the verify rule (detekt entry exists).

## Phase 6: Local verify

- [ ] 6.1 Run `bash scripts/test-resolve-issue-number.sh`, `bash scripts/test-board-sync.sh`, `bash scripts/test-secret-scan-range.sh`, `bash scripts/test-kover-summary.sh`, `shellcheck scripts/*.sh`, `actionlint`, `lefthook validate`.

## Phase 7: Admin (OUTWARD, user confirmation for each; order matters)

- [ ] 7.1 A1 (user, BEFORE the PR opens): create a classic PAT with only `project`, expiry 90 days or less; add repo secret `PROJECT_TOKEN`.
- [ ] 7.2 A2 (user, UI, BEFORE the PR opens): auto-add filter `is:issue`; disable "Pull request merged"; keep "Item closed" on, "Auto-close issue" off. Then the assistant verifies workflow states via GraphQL.
- [ ] 7.3 A3 (assistant, after 7.2 and S3 0.3): re-list with `gh project item-list 2 --owner SergioLopez000 --format json --limit 200`, filter `PullRequest`, confirm 18, archive all.

## Phase 8: Final (issue #5)

- [ ] 8.1 (OUTWARD) When the PR opens, verify the automation moved #5 to "In review" (S1); fallback is a manual move.
- (lifecycle, not an apply task) 8.2 `sdd-verify` against both spec deltas, including Strict TDD evidence.
- [ ] 8.3 (OUTWARD) After merge to `dev`, verify #5 moved to "In dev (QA)"; fallback manual.
- (lifecycle, not an apply task) 8.4 `sdd-archive`: merge deltas into living specs `issue-workflow` and `ci-pipeline`.
- (lifecycle, not an apply task) 8.5 (OUTWARD) At Done: post the summary comment on #5, close it manually, move to "Done".
- [ ] 8.6 Delete local branch `chore/5-issue-forms-board` after merge.
- [ ] 8.7 Record the TDD Cycle Evidence table in `apply-progress` for both scripts (RED commit SHA with failing output, GREEN SHA, TRIANGULATE, REFACTOR).

| Task | Test file | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2.1-2.5 | `scripts/test-resolve-issue-number.sh` | Unit (shell) | `test-secret-scan-range.sh` green | failing run + SHA | passing run + SHA | second input per branch + mutant | shellcheck clean |
| 3.1-3.5 | `scripts/test-board-sync.sh` | Unit (shell, `gh` stub) | `test-secret-scan-range.sh` green | failing run + SHA | passing run + SHA | per rank/event + mutant | shellcheck clean |
