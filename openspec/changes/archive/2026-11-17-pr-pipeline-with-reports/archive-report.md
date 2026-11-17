# Archive Report: pr-pipeline-with-reports

**Archived**: 2026-11-17
**Archive path**: `openspec/changes/archive/2026-11-17-pr-pipeline-with-reports/`
**Repository state at close**: HEAD `5c92454`, branch `docs/3-close-pipeline`
**Artifact store**: openspec (per `gentle-ai sdd-status`; the launch preflight named hybrid, and the dispatcher's openspec answer was treated as authoritative)
**Intentional partial archive**: No. All proposal, spec, design, tasks and verify artifacts were present.

## Observation IDs Read

None. Every artifact was read from its openspec locator. No Engram observation was read for this archive.

Artifacts read:
- `openspec/changes/pr-pipeline-with-reports/proposal.md`
- `openspec/changes/pr-pipeline-with-reports/specs/ci-pipeline/spec.md`
- `openspec/changes/pr-pipeline-with-reports/specs/code-quality-gates/spec.md`
- `openspec/changes/pr-pipeline-with-reports/design.md`
- `openspec/changes/pr-pipeline-with-reports/tasks.md`
- `openspec/changes/pr-pipeline-with-reports/verify-report.md`

## Gate and Task Completion

- Native status at archive time: `archive: ready`, `nextRecommended: archive`, `blockers: null`.
- Task completion (`tasks.md`): 30 `[x]`, 0 `[ ]`. Six tasks are `[-]`, deliberately skipped with rationale: 2.1-2.4 and 5.3 (D8 manual issue closing after the S2 spike failed) and 8.1 (superseded by D11). No stale unchecked tasks exist, so no archive-time reconciliation was needed.
- The S2 spike failed: issue #39, PR #40 squash `c39d0b0`, promote run 44042751261 green, issue stayed open. The user chose manual closing. D8 was amended and the conditional tasks were skipped.

## Verification at Close

Final-state source: the launch prompt, which outranks the `verify-report` snapshot (verify-report.md, written at HEAD `b0f0e4f`, re-verified in `2aac027`, probes recorded in `5c92454`).

- Verdict: PASS WITH WARNINGS. 0 CRITICAL.
- Requirements 13/13, scenarios 39/39 (verify envelope, validated).
- Tests: `./gradlew :app:testDebugUnitTest --rerun` exit 0, 102 passed. Build `:app:assembleDebug` exit 0.
- Live pre-commit probes at `5c92454`: misformatted file exit 1, fake secret exit 1, broken workflow exit 1, clean file exit 0. The verify-report WARNING 3 (Pre-commit Hook scenarios not live) is resolved by these probes.

Open warnings at close:
1. TDD RED for tasks 1.1-1.3 is not recorded. This is a user-approved, documented exception (script and harness landed together in `b798f36`).
2. Five CI scenarios were verified statically only, and the user explicitly accepted them: "Report on failing tests", "Missing results", "Fork PR", "Concurrent publishes", "Test failure still uploads results". Live probes would need throwaway failing or fork PRs.

Suggestion (not actioned): `./gradlew koverHtmlReportDebug :app:lintDebug spotlessCheck` if a coverage figure is wanted. Coverage was not measured.

## Spec Sync

| Domain | Action | Details |
|--------|--------|---------|
| `ci-pipeline` | Created | New capability. Mechanical copy of the delta spec (12 requirements, 32 scenarios). |
| `code-quality-gates` | Updated | `Pre-commit Hook` requirement replaced by the delta version (7 scenarios, up from 4). The `(Previously: ...)` annotation was dropped from the main copy, following the precedent of `2026-11-13-imdb-style-ui-and-filters`. All other requirements unchanged (7 requirements, 25 scenarios). |

The delta is a superset of the living requirement. The 4 original scenarios are unchanged, and 3 scenarios are added. No requirement was removed or renamed, so the `rules.archive` destructive-merge warning does not apply.

Requirement and scenario totals in `openspec/specs/code-quality-gates/spec.md` after merge: 8 requirements, 32 scenarios.

## Archive Move

`openspec/changes/pr-pipeline-with-reports/` moved with `git mv` to `openspec/changes/archive/2026-11-17-pr-pipeline-with-reports/`. This report is written into the change folder before the move, so it is included in the archived folder.

## Final-State Notes

- Commits between the verify snapshot and close (`b0f0e4f`, `2aac027`, `5c92454`) are docs-only SDD commits. They align the delta with the living requirement, record the TDD exception, and record the live probes.
- Issue #3 will be closed manually after this branch's PR merges and a summary comment is posted. This is a human step, not an archive step.
