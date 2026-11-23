# Archive Report: backend-skeleton

**Archived**: 2026-11-23
**Archive path**: `openspec/changes/archive/2026-11-23-backend-skeleton/`
**Repository state at close**: branch `docs/6-close-backend-skeleton`, based on `dev` 4fb7d39. Changes are uncommitted; delivery is owned by the orchestrator.
**Artifact store**: openspec. Native `gentle-ai sdd-status backend-skeleton --cwd . --json` reported `artifactStore: openspec`, `dependencies.archive: ready`, `nextRecommended: archive`, `blockedReasons: []`. The session preflight named store `hybrid`; the filesystem was the source of truth and the archive report was also persisted to Engram as requested by the launch prompt.
**Intentional partial archive**: No. Proposal, the three delta specs, design, tasks, apply-progress and verify-report are all present. `preproposal`, `exploration` and `research` were archived with the change as context.

## Task Completion Gate

`tasks.md` at close: 55/55 tasks checked, 0 unchecked (`- [ ]`). No stale-checkbox reconciliation was performed.

## Observation IDs

Engram observations read by this archive: none. Every artifact was read from its openspec locator, which is the store the dispatcher reported.

Engram observation written by this archive: `sdd/backend-skeleton/archive-report` (type `architecture`, project `libros-leidos`, `capture_prompt: false`).

## Verification Outcome

`verify-report.md` (written 2026-11-20 12:45): verdict PASS WITH WARNINGS, `CRITICAL: None`. The archive was not blocked by verification. The W-items below record their state at close, per the Final-State Authority rules.

## Final State at Close

Ranking used: (1) persisted `tasks.md`, (2) explicit final-state facts in the launch prompt, (3) `verify-report.md` and `apply-progress.md` snapshots. Numbers come from the highest-ranked source that covers them.

| Item | Verify-time claim (snapshot, 2026-11-20) | Final state at close | Source of final state |
|---|---|---|---|
| W1 TDD evidence format | Open WARNING: no formal TDD Cycle Evidence table in `apply-progress.md` | RESOLVED 2026-11-23. A formal "TDD Cycle Evidence (all PRs)" table and Test Summary were appended to `apply-progress.md`. The change is an uncommitted working-tree edit, kept as-is and archived with the file. | Launch prompt (rank 2); archived `apply-progress.md` (34 added lines) |
| W2 Spec/implementation mismatch | Open WARNING: spec said six `ci.yml` jobs and GHCR publish in "other workflows" | RESOLVED by this archive's spec sync (see below). `publish-image` is a job in `.github/workflows/ci.yml`. | This archive (rank 2 and repository evidence) |
| W3 Promotion path unproven | Open WARNING: `promote.yml` check-image, promote and retag-image had not run on a real promotion; 2 scenarios pending | RESOLVED 2026-11-23. Promote run https://github.com/SergioLopez000/android-book-tracker/actions/runs/45142335095 succeeded: "Require the backend image" (check-image), "Fast-forward main" (promote, staging environment approved by the user) and "Retag image as staging" (retag-image) all success. `main` == `dev` == 4fb7d39. GHCR tag `sha-4fb7d3905e412ad6f0f81e38077ac8166eed6096`; `dev` and `staging` share digest `sha256:4d2f8f0e7504e6ee12fe678588611e555ee4127ae77b2a06b26cfe9f87d9da66`. | Launch prompt (rank 2). Not re-queried by this phase; no GitHub operation was performed. |
| Scenario coverage | 99/101 covered; 2 awaiting promotion evidence | 101/101 with evidence | Launch prompt (rank 2) |
| Manual QA in dev | Not in verify-report | 2026-11-23 on the `backend:dev` image: Flyway V1 applied, `owner_id` NOT NULL; `/actuator/health` 200 `{"status":"UP"}`; 401 with empty body for GET/POST `/api/books`, unknown path, `/actuator/env`, `/actuator/info`, `/actuator/beans`; image runs as `nonroot`, only OCI source label, no secret env. | Launch prompt (rank 2) |
| Dev CI | Verify-time envelope: suites green | Dev CI run 44728827189: all 7 jobs green. PIT 95% (52/55 mutants killed); backend coverage line 98.5, branch 91.9. | Launch prompt (rank 2) |
| Issue #6 | Open at verify time | Labeled `qa:approved`, summary comment posted, closed as completed, board status Done. PRs #60 to #65 merged into `dev`. | Launch prompt (rank 2) |
| Test counts | Backend 58 + 6 + 25; app 102 (verify-report) | Not restated by the launch prompt; the verify-report counts are carried as verify-time values only. | `verify-report.md` (rank 3, snapshot) |

