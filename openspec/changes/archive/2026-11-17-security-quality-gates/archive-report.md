# Archive Report: security-quality-gates

**Archived**: 2026-11-17
**Archive path**: `openspec/changes/archive/2026-11-17-security-quality-gates/`
**Issue**: #4
**Repository state at close**: HEAD `be192f5` (`docs(sdd): verify security-quality-gates`), branch `docs/4-close-security-gates`
**Artifact store**: openspec (per `gentle-ai sdd-status ... --json --instructions`, `artifactStore: openspec`, `archive: ready`, `nextRecommended: archive`). The launch prompt also asked for Engram persistence of this report (hybrid mirror, project `libros-leidos`).
**Intentional partial archive**: No. Proposal, both delta specs, design, tasks and verify-report were all present. `applyProgress` is `missing` in the openspec locator set; this is recorded, not a blocker, because the verify-report and the tasks artifact carry the apply evidence.

## Observation IDs

Engram observations read by this archive: none. Every artifact was read from its openspec locator.

Cited but not read by this archive (references only):
- Verify observation `#111` (named in the launch prompt as the verify-report Engram record).
- Apply-progress observation `#109` (cited inside `verify-report.md` as the TDD evidence source; not an artifact locator in this store).

## Artifacts Read

- `openspec/changes/security-quality-gates/proposal.md`
- `openspec/changes/security-quality-gates/specs/ci-pipeline/spec.md`
- `openspec/changes/security-quality-gates/specs/code-quality-gates/spec.md`
- `openspec/changes/security-quality-gates/design.md`
- `openspec/changes/security-quality-gates/tasks.md`
- `openspec/changes/security-quality-gates/verify-report.md`

## Gate and Task Completion

- Native status at archive time: `archive: ready`, `nextRecommended: archive`, `blockedReasons: []`, `remediationState.required: false`.
- Task completion (`tasks.md`): 36 `[x]`, 0 `[ ]`, 0 `[-]`. No stale checkboxes exist, so no archive-time reconciliation was performed.

## Verification at Close

Ranking (per `sdd-archive` Final-State Authority): the persisted tasks artifact first, then the launch prompt's explicit final-state facts, then the `verify-report` snapshot.

- `verify-report.md` (snapshot, written at verification time, evidence revision `sha256:d887bd59c04d9c580409ddaf6627a4a195c436f77a8ec6c521cac9b06ab4b440`, the report body names HEAD `a886b88`): verdict `pass` in the envelope, `PASS WITH WARNINGS` in the body, 0 blockers, 0 critical findings, requirements 13/13, scenarios 45/45.
- Launch-prompt final-state facts (highest-ranked source after tasks): verdict PASS WITH WARNINGS, 0 critical, envelope valid 13/45, commit `be192f5`. These agree with the snapshot. The snapshot's HEAD `a886b88` is superseded by the final HEAD `be192f5`, which is the verify-report commit itself.
- Test count carried from `verify-report.md` (no later test change reported by any higher-ranked source): `./gradlew :app:testDebugUnitTest --rerun` exit 0, 102 passed, 0 failed, 0 skipped. Build `./gradlew :app:assembleDebug` exit 0.
- Other checks recorded at verify time: `actionlint` clean, `shellcheck scripts/*.sh` clean, `scripts/test-secret-scan-range.sh` all cases passed, `scripts/test-kover-summary.sh`, `scripts/test-check-commit-msg.sh`, `lefthook validate` All good, detekt exit 0 with 17 baseline entries.

### Scenario Evidence (45 total)

