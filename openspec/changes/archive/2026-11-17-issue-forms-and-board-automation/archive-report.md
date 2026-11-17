# Archive Report: issue-forms-and-board-automation

**Archived**: 2026-11-17
**Archive path**: `openspec/changes/archive/2026-11-17-issue-forms-and-board-automation/`
**Issue**: #5 (implementation PR #51, closure PR #52)
**Repository state at close**: HEAD `ccfa537` (`docs(sdd): verify issue-forms-and-board-automation`), branch `docs/5-close-issue-forms-board`
**Artifact store**: openspec (per `gentle-ai sdd-status ... --json --instructions`: `artifactStore: openspec`, `dependencies.archive: ready`, `nextRecommended: archive`, `blockedReasons: []`). The launch prompt also asked for Engram persistence (hybrid mirror, project `libros-leidos`, topic key `sdd/issue-forms-and-board-automation/archive-report`).
**Intentional partial archive**: No. Proposal, both delta specs, design, tasks and verify-report are all present. `applyProgress` is `missing` in the openspec locator set; the TDD evidence lives in the verify-report and tasks artifact (see Observation IDs), so this is recorded, not a blocker.

## Observation IDs

Engram observations read by this archive: none. Every artifact was read from its openspec locator, which is the store the orchestrator reported.

Cited but not read by this archive (references only):
- Verify observation `#124` (named in the launch prompt as the Engram record of the verify-report).
- Apply-progress observation `#123` (cited inside `verify-report.md` and `tasks.md` as the TDD Cycle Evidence source; not an artifact locator in this store).

## Artifacts Read

- `openspec/changes/issue-forms-and-board-automation/proposal.md`
- `openspec/changes/issue-forms-and-board-automation/specs/ci-pipeline/spec.md` (delta: 3 MODIFIED)
- `openspec/changes/issue-forms-and-board-automation/specs/issue-workflow/spec.md` (delta: 10 ADDED)
- `openspec/changes/issue-forms-and-board-automation/design.md`
- `openspec/changes/issue-forms-and-board-automation/tasks.md`
- `openspec/changes/issue-forms-and-board-automation/verify-report.md`

## Gate and Task Completion

- Native status at archive time: `archive: ready`, `nextRecommended: archive`, `blockedReasons: []`, `remediationState.required: false`, `taskProgress` 27/27 complete.
- Task completion (`tasks.md`): 27 `[x]`, 0 `[ ]` (checked by `grep` for `- [ ]`, no match). No stale checkboxes, so no archive-time reconciliation was performed.
- Lifecycle items that are not checkboxes in `tasks.md` (0.4 S4, 8.2 verify, 8.4 archive, 8.5 close at Done) are reported under Final-State Facts below.

## Verification at Close

Ranking (per `sdd-archive` Final-State Authority): the persisted tasks artifact first, then the launch prompt's explicit final-state facts, then the `verify-report` snapshot.

- `verify-report.md` (snapshot, written at verification time): verdict `pass` in the envelope, `PASS WITH WARNINGS` in the body, `blockers: 0`, `critical_findings: 0`, requirements 13/13, scenarios 37/37. The body names HEAD `053518c`; the verify commit that persisted it is `ccfa537`.
- Launch-prompt final-state facts agree: PASS WITH WARNINGS, 13/13 requirements, 37/37 scenarios (26 by live/harness evidence, 11 user-accepted), commit `ccfa537`.
- Test count carried from `verify-report.md` (no later test change was reported by any higher-ranked source): `./gradlew :app:testDebugUnitTest --rerun` exit 0, 102 passed, 0 failed, 0 skipped. Build `./gradlew :app:assembleDebug` exit 0.
- Other checks recorded at verify time: `actionlint` clean, `shellcheck scripts/*.sh` clean, `scripts/test-resolve-issue-number.sh` and `scripts/test-board-sync.sh` all cases passed, `scripts/test-secret-scan-range.sh`, `scripts/test-kover-summary.sh`, `scripts/test-check-commit-msg.sh` (31 cases) passed, `lefthook validate` All good, detekt exit 0.

