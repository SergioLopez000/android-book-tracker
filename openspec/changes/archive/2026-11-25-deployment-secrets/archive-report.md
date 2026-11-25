# Archive Report: deployment-secrets

**Archived**: 2026-11-25
**Archive path**: `openspec/changes/archive/2026-11-25-deployment-secrets/`
**Issue**: #54 (stays open; owner closes it manually after the PR, per the README workflow)
**Repository state at close**: branch `docs/54-close-deployment-secrets`, based on HEAD `cf9e266`. Changes are uncommitted in the working tree; delivery is owned by the orchestrator. No GitHub operation was performed by archive.
**Artifact store**: hybrid (openspec primary, Engram topic `sdd/deployment-secrets/archive-report`). Native status reported `dependencies.archive: ready` and `nextRecommended: archive` per the launch prompt.
**Intentional partial archive**: No. Proposal, the three delta specs, design, tasks, apply-progress and verify-report are all present.

## Task Completion Gate

`tasks.md` at close (read from the persisted openspec artifact, which outranks snapshots): 67/67 tasks checked, 0 unchecked (`- [ ]`). Tasks 9.4, V.3 and V.5 are marked `[x]` with an N/A note (2026-11-25). No stale-checkbox reconciliation was performed.

## Final State at Close

Final-state facts come from the orchestrator launch prompt and the persisted tasks artifact. `verify-report` and `apply-progress` are intermediate snapshots, so their statements are attributed to their time of writing.

- **Implementation**: merged to `dev` via PR #81 (squash `55159f7`). Promoted to `main`/`staging` by run `45595404774` (V.2 pass).
- **Token expiry, first run on `main`** (run `45597007631`): state `ok`, 82 days remaining. `PROJECT_TOKEN` expires 2027-02-15, recorded in `docs/secrets.md`. Post-extraction run on the branch (`45600867420`): state `ok`.
- **Remediations on this branch** (owner-approved):
  - `4bf191e`: notify/read logic in scripts with the fake-gh harness, Google Books spec wording, inventory consumers.
  - `cf9e266`: assertions for the reminder runbook link, the missing-secret notice, and the Google Books row.
- **Verification**: third pass admitted per Engram `sdd/deployment-secrets/verify-report` (observation #229): `pass_with_warnings`, 15/15 requirements, 57/57 scenarios, 0 CRITICAL, `evidence_revision` `sha256:85aa3b3d72af781f3f21f3e92849ce5d16301c4ae605237cbb95fed1ad663fe4`. The earlier passes (49/57, 54/57) were refused by the validator and led to the remediations above. Those counts are superseded and are not reported as current.
- **N/A items**: 9.4 and V.3 are N/A because no Google Books client key exists; the server key comes with #29. V.5 is N/A because the first run returned `ok`, so no reminder issue exists yet.

## Open Follow-Ups (carried from verify-report #229, at verification time)

The verify-report WARNING and SUGGESTION items below are carried forward as follow-ups. They are not blocking.

1. **WARNING**: The notify path is proven only against the fake gh/curl harness and actionlint. It has not run against GitHub. The first real reminder is expected around 2027-02-01.
2. **WARNING**: The image smoke test (`scripts/test-image-smoke.sh`) is local-only, not in CI.
3. **SUGGESTION**: Fence or truncate the raw, unparseable expiry header value echoed in the warning issue body (untrusted Markdown; no shell risk).
4. **SUGGESTION**: Two unrelated untracked `.gentle-ai-instance` files exist under `openspec/changes/archive/` (`2026-11-17-pr-pipeline-with-reports`, `2026-11-23-e2e-appium`). They are left out of commits and were not touched by this archive.

## Specs Synced

Delta specs merged into the living specs under `openspec/specs/`. Each merged block was checked byte-for-byte against its delta source (see Mechanical Readback).

| Domain | Action | Details |
|--------|--------|---------|
| `backend-api` | Updated | 2 added (Database Credentials From Environment Only; Packaged Artifact Free of Credentials), 1 modified (Hardened Container Image). Main now has 13 requirements. |
| `ci-pipeline` | Updated | 7 added (Workflow Permissions Declared; Secrets Allow-List; No Secret-Like Dockerfile Variables; Static Contract Checks Self-Tested and Run Locally; Secret Inventory Document; Inventory Drift Guard; Secrets Section in README), 2 modified (Workflow Triggers and Runner; Out of Scope Boundaries). Main now has 29 requirements. |
| `issue-workflow` | Updated | 2 added (PROJECT_TOKEN Expiry Reminder Workflow; Expiry Header Parsing Is Defensive), 1 modified (Admin Setup Steps). Main now has 12 requirements. |

Merge method: ADDED blocks were appended to each main spec; MODIFIED blocks replaced the same-named main requirement in place. No REMOVED or RENAMED sections exist in the deltas. The removed lines in the diff are exactly the prior text of the four MODIFIED requirements. Other requirements were preserved. No duplicate requirement names exist in the merged mains.

## Archive Contents

- `proposal.md` (Engram `sdd/deployment-secrets/proposal`, #214)
- `specs/backend-api/spec.md`, `specs/ci-pipeline/spec.md`, `specs/issue-workflow/spec.md` (Engram `sdd/deployment-secrets/spec`, #215)
- `design.md` (Engram `sdd/deployment-secrets/design`, #216)
- `tasks.md` (Engram `sdd/deployment-secrets/tasks`, #218; 67/67 complete)
- `apply-progress.md` (Engram `sdd/deployment-secrets/apply-progress`, #220; intermediate snapshot)
- `verify-report.md` (Engram `sdd/deployment-secrets/verify-report`, #229; admitted third pass)
- `exploration.md` (Engram `sdd/deployment-secrets/explore`, #210)
- `archive-report.md` (this file; Engram `sdd/deployment-secrets/archive-report`)

## Observation IDs

Engram observations read by this archive for traceability: proposal #214, spec #215, design #216, tasks #218, verify-report #229. The openspec files were the locators actually read for content. Observation #220 (apply-progress) and #210 (explore) were located by search and were not used for final-state facts.

Engram observation written by this archive: `sdd/deployment-secrets/archive-report` (type `architecture`, project `libros-leidos`).

## Mechanical Readback

Archive move (snapshot taken before `git mv`; `diff -r` source snapshot vs. `openspec/changes/archive/2026-11-25-deployment-secrets`): empty output, exit 0. The `archive-report.md` file is additive and was written after the readback, so it is excluded from that comparison.

Spec merge: each ADDED and MODIFIED block (15 total) was found verbatim in its target main spec, with zero misses. Removed lines in `git diff` are only the prior text of the four MODIFIED requirements.

## Problems and Open Items

- No unresolved contradictions between the launch prompt final-state facts and the repository evidence.
- The `verify-report.md` file is still untracked in git (it was moved with the folder but not staged). The orchestrator stages it with the rest of the archive commit.
- Issue #54 stays open until the owner closes it manually.
