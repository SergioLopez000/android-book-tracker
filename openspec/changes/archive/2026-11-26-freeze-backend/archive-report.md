# Archive Report: freeze-backend

**Archived**: 2026-11-26
**Archive path**: `openspec/changes/archive/2026-11-26-freeze-backend/`
**Issue**: #91 (stays open; the owner adds `qa:approved`, posts a summary comment and closes it, per the README workflow)
**Implementation PR**: #92
**Repository state at close**: branch `docs/91-close-freeze-backend`, HEAD `4979faf3`. Changes are uncommitted in the working tree; delivery is owned by the orchestrator. No GitHub operation was performed by archive.
**Artifact store**: hybrid (openspec primary, Engram topic `sdd/freeze-backend/archive-report`). Native status (`gentle-ai sdd-status freeze-backend`) reported `archive: ready`, `next: archive`, `tasks: 42/42 complete` before the archive ran.
**Intentional partial archive**: No. Proposal, the delta spec (`ci-pipeline`), design, tasks, apply-progress and verify-report are all present. `backend-api` and `code-quality-gates` have no delta for this change (see Specs Synced).

## Task Completion Gate

`tasks.md` at close (read from the persisted openspec artifact, which outranks snapshots): 42/42 tasks checked, 0 unchecked (`- [ ]`). No stale-checkbox reconciliation was performed.

## Final State at Close

Final-state facts come from the orchestrator launch prompt (rank 2) and the persisted tasks artifact (rank 1). `verify-report` and `apply-progress` are intermediate snapshots (rank 3) and are cited only with their time of writing.