### Scenario Evidence (37 total)

- 26 scenarios rest on runtime, live or harness evidence (LIVE or HARNESS in `verify-report.md`).
- 11 scenarios are user-accepted on static evidence (file inspection, accepted in chat on 2026-11-17):
  1. QA is a checklist (Feature Issue Form)
  2. DoD matches the project workflow (Feature Issue Form)
  3. DoD without SonarCloud (Feature Issue Form)
  4. Labels and projects (Issue Template Configuration)
  5. Fork or Dependabot PR (Board Sync Guards and Permissions)
  6. Least privilege (Board Sync Guards and Permissions)
  7. Automation does not set manual states (Manual Board Transitions)
  8. Section content (Issue Workflow Documentation)
  9. No issues write in promote (Issue Linking and Manual Closing)
  10. Least privilege (Workflow Triggers and Runner)
  11. No extra jobs (Out of Scope Boundaries)

## Final-State Facts (launch prompt, highest-ranked after tasks)

Where a snapshot said pending or open and the launch prompt says done, the final state is reported and the snapshot claim is not repeated as current.

- Implementation: PR #51 merged as `27f2cef` and promoted to `main` (promote run `44089524727`). `27f2cef` is reachable from `origin/main`; confirmed by `git merge-base --is-ancestor` at archive time.
- Closure PR #52 also carries the feature-form fix `5a830f2` (QA checklist and the agreed Definition of Done). Git check at archive time: `5a830f2` is on the closure branch but NOT reachable from `origin/main`, so the fix awaits the final promotion. This matches the launch prompt.
- Lifecycle 0.4 S4 is DONE: `main` renders the Bug and Feature forms with their labels (`type:bug`, `type:feature`). The verify snapshot listed 0.4 as pending for archive; that is superseded.
- Lifecycle 8.5 is DONE: summary comment on #5 (issuecomment-6949117933), then manual close; #5 moved to Done via the built-in "Item closed" workflow with no manual board move. The verify snapshot listed 8.5 as pending; that is superseded.
- Lifecycle 8.4 (this archive) is done by this report and the spec sync below.
- Live facts (launch prompt, consistent with `verify-report.md` LIVE rows): S1, S2, S3, A1-A3 and never-regress are proven; the S4 render on `main` was of the pre-fix feature form.

### Open Warnings at Close (final state)

1. The live render of the fixed Feature form (QA checklist and the 5 DoD items from `5a830f2`) follows the final promotion. It is not yet observed on `main`.
2. The Dependabot guard of `board-sync.yml` is verified statically only; no live Dependabot PR has exercised it.
3. The mutant kill counts (5/5 resolver, 10/10 board-sync) come from `apply-progress` and were not re-run by verify. RED and GREEN were re-verified.

## Spec Sync

Merge method: the `ci-pipeline` delta blocks were spliced into the living spec by a script that copies requirement bytes from the delta (no requirement text was typed or regenerated by the model). The new `issue-workflow` living spec is a shell copy of the delta file. Per-block equality was then checked by an independent checker that reads the bytes on disk.

| Domain | Action | Requirements | Scenarios |
|--------|--------|--------------|-----------|
| `ci-pipeline` | Updated | 17 → 17 (3 MODIFIED replaced in place, 0 ADDED, 0 REMOVED) | 52 → 54 (Issue Linking grows 3 → 5 scenarios; Workflow Triggers and Out of Scope keep their scenario counts, bodies change) |
| `issue-workflow` | Created | 0 → 10 (10 ADDED) | 0 → 28 |

Scenario counts were taken from the living files by `grep -c '#### Scenario:'`, not carried from the verify-report.

### ci-pipeline

