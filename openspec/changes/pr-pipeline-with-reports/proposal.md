# Proposal: PR Pipeline With Test Reports

## Intent

Issue #3: local gates (#2) can be bypassed (`--no-verify`) and `promote.yml` only warns when the promoted SHA has no checks. Add CI that enforces those gates on every PR and dev push, publishes linked Allure + Kover reports, and makes `Closes #N` close issues on promotion to `main`.

## Scope

### In Scope
- `.github/workflows/ci.yml` (`pull_request` + push to `dev`, no path filters, `ubuntu-24.04`): parallel lint jobs (spotlessCheck, `:app:lintDebug`, actionlint, gitleaks, PR-range commits + PR title, shellcheck + commit-msg script test) gate `unit-tests` (`:app:testDebugUnitTest` + Kover XML/HTML).
- `report` job (`if: always()`): merges `allure-results-*`, generates Allure 2.46.2 from JUnit XML, publishes Allure + Kover to `gh-pages` (`pr-<N>/`, `dev/`), upserts a sticky PR comment (links + line/branch coverage); same-repo PRs only.
- `ci-gate` aggregator as the single required check; `pr-cleanup.yml` removes `pr-<N>/` on close.
- Artifact contract `allure-results-<suite>`, `coverage-<suite>`, `pit-<suite>`.
- `promote.yml`: require a successful latest `ci-gate` check-run on the promoted SHA; closing-keyword fallback if the spike requires it.

### Out of Scope
- Contract/PIT (#6), E2E (#7), SonarCloud/CodeQL/Dependabot (#4).
- Fork PR commenting via `workflow_run`; Robolectric bump.

## Capabilities

### New Capabilities
- `ci-pipeline`: job graph and gating, report publishing + PR comment, artifact contract, promotion check, issue closing (orchestration, not gate semantics).

### Modified Capabilities
- None. `code-quality-gates` gate behavior is unchanged; CI only invokes it.

## Approach

Spikes first: Allure 2 JUnit XML (fallback Allure 3), fast-forward issue closing (throwaway issue), Actions app id. Least privilege (`contents: read`; writes only on report/cleanup), never `pull_request_target`, pinned action majors, `concurrency: gh-pages`. No app code or layering changes.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `.github/workflows/ci.yml` | New | Lint, test, report, gate |
| `.github/workflows/pr-cleanup.yml` | New | Delete PR report folder |
| `.github/workflows/promote.yml` | Modified | Require `ci-gate`; issue closing fallback |
| `README.md` | Modified | CI, reports, admin steps |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Allure 2 reads JUnit XML poorly | Med | Spike; Allure 3 fallback |
| Fast-forward push does not close issues | Med | Throwaway test; promote.yml closes them |
| `gh-pages` push race | Med | Concurrency group, `keep_files` |
| Pages 10 builds/h, 1 GB cap | Low | Solo repo; per-PR cleanup |
| Required-check rename blocks merges | Med | Fixed `ci-gate` name; add after first green run |
| Action major mismatch | Low | Verify on first run |

## Rollback Plan

Remove `ci-gate` from the `dev` ruleset first, then revert the PR (restores `promote.yml`). Optionally delete `gh-pages` and disable Pages.

## Dependencies

- Admin: enable Pages after `gh-pages` exists; add `required_status_checks` (`ci-gate`) to the `dev` ruleset after the first green run; set squash/merge message to include the PR description.

## Success Criteria

- [ ] AC1: lint jobs run in parallel; a failure skips `unit-tests` and fails `ci-gate`.
- [ ] AC2: a same-repo PR gets one sticky comment linking live Allure and Kover reports with coverage figures.
- [ ] AC3: a `Closes #N` commit closes the issue when promoted to `main` (throwaway test).
- [ ] `promote.yml` fails when `ci-gate` is missing or unsuccessful.
- [ ] `pr-cleanup.yml` removes `pr-<N>/` on close.
