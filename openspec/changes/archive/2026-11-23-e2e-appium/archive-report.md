# Archive Report: e2e-appium

**Archived**: 2026-11-23
**Archive path**: `openspec/changes/archive/2026-11-23-e2e-appium/`
**Repository state at close**: branch `docs/7-close-e2e-appium`, based on `dev` fca9dd7. Changes are uncommitted in the working tree; delivery is owned by the orchestrator. No GitHub operation was performed by this phase.
**Artifact store**: openspec. Native `gentle-ai sdd-status e2e-appium --cwd . --json` reported `artifactStore: openspec`, `dependencies.archive: ready`, `nextRecommended: archive`, `blockedReasons: []`.
**Intentional partial archive**: No. Proposal, the three delta specs, design, tasks, apply-progress and verify-report are all present. `preproposal`, `exploration` and `research` were archived with the change as context.

## Task Completion Gate

`tasks.md` at close: 53/53 tasks checked, 0 unchecked (`- [ ]`). No stale-checkbox reconciliation was performed.

## Observation IDs

Engram observations read by this archive: none. Every artifact was read from its openspec locator, which is the store the dispatcher reported.

Engram observation written by this archive: `sdd/e2e-appium/archive-report` (type `architecture`, project `libros-leidos`, `capture_prompt: false`).

## Verification Outcome

`verify-report.md` (verify-time snapshot): verdict PASS WITH WARNINGS, `CRITICAL: None`. The archive was not blocked by verification. Items W1 to S3 are recorded below with their state at close, per the Final-State Authority rules.

## Final State at Close

Ranking used: (1) persisted `tasks.md`, (2) explicit final-state facts in the launch prompt, (3) `verify-report.md` and `apply-progress.md` snapshots. Numbers come from the highest-ranked source that covers them.

| Item | Verify-time claim (snapshot) | Final state at close | Source of final state |
|---|---|---|---|
| W1 Robolectric android-all download flake | Open WARNING: `unit-tests` failed on dev push run 45170275513 (a388c3d) with `MavenArtifactFetcher` `IOException`; not reproduced on the PR runs | Did not recur in later runs. Kept as a follow-up: consider Robolectric offline mode or an android-all cache if it repeats. | Launch prompt (rank 2) |
| W2 Hosted dev run for the final commit | Open WARNING: run 45180858808 (fca9dd7) had `e2e-tests` in progress | RESOLVED. Run 45180858808 finished success on all jobs: `unit-tests`, `e2e-tests`, `backend-tests`, `secret-scan`, `contract-tests`, `ci-gate`, `publish-image`, `report`. The first AVD cache was saved. | Launch prompt (rank 2) |
| W3 e2e hook scenarios are manual and recorded | Open WARNING: the three `e2e-typecheck` scenarios rest on recorded `lefthook run pre-commit` runs (apply-progress 9.1 to 9.3) | Remains as recorded. Accepted; no hook-runner test, same pattern as the existing hooks. | Verify-report (rank 3); launch prompt (rank 2) confirms the item stays as recorded |
| W4 Known and accepted items | Report only | Remain as recorded: (1) SP2 half-star bounds limitation, Page Object uses a geometry tap; (2) `size:exception` accepted for PR1 792 and PR2 862 authored lines, `package-lock.json` treated as generated; (3) ledger budget reset for PR2 approved; (4) `e2e-tests` intentionally non-gating, so the release no-test-tags proof is also non-gating. | Launch prompt (rank 2) |
| S1 `apply-progress.md` PR1 header shows `[ ] 3.6` | Suggestion: align with `tasks.md` at archive | FIXED in this archive. `apply-progress.md` line 9 now reads `[x] 3.6` with the accepted-limitation wording, matching `tasks.md` 3.6. | This archive (edit to the source file before the move) |
| S2 PR1 TDD table uses plain values, not glyphs | Suggestion | Open follow-up. Content is complete; consistency only. | Verify-report (rank 3) |
| S3 `structure.test.ts` allows selector markers in `src/support` | Suggestion | Open follow-up. Today `src/support` has no selectors; tightening `ALLOWED_DIRS` to `pages` would match the spec literally. | Verify-report (rank 3) |
| Manual QA in dev (fca9dd7) | Not in verify-report | Passed: local e2e 2 passing; UI unchanged; delete dialog exposes ids; Cancel keeps the book; dev Allure report shows 153 tests including the `book library` suite. | Launch prompt (rank 2) |
| Promotion | Not in verify-report | Promotion run 45184092586 succeeded: `check-image`, fast-forward `main`, `retag-image`. `main` == `dev` == fca9dd7. GHCR tag `sha-fca9dd7`; `dev` and `staging` share digest `sha256:399eb66c…710a`. | Launch prompt (rank 2). Not re-queried by this phase. |
| Issue and board | Not in verify-report | Issue #7 closed as completed; board status Done. PRs: #75 plan (c526f51), #76 PR1 (a388c3d), #77 PR2 (a3e2064), #78 PR3 (fca9dd7). | Launch prompt (rank 2). Not re-queried by this phase. |
| Test counts | app 126, release 2, node 20 (verify-time) | The dev Allure report shows 153 tests (launch prompt, rank 2). The verify-time counts are carried as snapshot values only. | Launch prompt (rank 2); `verify-report.md` (rank 3) |

## Specs Synced