- Purpose: KEPT as the living Purpose, unchanged. Decision: the delta's `### Purpose` is an explanatory note describing this change ("Delta: only ... changes"), not a replacement for the capability Purpose. The living Purpose remains the capability description. The launch prompt directed this, and it differs from the `security-quality-gates` precedent, which replaced Purpose.
- MODIFIED (replaced whole, `(Previously: ...)` line dropped):
  - Workflow Triggers and Runner (adds `board-sync.yml` to Least privilege; adds the `PROJECT_TOKEN` sentence)
  - Issue Linking and Manual Closing (5 scenarios; the Board Status sentence is added; the `(Previously: ...)` note is dropped)
  - Out of Scope Boundaries (No extra jobs lists board sync)
- Untouched requirements (14), byte-identical to the pre-merge living spec: No Static Analysis in CI; Unit Test Job; Artifact Contract; Report Generation and Publishing; Sticky PR Comment; Single Required Check (ci-gate); PR Report Cleanup; Promotion Requires Green ci-gate; Documentation of CI and Admin Steps; CodeQL Analysis Workflow; Code Scanning Merge Protection; Secret Scan Job; Dependabot Configuration; Dependabot PR Report Guard.

### issue-workflow

- Created `openspec/specs/issue-workflow/spec.md` from the delta: `## Capability: issue-workflow`, `### Purpose` (the delta Purpose, which carries no `(Previously...)` note), `## ADDED Requirements` (the same heading used by the other living specs, e.g. `code-quality-gates`), and 10 requirements: Feature Issue Form; Bug Issue Form; Issue Template Configuration; Issue Number Resolution; Board Sync Transitions; Board Sync Guards and Permissions; Manual Board Transitions; Board Holds Issues Only; Admin Setup Steps; Issue Workflow Documentation.

### Merge Checks

- Requirement count: `ci-pipeline` 17 before, 17 after (PASS). `issue-workflow` 10 (PASS).
- MODIFIED blocks equal the delta block after normalization (`(Previously:` lines removed, trailing blank lines stripped): 3 of 3 MODIFIED match (PASS).
- Untouched blocks byte-identical to the pre-merge snapshot: 14 of 14 (PASS).
- Purpose of `ci-pipeline` identical to the pre-merge living Purpose (PASS).
- No `(Previously` notes in either living spec (PASS).
- Full-file diff of `ci-pipeline`, pre-merge vs post-merge: changes confined to the three MODIFIED blocks (lines 9, 22, 24, 181, 196-201, 217 of the pre file; the Issue Linking block gains two scenarios).
- `diff -r` readbacks (empty output means identical):

```text
$ diff -r <candidate ci-pipeline spec> openspec/specs/ci-pipeline/spec.md
(no output, exit 0)

$ diff -r openspec/changes/issue-forms-and-board-automation/specs/issue-workflow/spec.md openspec/specs/issue-workflow/spec.md
(no output, exit 0)
```

## Archive Move

- The change folder is moved with `git mv` to `openspec/changes/archive/2026-11-17-issue-forms-and-board-automation/`. This report is written into the change folder before the move, so it is included in the archived folder.
- The verbatim `diff -r` readback for the move is recorded in the phase result, because this file was written before the move and the readback compares the archived tree with the pre-move snapshot.
- Pre-existing untracked file `openspec/changes/archive/2026-11-17-pr-pipeline-with-reports/.gentle-ai-instance` is unrelated to this change. It is NOT staged or committed.

## Unresolved Contradictions

- The verify snapshot's statements that 0.4 (S4) and 8.5 were pending are superseded by the launch prompt's final-state facts (both DONE), corroborated by `main` rendering the forms and the `#5` close evidence in `verify-report.md`.
- The verify snapshot's "fix `5a830f2` is not on `main` yet" is still true at archive time (git check above). It is an open warning, not a contradiction.
- No other contradictions. Verdict, counts, critical findings and commit `ccfa537` agree across sources.

## Engram Mirror

Persisted to Engram with topic key `sdd/issue-forms-and-board-automation/archive-report` (project `libros-leidos`, type `architecture`, `capture_prompt: false`), per the hybrid instruction in the launch prompt.