Suggestions S1 to S7 from `verify-report.md` remain open follow-ups, unchanged:
- S1: confirm `backend/src/main` files in the CodeQL code-scanning UI after the next hosted run.
- S2: no automated regression test for the PIT "score below threshold" gate.
- S3: PIT 95% has 3 accepted survivors (`getValue` of inline value classes `Rating`, `OwnerId`, `BookId`, equivalent mutants).
- S4: `SecuritySliceTest` asserts anonymous 401 for `env` and `info` but not `beans`.
- S5: lefthook pre-commit scenarios verified by config inspection, not by a hook-runner test.
- S6: `contract-tests` re-runs `test` and `integrationTest` through Kover tasks; consider `koverXmlReport` scoped to the contract suite.
- S7: open design questions: unbounded `listBooks` per owner (#10) and `owner_id` format once #26 lands.

## Specs Synced

| Domain | Action | Details |
|---|---|---|
| `backend-api` | Created `openspec/specs/backend-api/spec.md` | Full spec copied from the delta (11 requirements, 31 scenarios). |
| `ci-pipeline` | Updated | 4 ADDED (Backend Test Job, Contract Test Job With PIT, GHCR Image Publication, Promote Retags the Same Image Digest); 8 MODIFIED (Workflow Triggers and Runner, Unit Test Job, Artifact Contract, Report Generation and Publishing, Single Required Check (ci-gate), Out of Scope Boundaries, CodeQL Analysis Workflow, Dependabot Configuration); 0 REMOVED. Then W2 wording fix (below). |
| `code-quality-gates` | Updated | 4 MODIFIED (Formatting Gate, Coverage Report, Pre-commit Hook, Detekt Gate With Baseline); 0 ADDED, 0 REMOVED. |

Merge notes:
- MODIFIED blocks replaced the matching main requirement in full. The delta's `(Previously: ...)` note in "Out of Scope Boundaries" was dropped, since no main spec keeps Previously notes.
- ADDED blocks were appended after the existing requirements. Requirements not named in the delta are unchanged, verified by the merge output diff (only the replaced blocks changed).
- The `## ADDED Requirements` heading already present in the main `ci-pipeline` and `code-quality-gates` specs was kept as-is.

## W2 Wording (main spec only)

The delta spec was NOT edited, because archived changes are an audit trail. The fix lives in `openspec/specs/ci-pipeline/spec.md`:

1. "Out of Scope Boundaries > Only expected jobs" now reads: `ci.yml` has only `unit-tests`, `backend-tests`, `contract-tests`, `secret-scan`, `report`, `ci-gate`, `publish-image`, and the other workflows are only CodeQL analysis, cleanup, promote, board sync. The GHCR publish is no longer listed under "other workflows".
2. "GHCR Image Publication" now opens with: "The `publish-image` job in `.github/workflows/ci.yml` MUST build the backend jar ...". Previously: "A workflow MUST build ...".

Job names were checked against `.github/workflows/ci.yml`: `unit-tests`, `backend-tests`, `contract-tests`, `secret-scan`, `report`, `ci-gate`, `publish-image`. The `publish-image` job has `needs: ci-gate` and runs only on `push` to `dev`.

## Archive Contents

Moved to `openspec/changes/archive/2026-11-23-backend-skeleton/`:
- `proposal.md`, `design.md`, `preproposal.md`, `exploration.md`, `research.md`, `tasks.md` (55/55), `apply-progress.md` (with the W1 TDD evidence addition), `verify-report.md`
- `specs/backend-api/spec.md`, `specs/ci-pipeline/spec.md`, `specs/code-quality-gates/spec.md` (delta specs, unchanged)

Not archived: `.gentle-ai-instance` (an untracked 37-byte marker, content `sdd-32b648552d9c57e8af139670b1b71d60`). It was moved out of the tree to the session scratch area and is not in the archive. The untracked `openspec/changes/archive/2026-11-17-pr-pipeline-with-reports/.gentle-ai-instance` was not touched.

## Mechanical Readback

- Folder move: `git mv` for the tracked files and the `specs/` directory; plain `mv` for the untracked `verify-report.md`; source directory removed after the marker was held out. Readback against a pre-move snapshot, excluding the held-out marker, returned an empty `diff -r`.
- Spec merges are not byte copies, so the readback is a requirement-block check: every delta requirement (`ci-pipeline` 12, `code-quality-gates` 4) matches the main spec verbatim after the Previously-note normalization, and `diff` against the pre-merge snapshot shows changes only inside the named blocks.
- `backend-api` main spec: `diff -r` of the delta source against `openspec/specs/backend-api/spec.md` returned an empty diff.

## Repository Diff at Close

From `git diff HEAD --stat -M` (tracked, including the staged renames) and line counts of untracked files:

```
 .../2026-11-23-backend-skeleton}/apply-progress.md |  34 +++++
 .../2026-11-23-backend-skeleton}/design.md         |   0
 .../2026-11-23-backend-skeleton}/exploration.md    |   0
 .../2026-11-23-backend-skeleton}/preproposal.md    |   0
 .../2026-11-23-backend-skeleton}/proposal.md       |   0
 .../2026-11-23-backend-skeleton}/research.md       |   0
 .../specs/backend-api/spec.md                      |   0
 .../specs/ci-pipeline/spec.md                      |   0
 .../specs/code-quality-gates/spec.md               |   0
 .../2026-11-23-backend-skeleton}/tasks.md          |   0
 openspec/specs/ci-pipeline/spec.md                 | 147 ++++++++++++++++++---
 openspec/specs/code-quality-gates/spec.md          |  33 ++++-
 12 files changed, 192 insertions(+), 22 deletions(-)
```

Untracked, new in this archive:
- `openspec/specs/backend-api/spec.md`: 206 lines
- `openspec/changes/archive/2026-11-23-backend-skeleton/verify-report.md`: 291 lines
- `openspec/changes/archive/2026-11-23-backend-skeleton/archive-report.md`: this file

## Problems and Open Items

- No CRITICAL issues; no blocked steps.
- The verify-report verdict and its "99/101" and "fix or accept W1 to W3" statements are verify-time snapshots. They are superseded by the final-state facts above. The final-state facts (W3 promote run, manual QA, issue #6) come from the launch prompt; this phase did not re-query GitHub, per the instruction not to touch GitHub.
- Store mismatch: native status reported `openspec`; the session preflight named `hybrid`. Filesystem was used as the source of truth, and Engram received the archive report as the hybrid mirror.
- Changes are uncommitted: the archive moves, the two main specs, the new `backend-api` spec, and the `apply-progress.md` W1 edit. Delivery is left to the orchestrator.