| Domain | Action | Details |
|---|---|---|
| `e2e-testing` | Created `openspec/specs/e2e-testing/spec.md` | Full spec copied mechanically from the delta (8 requirements, 21 scenarios). The delta had no delta headers, so no merge was needed. |
| `ci-pipeline` | Updated | 1 ADDED (E2E Test Job); 5 MODIFIED (Workflow Triggers and Runner, Artifact Contract, Report Generation and Publishing, Out of Scope Boundaries, Dependabot Configuration); 0 REMOVED. |
| `code-quality-gates` | Updated | 1 MODIFIED (Pre-commit Hook); 0 ADDED, 0 REMOVED. |

Merge notes:
- MODIFIED blocks replaced the matching main requirement in full. The delta's `(Previously: ...)` notes were dropped, since no main spec keeps Previously notes (same as the backend-skeleton precedent).
- The ADDED block was appended after the existing requirements. Requirements not named in the delta are unchanged.
- The `## ADDED Requirements` heading already present in the main `ci-pipeline` and `code-quality-gates` specs was kept as-is.
- Block-level readback: each delta block (after the Previously-note removal) was compared with its main requirement and matched verbatim: 6 blocks in `ci-pipeline`, 1 block in `code-quality-gates`.

## Main-Spec Wording Check Against the Code

The main specs were checked against the code as built (`.github/workflows/ci.yml`, `lefthook.yml`, `.github/dependabot.yml`, `e2e/package.json`, `app/src/{debug,release,main}`). No divergence required a wording fix beyond the delta. Confirmed:
- `e2e-tests` has no `needs`, runs on `ubuntu-24.04`, has `permissions: contents: read` only, references no `secrets.*`, runs `npm ci` with `.nvmrc`, builds `:app:assembleDebug`, and uploads `allure-results-e2e` with `retention-days: 7` under `if: always()`.
- `ci-gate` needs only `unit-tests`, `backend-tests`, `contract-tests` and `secret-scan`, so `e2e-tests` is non-gating.
- `report` needs `unit-tests`, `backend-tests`, `contract-tests` and `e2e-tests`, runs with `if: !cancelled()`, and detects `allure-results/*-result.json` as well as JUnit XML.
- `ci.yml` jobs are `unit-tests`, `backend-tests`, `contract-tests`, `e2e-tests`, `secret-scan`, `report`, `ci-gate`, `publish-image`. This matches the Out of Scope delta.
- Dependabot has an `npm` entry for `/e2e`.
- `lefthook.yml` pre-commit jobs are spotless, android-lint, detekt, gitleaks, actionlint, shellcheck and e2e-typecheck, matching the Pre-commit Hook delta.
- `e2e/package.json` pins `appium` 3.9.1, `appium-uiautomator2-driver` 8.7.3 and the WebdriverIO packages at 10.2.0 with no ranges. `.nvmrc` is 24.22.3.
- The `E2E_APK_PATH` override, the failure-only screenshot policy and the debug-only `e2eRoot` are present in code.

The archived delta specs are unchanged. They remain the audit trail.

## Conventions Check (`compose-design` project-conventions, "Test tags")

`.claude/skills/compose-design/references/project-conventions.md`, lines 20 to 24: checked against the implementation and found accurate. No change was made.
- `Modifier.e2eRoot()` is defined in `app/src/debug/.../ui/E2eRoot.kt` (`semantics { testTagsAsResourceId = true }`) and in `app/src/release/.../ui/E2eRoot.kt` (identity).
- It is applied on the activity `Scaffold` in `MainActivity.kt` and on the delete `AlertDialog` in `BookListScreen.kt` (`Modifier.e2eRoot().testTag(BookListTags.DELETE_DIALOG)`).
- `src/main` calls only the `e2eRoot` function and never references the flag. The flag is referenced only in `src/debug`.
- Tag format matches the example names: `book_list_item_<id>`, `book_edit_save_button`, `book_list_empty_state`.

## Archive Contents

Moved to `openspec/changes/archive/2026-11-23-e2e-appium/`:
- Tracked files moved with `git mv` (10): `apply-progress.md` (edited, see S1), `design.md`, `exploration.md`, `preproposal.md`, `proposal.md`, `research.md`, `tasks.md`, `specs/ci-pipeline/spec.md`, `specs/code-quality-gates/spec.md`, `specs/e2e-testing/spec.md`.
- Untracked files moved with plain `mv` (2): `verify-report.md`, `.gentle-ai-instance`. The marker was moved with the folder, as the launch prompt allowed. It is not staged in git. The untracked `.gentle-ai-instance` files in other archive folders were not touched.

## Mechanical Readback

- A recursive snapshot of the source folder was taken after the S1 edit and before any move. The `diff -r` readback of the snapshot against the archive folder returned no output (exit 0).
- The e2e-testing main spec: `diff -r` of the delta source against `openspec/specs/e2e-testing/spec.md` returned no output (exit 0).
- The ci-pipeline and code-quality-gates main specs are merges, not byte copies. Readback is the block-level check above.

## Problems and Open Items

- No CRITICAL issues. No blocked steps.
- The verify-report verdict and its "W1 to W4 open" language are verify-time snapshots. They are superseded by the final-state table above. The final-state facts come from the launch prompt. This phase did not re-query GitHub, per the instruction not to touch GitHub.
- Open follow-ups: W1 (monitor the Robolectric download flake), S2, S3. Accepted items W3 and W4 stay as recorded.
- Changes are uncommitted: archive moves, the two edited main specs, the new `e2e-testing` main spec, and the `apply-progress.md` S1 edit. Delivery is left to the orchestrator.