- 30 scenarios rest on runtime, live, or harness evidence (LIVE or HARNESS in `verify-report.md`).
- 15 scenarios are user-accepted on static or analogous evidence (explicit chat acceptance, 2026-11-17, recorded in `verify-report.md`):
  1. Minimal permissions (CodeQL Analysis Workflow)
  2. Least privilege (Workflow Triggers and Runner)
  3. No lint jobs (No Static Analysis in CI)
  4. Retargeting documented (Dependabot Configuration)
  5. Admin steps documented (Documentation of CI and Admin Steps)
  6. What-runs-where table and risk note (Documentation of CI and Admin Steps)
  7. No extra jobs (Out of Scope Boundaries)
  8. Not repeated in CI (Detekt Gate With Baseline)
  9. README lists setup and commands (Documented Setup)
  10. Config reflects tooling (Documented Setup)
  11. Test failure (Single Required Check, analogous live evidence from #44)
  12. Report outage (Single Required Check, static)
  13. Push and schedule trigger (CodeQL Analysis Workflow; push observed, weekly cron pending)
  14. Bypassed hook is not caught in CI for quality (No Static Analysis in CI)
  15. Hook bypass is an accepted risk for quality only (Pre-commit Hook; quality half accepted, secret half live #44)

## Open Warnings at Close (final state)

These are the warnings that remain open at close. Source: launch prompt, consistent with `verify-report.md` WARNING section.

1. The weekly CodeQL `schedule` cron is unconfirmed until its first Monday run.
2. The Dependabot security-update retarget path (close and reopen against `dev`) is untested, because no alerts exist.
3. The RED and GREEN commits `e48d66a` and `5f3d653` were squashed into `16923d4` on `dev`. The TDD order rests on the apply-progress record and the passing harness, not on separately reachable commits.
4. 15 of 45 scenarios rest on user-accepted static or analogous evidence (listed above).

## Implementation and Admin State at Close

Source: launch prompt (final-state facts). Repository-visible confirmations noted.

- Implementation: PR #42 merged as `16923d4` and promoted to `main` (promote run `44061146160`).
- Admin: Dependabot alerts and security updates enabled (verify-report LIVE API A1 for the alerts and automated security fixes). Code-scanning rule present on ruleset `27200640` (verify-report LIVE API; the rule was added after the green `dev` analysis run `44056296882`).
- Probes: #43 (CodeQL High alert, blocked, closed unmerged), #44 (secret-scan blocked), #50 (docs-only PR, mergeable after CodeQL), plus local hook probes (verify-report).
- Dependabot PRs #45 to #49 are open against `dev`. Confirmed read-only via `gh pr list`.

## Follow-up Risk (not part of this change)

- Dependabot PR #46 (`build(deps): bump the kotlin-ksp group with 2 updates`) bumps `kotlin` from `2.3.20` to `2.4.21` and `ksp` from `2.3.11` to `2.3.14`. The Kotlin bump was confirmed in the PR diff (read-only `gh pr diff 46`). The launch prompt states that CodeQL's documented maximum Kotlin version is `2.4.20`, which would place #46 outside the documented range. That CodeQL limit was not verified in this archive. Treat as a follow-up risk: confirm CodeQL Kotlin extraction on #46 before merging it.

## Spec Sync

Merge method: the delta blocks were spliced into the living specs by a line-range script that copies bytes from the delta. No requirement text was typed or regenerated by the model. Per-block equality was then checked against the delta after normalization.

| Domain | Action | Requirements | Scenarios |
|--------|--------|--------------|-----------|
| `ci-pipeline` | Updated | 12 → 17 (5 MODIFIED replaced in place, 5 ADDED appended) | 32 → 52 |
| `code-quality-gates` | Updated | 8 → 9 (2 MODIFIED replaced in place, 1 ADDED appended) | 32 → 38 |

### ci-pipeline

- Purpose: the `### Purpose` paragraph was replaced with the delta's replacement Purpose text. The delta's `(Previously: ...)` and `(Dropped living scenarios: ...)` notes were not carried over.
- MODIFIED (replaced whole, `(Previously: ...)` lines dropped): Workflow Triggers and Runner; No Static Analysis in CI; Single Required Check (ci-gate); Documentation of CI and Admin Steps; Out of Scope Boundaries.
- ADDED (appended after the last living requirement, in delta order): CodeQL Analysis Workflow; Code Scanning Merge Protection; Secret Scan Job; Dependabot Configuration; Dependabot PR Report Guard.
- Untouched requirements (7): Unit Test Job; Artifact Contract; Report Generation and Publishing; Sticky PR Comment; PR Report Cleanup; Promotion Requires Green ci-gate; Issue Linking and Manual Closing. Byte-identical to the pre-merge living spec.

### code-quality-gates

- Purpose: replaced with the delta's replacement Purpose text (notes dropped).
- MODIFIED (replaced whole, `(Previously: ...)` lines dropped): Pre-commit Hook; Documented Setup.
- ADDED (appended after the last living requirement): Detekt Gate With Baseline.
- Untouched requirements (6): Formatting Gate; Android Lint Gate With Baseline; Compose Lint Checks (Conditional on Spike); Coverage Report; Commit-message Hook; Unit Tests Remain Green. Byte-identical to the pre-merge living spec.

### Merge Checks

- Requirement count: `ci-pipeline` pre 12 + 5 added = 17, post 17. `code-quality-gates` pre 8 + 1 added = 9, post 9. PASS.
- MODIFIED equal to the delta block after normalization (trailing blank lines stripped, `(Previously:` lines removed): all 7 MODIFIED blocks, PASS.
- ADDED equal to the delta block after normalization: all 6 ADDED blocks, PASS.
- Untouched requirements byte-identical to the pre-merge snapshot: 7 in `ci-pipeline`, 6 in `code-quality-gates`, 0 failures. PASS.
- Purpose equals the delta replacement Purpose (notes removed). PASS.
- No `(Previously:` or `(Dropped living` notice lines remain in either living spec. PASS.

## Archive Move

- The change folder was moved with `git mv` to `openspec/changes/archive/2026-11-17-security-quality-gates/`. This report was written into the change folder before the move, so it is included in the archived folder.
- The verbatim `diff -r` readback for the move and for the living-spec merge is recorded in the phase result, not in this file, because this file was written before the move.

## Unresolved Contradictions

None. The sources agree on verdict, critical count, scenario split, open warnings, and the final commit. The only difference is the snapshot HEAD (`a886b88`) versus the final HEAD (`be192f5`), recorded above.

## Engram Mirror

Persisted to Engram with topic key `sdd/security-quality-gates/archive-report` (project `libros-leidos`, type `architecture`, `capture_prompt: false`), per the hybrid instruction in the launch prompt.
