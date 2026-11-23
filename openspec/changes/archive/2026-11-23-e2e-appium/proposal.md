# Proposal: Appium E2E Suite With Page Objects (#7)

## Intent

The app has no end-to-end coverage. Robolectric does not prove that the real APK works on a device. Issue #7 asks for an Appium suite on an emulator that reports into the existing Allure report, without exposing extra view IDs in release builds.

## Scope

### In Scope
- Compose `testTag`s following `<screen>_<element>[_<id>]` on the list and edit screens, the delete dialog and the `StarRating` targets.
- `Modifier.e2eRoot()` defined in a `src/debug` + `src/release` pair. Debug sets `testTagsAsResourceId = true`; release returns the modifier unchanged. Applied at the `MainActivity` root and inside the delete `AlertDialog` content. Robolectric tests written first (Strict TDD).
- `/e2e` project: TypeScript + WebdriverIO 10.2.0 (`@wdio/cli`, `@wdio/appium-service`, `@wdio/allure-reporter`), Appium 3.9.1, appium-uiautomator2-driver 8.7.3. Exact pins, committed lockfile, `npm ci`, Node pinned in `.nvmrc` (22 >=22.23.4 or 24 >=24.22.1).
- Page Objects for list and edit. One happy-path test: add, list, edit, delete, then empty state. Manual entry, no network, `clearApp` before each test, APK path overridable through an env variable.
- Screenshots on failure only. Synthetic book data only in screenshots, page source and logs.
- `e2e-tests` CI job on an Android emulator (`ubuntu-24.04`): runs on every PR and `dev` push, `permissions: contents: read`, no secrets. Uploads `allure-results-e2e` with `retention-days: 7`. Listed in `report` needs only (non-gating).
- Dependabot `npm` entry for `/e2e`. Local lefthook `tsc --noEmit` for `e2e/**/*.ts`.
- README, `openspec/config.yaml` (`e2e` layer), `.gitignore`.

### Out of Scope
- Making `e2e-tests` gating in `ci-gate`. A later delta does this after a flake burn-in.
- Appium 4 / driver 9+. Revisit when WDIO documents them.
- CodeQL `javascript-typescript`, metadata search tests, fake metadata source, Allure 3 migration (unless the spike requires it).

## Capabilities

### New Capabilities
- `e2e-testing`: tag naming, debug-only resource-id exposure, Page Objects, happy path, data reset, evidence policy, pinned dependencies.

### Modified Capabilities
- `ci-pipeline`: Out of Scope Boundaries rewritten as a permanent boundary that allows `e2e-tests` (no "This change MUST NOT..." wording). Artifact Contract adds the `e2e` suite (Allure JSON). Report `needs`, least privilege and Dependabot ecosystems change. E2E Test Job is ADDED. `ci-gate` stays unchanged.
- `code-quality-gates`: the Pre-commit Hook adds local `tsc --noEmit` for `/e2e`. CI stays free of static analysis.
- `book-entry` and `book-library`: unchanged.

## Approach

Run the design spikes first. A failed spike reopens its decision:
1. Appium 3.9.1 + driver 8.7.3 against a local debug APK.
2. Resource-id exposure in the dialog and in the merged clickable list item (page-source dump).
3. Allure 2.46.2 rendering WDIO JSON and screenshots.
4. Emulator API level, image and boot time on `ubuntu-24.04`.

Deliver as 3 chained PRs into `dev`. `package-lock.json` counts as generated for the 800-line budget.

| PR | Scope | Estimate |
|---|---|---|
| 1 | Tags, debug/release modifier, dialog flag, Robolectric tests | ~250 |
| 2 | `/e2e` project, Page Objects, happy path, `.nvmrc`, lefthook tsc | ~400 (excluding the lockfile) |
| 3 | CI job, `report` needs, Dependabot npm, spec deltas, README, config | ~200 |

## Affected Areas

| Area | Impact |
|---|---|
| `app/src/main/java/com/librosleidos/MainActivity.kt`, `ui/list/BookListScreen.kt`, `ui/edit/BookEditScreen.kt`, `ui/components/StarRating.kt` | Modified |
| `app/src/debug/**`, `app/src/release/**`, `app/src/test/**` | New/Modified |
| `e2e/**`, `.nvmrc` | New |
| `.github/workflows/ci.yml`, `.github/dependabot.yml`, `lefthook.yml`, `.gitignore` | Modified |
| `README.md`, `openspec/config.yaml` | Modified |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Dialog or merged list item does not expose resource-ids | Med | Spike 2. Flag inside the dialog; tag the inner button. |
| Allure 2.46.2 does not render WDIO JSON | Low | Spike 3. Use the spec's Allure 3 escape hatch. |
| Emulator flakiness and cost | High | Non-gating, `disable-animations`, AVD cache built on `dev`. |
| Strict Node engines break `npm ci` | Med | `.nvmrc` and `setup-node` read it. |
| PII in evidence | Low | Synthetic data only; 7-day retention; screenshots on failure only. |
| gh-pages growth from screenshots | Med | Failure-only screenshots; cleanup decided in design. |

## Rollback Plan

Revert PR3, then PR2, then PR1. Reverting PR3 removes the job and restores the spec boundary. `ci-gate` is never touched. Release builds stay unaffected because the release modifier is the identity.

## Dependencies

- `reactivecircus/android-emulator-runner@v2` and KVM on GitHub-hosted runners.

## Success Criteria

- [ ] A debug APK exposes every listed tag as a resource-id, including the dialog. A release build has no `testTagsAsResourceId` reference (test or bytecode check).
- [ ] The happy path passes on the CI emulator, and its results appear in the Allure report with a screenshot on a forced failure.
- [ ] `e2e-tests` has `contents: read` and no secrets. It uses `pull_request`, not `pull_request_target`, so fork PRs get no secrets. The artifact has `retention-days: 7`.
- [ ] Every `/e2e` dependency is exact-pinned, the lockfile is committed, and Dependabot npm covers `/e2e`.
- [ ] `ci-gate`, local hooks, CodeQL and secret-scan are green. Each PR stays under 800 lines (lockfile excluded).