- **Implementation**: PR #92 squash-merged into `dev` as `4979faf`. The branch commit before the squash is `28b7671`. The revert target to reactivate the backend CI is `4979faf` (the squash commit).
- **CI**: PR #92 all checks green. Dev run `45784298363` green.
- **Promotion**: run `45784872454` succeeded; its only job was "Fast-forward main". `main` == `dev` == `4979faf`. Staging deployment is at `4979faf`; the owner approved the environment.
- **Dependabot**: PRs #66, #67 and #69 closed with a comment linking #91/#92 (D5). PR #84 is open and deferred (D4). D6 (ongoing watch for Dependabot activity) is ongoing; none received as of 2026-11-26.
- **Verification**: `pass_with_warnings` per `verify-report` (Engram #256, written 2026-11-26 09:43, at verification time). 16/16 requirements, 52/52 scenarios, 42/42 tasks, 0 CRITICAL. `evidence_revision` `sha256:3af52c07e06e2e5882cbc70efbadcd480b3782496a76cd3e19589d47d9154159`.
- **Application tests**: 126 passed, 0 failed, per `apply-progress` (Engram #255). The verify run used a cached Gradle build (see Warnings), so the 126 count is the prior re-run, not a fresh execution in verify.

## Warnings Carried From verify-report (at verification time)

Recorded as they stood when `verify-report` was written. None is CRITICAL. Status at close is noted where the launch prompt or the archive state changed it.

1. **TDD evidence format**: `apply-progress` documents RED/GREEN/triangulation in prose per unit, not as a formal "TDD Cycle Evidence" table. Open at close; documentation-level.
2. **Living spec not yet merged**: `verify-report` noted that `openspec/specs/ci-pipeline/spec.md` did not yet contain the delta. Resolved by this archive: "Backend Frozen in CI" is now merged into the living spec.
3. **Failure-path scenarios not exercised live**: report outage, test-failure upload and fork-PR paths of report/ci-gate were verified by static inspection and `scripts/test-e2e-ci-config.sh` only. Open at close; those paths are unchanged by the delta.
4. **Cached Gradle run**: the Gradle build was up-to-date (cached) during verify, so the 126-test count is the prior apply-progress re-run. Open at close.

Suggestion carried: `verify-report` suggested citing only `4979faf` as the revert target. This report does so for the reactivation path (see Final State).

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| `ci-pipeline` | Updated | 1 added (Backend Frozen in CI); 10 modified (Workflow Triggers and Runner; Unit Test Job; Artifact Contract; Report Generation and Publishing; Single Required Check (ci-gate); Out of Scope Boundaries; CodeQL Analysis Workflow; Dependabot Configuration; E2E Test Job; Static Contract Checks Self-Tested and Run Locally); 5 removed (Backend Test Job; Contract Test Job With PIT; GHCR Image Publication; Promote Retags the Same Image Digest; No Secret-Like Dockerfile Variables). Main goes from 29 to 25 requirements. |
| `backend-api` | Unchanged by archive | The Purpose sentence "Frozen since 2026-11-26 (#91) ..." was applied on the branch (task P.5), was already present once when archive ran, and was not added again. No requirement delta. |
| `code-quality-gates` | Unchanged | No delta for this change. |

Merge method: the delta's ADDED block was appended to the living spec; each MODIFIED block replaced the same-named requirement in place; each REMOVED block was deleted by name. The delta's "Notes for archive" section was not copied. The `(Previously: ...)` annotations were kept verbatim, matching the house style of the living `ci-pipeline` spec (which still carries the `(Previously: ...)` lines from `deployment-secrets`).

Readback: every ADDED and MODIFIED block was verified as byte-identical (after trailing-newline normalization) to its delta source; every REMOVED name is absent; all 14 untouched requirement blocks are byte-identical to their pre-merge text (the last one, "Secrets Section in README", differs only by the blank separator line inserted before the appended requirement); the requirement name set equals before minus removed plus added; no duplicate names.

Gap recorded: the REMOVED blocks carry `(Reason: ...)` notes but no explicit `(Migration: ...)` notes, which the archive skill asks for. The reactivation path is stated in the Reason text (revert the squash commit `4979faf`), and the removed requirement text is recoverable from git history of `openspec/specs/ci-pipeline/spec.md` before this archive. Accepted per the orchestrator's explicit instruction to delete the blocks by name.

## Archive Contents

- `proposal.md` (Engram `sdd/freeze-backend/proposal`, #250)
- `specs/ci-pipeline/spec.md` (Engram `sdd/freeze-backend/spec`, #251)
- `design.md` (Engram `sdd/freeze-backend/design`, #252; revision 2)
- `tasks.md` (Engram `sdd/freeze-backend/tasks`, #254; 42/42 complete in the openspec artifact)
- `apply-progress.md` (Engram `sdd/freeze-backend/apply-progress`, #255; intermediate snapshot)
- `verify-report.md` (Engram `sdd/freeze-backend/verify-report`, #256; intermediate snapshot, `pass_with_warnings`)
- `exploration.md` (Engram `sdd/freeze-backend/explore`, #247)
- `research.md` (Engram `sdd/freeze-backend/research`, #249; outcome `partial`)
- `preproposal.md` (no Engram observation located for it; openspec file only)
- `archive-report.md` (this file; Engram `sdd/freeze-backend/archive-report`)
- `.gentle-ai-instance` (untracked, not committed; moved with plain `mv`)

## Observation IDs

Engram observations related to this change: explore #247, research #249, proposal #250, spec #251, design #252, tasks #254, apply-progress #255, verify-report #256. Decisions: #248 (PR #92 merged, 2026-11-26), #253 (gate amendments: byte-identity check is `git diff --stat origin/dev...HEAD -- backend/ app/`).

The openspec files were the locators actually read for content. Engram IDs for artifacts are listed for traceability; `verify-report` #256 was the source of the evidence revision.

## Mechanical Readback

- Spec merge: verified against the delta source as described under Specs Synced.
- Archive move: `diff -r` of the pre-move snapshot against `openspec/changes/archive/2026-11-26-freeze-backend/` (archive-report.md excluded, as it did not exist in the source) returned empty output, exit 0.
- Source removed: `openspec/changes/freeze-backend/` no longer exists.
