# Tasks: e2e-appium (#7)

Strict TDD is active: every implementation task is preceded by a RED task (failing first, expected output named), then GREEN, TRIANGULATE (where the spec has several cases) and REFACTOR. Spikes open their PR; a failed spike STOPS apply until the orchestrator and user decide (the design fallback is the proposed option, not an automatic switch).

Spec refs: `E2E` = e2e-testing, `CI` = ci-pipeline, `QG` = code-quality-gates (requirement names). Versions and names come from design.md (setup-node@v7, cache/restore@v6, cache/save@v6, android-emulator-runner@v2, `.nvmrc` 24.22.3).

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | PR1 ~300; PR2 ~480 excl. `package-lock.json` (generated); PR3 ~220 |
| 800-line budget risk (session) | Low for all three PRs |
| Chained PRs recommended | Yes (3 sequential PRs into `dev`, stacked-to-main) |
| Delivery strategy | ask-on-risk |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Medium

(Session budget is 800 lines per PR; PR2 exceeds 400 but stays under 800. Lockfile is generated and not counted.)

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | Tags + debug-only `e2eRoot` + release proof | PR1 (base `dev`) | `./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest` | `scripts/test-release-no-test-tags.sh`; SP2 page-source dump on the debug APK | Revert PR1; release behavior unchanged (identity modifier) |
| 2 | `e2e/` project, Page Objects, hook | PR2 (base PR1 merged into `dev`) | `cd e2e && npm run typecheck && npm run test:unit` | `cd e2e && npm run e2e` on a local emulator (SP1, SP3); `lefthook run pre-commit` manual RED/GREEN | Revert PR2; app untouched |
| 3 | CI job, report, Dependabot, docs | PR3 (base PR2 merged into `dev`) | `scripts/test-e2e-ci-config.sh` | `e2e-tests` job passing on the PR itself (SP4) | Revert PR3; `ci-gate` untouched |

---

## PR1: App tags and debug-only resource ids (branch `feat/7-e2e-appium-app`, `Refs #7`)

Start: `dev` without tags. End: tags in `main`, `e2eRoot` pair, Robolectric green, release proof harness green. Out of scope: `e2e/`, CI.

### 1. Spike (first, blocks PR1 merge)

- [ ] 1.1 **SP2 resource-id dump** (local, before PR1 merges; throwaway WDIO or `adb shell uiautomator dump`, nothing committed). Needs tasks 2.x-3.x GREEN to build the debug APK, so run it right after 3.5 and before 3.6. Pass: list (1 item) and open dialog show `book_list_item_<id>`, `book_list_remove_button_<id>`, both dialog buttons as `resource-id`; item text searchable. Fail, fallback (STOP and ask): apply `e2eRoot` on dialog content, tag inner `Text`s, or use `descriptionContains`. (E2E: Debug-Only Resource-Id Exposure)

### 2. Tag constants and screen tags (RED then GREEN)

- [x] 2.1 RED: extend `app/src/test/java/com/librosleidos/ui/list/BookListScreenTest.kt` with `onNodeWithTag(...).assertExists()` for every list and dialog tag (`book_list_add_button`, 3 filters, `book_list_loading`, `book_list_empty_state`, `book_list`, `book_list_delete_dialog`, confirm, cancel) and two distinct `book_list_item_<id>` / `book_list_remove_button_<id>`. Expected: compile error (unresolved `BookListTags`), then assertion failures. (E2E: Test Tag Naming, Per-item tags unique)
- [x] 2.2 RED: extend `app/src/test/java/com/librosleidos/ui/edit/BookEditScreenTest.kt` for every edit tag (search field/button/result_<index>/status, title, author, review, errors, cancel, save, `book_edit_rating_4_5`). Expected: unresolved `BookEditTags`. (E2E: Edit screen tagged)
- [x] 2.3 RED: `app/src/test/java/com/librosleidos/ui/components/StarRatingTest.kt`: `ratingTagSuffix(4.5f) == "4_5"`, `5.0f -> "5_0"`, `0.5f -> "0_5"`; edit mode with `tapTargetTagPrefix` tags each target; display mode stays untagged. Expected: unresolved `ratingTagSuffix`. (E2E: Test Tag Naming)
- [x] 2.4 GREEN: create `app/src/main/java/com/librosleidos/ui/list/BookListTags.kt` and `app/src/main/java/com/librosleidos/ui/edit/BookEditTags.kt` (`object`, `const val`, `fun item(id: Long)`). Apply tags in `BookListScreen.kt` (incl. dialog `modifier`) and `BookEditScreen.kt`.
- [x] 2.5 GREEN: `StarRating.kt`: add `tapTargetTagPrefix: String? = null`, `internal fun ratingTagSuffix(v: Float)` using `Locale.ROOT`; pass `book_edit_rating` from `BookEditScreen.kt`.
- [x] 2.6 TRIANGULATE: add a second `ratingTagSuffix` case set (`1.0f`, `3.5f`) and a second-item case; run `./gradlew :app:testDebugUnitTest --tests '*BookListScreenTest' --tests '*BookEditScreenTest' --tests '*StarRatingTest'`.
- [x] 2.7 REFACTOR + commit: `feat(app): add stable test tags for list, edit and delete dialog`.

### 3. Debug-only `e2eRoot` (RED then GREEN)

- [x] 3.1 RED: `app/src/testDebug/java/com/librosleidos/ui/E2eRootDebugTest.kt` (Robolectric `@Config(sdk=[34])`): `MainActivity` and the open delete dialog have a node with `SemanticsPropertiesAndroid.TestTagsAsResourceId == true` (`useUnmergedTree`). Expected: fails (property absent / unresolved `e2eRoot`). (E2E: Debug exposes tags as resource ids)
- [x] 3.2 RED: `app/src/testRelease/java/com/librosleidos/ui/E2eRootReleaseTest.kt` (plain JUnit): `assertSame(Modifier, Modifier.e2eRoot())`. Expected: unresolved reference. (E2E: Release behavior unchanged)
- [x] 3.3 GREEN: create `app/src/debug/java/com/librosleidos/ui/E2eRoot.kt` (`semantics { testTagsAsResourceId = true }`) and `app/src/release/java/com/librosleidos/ui/E2eRoot.kt` (`= this`).
- [x] 3.4 GREEN: apply `Scaffold(modifier = Modifier.e2eRoot())` in `MainActivity.kt` and `AlertDialog(modifier = Modifier.e2eRoot().testTag(...))` in `BookListScreen.kt`; confirm 3.1 and 3.2 pass (`:app:testDebugUnitTest`, `:app:testReleaseUnitTest --tests 'com.librosleidos.ui.E2eRootReleaseTest'`).
- [x] 3.5 REFACTOR + commit: `feat(app): expose test tags as resource ids in debug builds only`. Then run SP2 (task 1.1) against `./gradlew :app:assembleDebug`.
- [ ] 3.6 If SP2 needed its fallback, apply it with a RED test first (same files as 2.1/3.1) and amend the commit; record the outcome in the PR.

### 4. Release proof harness (RED then GREEN)

- [ ] 4.1 RED: write `scripts/test-release-no-test-tags.sh` skeleton asserting: no `TestTagsAsResourceId` in `app/src/{main,release}`; release `.class` output has 0 matches; debug output has >0 (positive control). Run it on a stash of `E2eRoot.kt`/before 3.3 (or with the debug pair temporarily removed): expected non-zero exit at the positive control. (E2E: Release exposes nothing)
- [ ] 4.2 GREEN: finish the script (`:app:compileReleaseKotlin :app:compileDebugKotlin`, class-dir grep, then `:app:testReleaseUnitTest --tests 'com.librosleidos.ui.E2eRootReleaseTest'`); exits 0.
- [ ] 4.3 TRIANGULATE: temporarily add `testTagsAsResourceId` to a `src/main` file; expected exit non-zero naming the file; revert.
- [ ] 4.4 Docs: `openspec/config.yaml` verify rule for the script; update `project-conventions.md` line 22 (root applied once on `Scaffold` plus the dialog). Run `shellcheck scripts/test-release-no-test-tags.sh`.
- [ ] 4.5 Commit: `test(app): prove release builds carry no test-tag exposure`.
- [ ] 4.6 PR1 verification record: Robolectric green, harness green, SP2 result, `./gradlew spotlessCheck`; PR body `Refs #7`. Rollback: revert PR1.

---

## PR2: `e2e/` Appium project (branch `feat/7-e2e-appium-project`, `Refs #7`; stacks on PR1)

Start: PR1 merged to `dev`. End: `npm ci`, `typecheck`, `test:unit` green; happy path passes locally; hook active. Out of scope: CI, Dependabot.

### 5. Spikes (first)

- [ ] 5.1 **SP1** in a scratch `e2e/` (committed result only if it passes): install `appium 3.9.1`, `appium-uiautomator2-driver 8.7.3`, `webdriverio 10.2.0`; with `APPIUM_HOME=<e2e>`, `appium driver list --installed` shows uiautomator2 from npm; a session on the local debug APK finds `book_list_add_button`. Fallback (STOP and ask): CommonJS config, `postinstall` local driver install, else reopen D3. (E2E: Pinned Dependencies)
- [ ] 5.2 **SP3** Allure 2.46.2: render mixed JUnit XML and WDIO JSON with a forced-failure PNG; suite and screenshot appear. Fallback (STOP and ask): Allure 3. Run after 8.3 produces results. (CI: Report Generation, E2E results shown)

### 6. Tooling and pins (RED then GREEN)

- [ ] 6.1 RED: `e2e/src/support/pins.test.ts` (`node:test`): every `dependencies` and `devDependencies` version is exact (no caret, tilde or range) and appium=3.9.1, driver=8.7.3, webdriverio=10.2.0, `@wdio/*`=10.2.0; `.nvmrc` is `24.22.3` and satisfies `engines`. Run `cd e2e && npm run test:unit`: expected fail (no `package.json`). (E2E: No version ranges, Pinned versions match)
- [ ] 6.2 GREEN: create `e2e/package.json` (scripts `e2e`, `typecheck`, `test:unit`; `type: module`; engines), `.nvmrc` (`24.22.3`), resolve and exactly pin `typescript`, `@types/node`, `@types/mocha`, `expect-webdriverio`; generate `e2e/package-lock.json` via `npm install`; verify `npm ci` exits 0 without touching the lockfile.
- [ ] 6.3 TRIANGULATE: edit one dependency to `^x` locally; expected: 6.1 fails and `npm ci` exits non-zero (lockfile drift); revert. (E2E: Lockfile drift fails)
- [ ] 6.4 GREEN: `e2e/tsconfig.json` (strict, `erasableSyntaxOnly`, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess`, wdio/mocha types) and `.gitignore` entries for `e2e/node_modules/`, `e2e/allure-results/`, `e2e/allure-report/`, `e2e/logs/` and `e2e/test-results/` (root-anchored).

### 7. Support code and Page Objects (RED then GREEN)

- [ ] 7.1 RED: `e2e/src/support/config.test.ts`: `resolveApkPath({})` returns the default APK `app/build/outputs/apk/debug/app-debug.apk` (read-only) resolved from the repository root (the e2e code expresses it relative to `e2e/`); `E2E_APK_PATH` overrides it; `APP_ID === "com.sergiolopez.rincondelectura"`. Expected: module not found. (E2E: APK override)
- [ ] 7.2 RED: `e2e/src/pages/tags.test.ts`: `tags.ts` builders produce `book_list_item_7`, `book_list_remove_button_7`, `book_edit_rating_4_5`, and a regex that rejects non-numeric ids. Expected: module not found. (E2E: Test Tag Naming)
- [ ] 7.3 RED: `e2e/src/structure.test.ts`: no selector strings (`UiSelector`, `resourceId(`, tag literals like `book_list_`) in any file outside `e2e/src/pages` and `e2e/src/support`; `bookList.page.ts` and `bookEdit.page.ts` exist. Expected: fails (pages absent). (E2E: Selectors encapsulated, Both screens covered)
- [ ] 7.4 GREEN: `e2e/src/support/config.ts`, `e2e/src/pages/tags.ts` (mirror of Kotlin tags), `e2e/src/pages/base.page.ts` (`byTag`, `byTagPattern`, `tap`, `typeText`, `waitForVisible` 20 s).
- [ ] 7.5 GREEN: `e2e/src/pages/bookList.page.ts` (incl. dialog; `itemIdByTitle` via `resourceIdMatches("^book_list_item_\\d+$")` + `textContains`, parse numeric suffix) and `e2e/src/pages/bookEdit.page.ts`; actions plus `assert*`. `npm run typecheck` goes from errors (missing modules) to 0.
- [ ] 7.6 TRIANGULATE: add cases to `config.test.ts` (empty-string env falls back to default) and `tags.test.ts` (ids 0, 12345; suffixes `5_0`, `0_5`).
- [ ] 7.7 GREEN: `e2e/src/data/books.ts` (synthetic: "The Clockwork Teapot", "A. N. Example"; no real data). (E2E: Synthetic Data Only)
- [ ] 7.8 REFACTOR + commit: `feat(e2e): add Appium project with Page Objects and tag mirror`.

### 8. WDIO config, evidence, happy path

- [ ] 8.1 RED: `e2e/src/support/evidence.test.ts`: `attachFailureScreenshot` attaches only when `passed === false` (injected fake driver and reporter; pass case asserts zero attachments; a throwing `takeScreenshot` is swallowed). Expected: module not found. (E2E: Failure Evidence Policy)
- [ ] 8.2 GREEN: `e2e/src/support/evidence.ts` and `e2e/wdio.conf.ts` (local runner, `maxInstances: 1`, mocha 60 s, spec + allure `./allure-results` with step/screenshot auto-reporting disabled, appium service with `APPIUM_HOME`, caps `noReset`, `enforceAppInstall`, `appium:app` from `resolveApkPath`, `beforeTest` `mobile: clearApp` + `activateApp`, `afterTest` evidence).
- [ ] 8.3 RED: `e2e/src/tests/bookLifecycle.e2e.ts` written against the Page Objects (add, assert list, edit title/rating, assert, delete via dialog, assert empty state) plus a cancel case (book remains). Run `cd e2e && npm run e2e` on a local emulator before the Page Object methods are complete: expected fail at the first missing method or locator. Then GREEN: complete the methods until it passes. (E2E: Full flow passes, Cancel keeps the book, Data reset between tests)
- [ ] 8.4 TRIANGULATE: run offline (`adb shell cmd connectivity airplane-mode enable`) and pass; run a forced-failure test: PNG attached; passing test has none. Feed `allure-results` to SP3 (task 5.2). (E2E: No network needed, Failure attaches a screenshot, Pass attaches none)
- [ ] 8.5 REFACTOR + commit: `test(e2e): add happy-path and cancel flows with failure evidence`.

### 9. Lefthook type-check hook (manual RED then GREEN)

- [ ] 9.1 RED: with no hook yet, stage `e2e/src/pages/base.page.ts` with a type error, then `e2e/wdio.conf.ts` with a type error: `lefthook run pre-commit` exits 0 (gate missing). Record in PR. (QG: Staged e2e TypeScript with a type error blocked)
- [ ] 9.2 GREEN: add job `e2e-typecheck` to `lefthook.yml` (globs `{e2e/*.ts,e2e/**/*.ts}`; missing `node_modules` fails with `cd e2e && npm ci` hint; `npx --no-install tsc --noEmit`; fix hint). Both staged files now exit non-zero with the hint.
- [ ] 9.3 TRIANGULATE: clean staged `.ts` exits 0; only a staged `.md` skips the job; `actionlint`; no CI step runs `tsc`. (QG: clean passes, Non-e2e change skips, Type check not repeated in CI)
- [ ] 9.4 Docs: `e2e/README.md` (setup, `npm ci`, `E2E_APK_PATH`, emulator, running, hook). Commit: `build(e2e): add TypeScript pre-commit check and e2e README`.
- [ ] 9.5 PR2 verification record: `npm ci`, `typecheck`, `test:unit` green; happy path passes locally; SP1 and SP3 outcomes; hook RED/GREEN log; `Refs #7`. Rollback: revert PR2 (`e2e/`, `.nvmrc`, hook, `.gitignore`).

---

## PR3: CI wiring (branch `feat/7-e2e-appium-ci`, `Closes #7`; stacks on PR2)

Start: PR2 merged to `dev`. End: `e2e-tests` job green on its own PR, report includes e2e results, Dependabot covers `e2e/`. Out of scope: gating `ci-gate`.

### 10. Spike (first)

- [ ] 10.1 **SP4** on the PR3 branch: draft `e2e-tests` with API 34 `default` x86_64 on `ubuntu-24.04`. Pass: boot under 5 min (warm cache under 2 min), job under 20 min. Fallback (STOP and ask): API 35 `google_apis`, then 33. Also note repo visibility (runner size/KVM, open question). (CI: E2E Test Job)

### 11. CI harness (RED then GREEN)

- [ ] 11.1 RED: `scripts/test-e2e-ci-config.sh` (awk `job_body`) with checks: job `e2e-tests` exists; `contents: read` only; no `secrets.`; no `needs`; absent from `ci-gate` needs; `report` needs `e2e-tests`; Detect step has the `*-result.json` branch; `node-version-file: .nvmrc`; `retention-days: 7`; `if: always()` on upload; Dependabot `npm` with `directory: /e2e`, weekly, `target-branch: dev`. Run: expected non-zero (job missing). (CI: Read-only and secret-free, Non-gating, Report waits for e2e, npm e2e covered)
- [ ] 11.2 GREEN: `.github/workflows/ci.yml` `e2e-tests` job per design (checkout@v7, setup-java@v6 17, setup-gradle@v6 read-only off `dev`, `:app:assembleDebug`, `scripts/test-release-no-test-tags.sh`, setup-node@v7 with `.nvmrc` and `cache-dependency-path e2e/package-lock.json`, `npm ci`, KVM rules, cache/restore@v6 AVD, dev-and-miss snapshot create plus cache/save@v6, android-emulator-runner@v2 with airplane mode and `npm run e2e`, upload-artifact@v7 `allure-results-e2e`, retention 7). `timeout-minutes: 30`. (CI: E2E Test Job, Runs on every trigger, Failure still uploads)
- [ ] 11.3 GREEN: `ci.yml` `report`: `needs` += `e2e-tests`; Detect = `compgen *.xml || compgen *-result.json`; summary text "no test results". Leave `ci-gate` untouched. (CI: Report Generation, E2E results shown, Missing results)
- [ ] 11.4 GREEN: `.github/dependabot.yml` `npm` for directory e2e (Dependabot notation /e2e) (weekly Monday, `target-branch: dev`, prefix `build` `include: scope`, limit 5, groups `wdio`, `appium` and `typescript`, ignore semver-major for appium, driver, webdriverio, `@wdio/*`). (CI: Dependabot Configuration)
- [ ] 11.5 TRIANGULATE: mutate the config (add `secrets.X`, add `e2e-tests` to `ci-gate` needs, remove retention); each mutation makes `scripts/test-e2e-ci-config.sh` fail naming the check; revert. Run `actionlint` and `shellcheck`. Expected: green.
- [ ] 11.6 REFACTOR + commit: `ci(e2e): add non-gating Appium job, report wiring and npm Dependabot`.

### 12. Docs and verification

- [ ] 12.1 `README.md` (Spanish): e2e section (local run, `E2E_APK_PATH`, non-gating CI job, Allure report) and Dependabot npm note. `openspec/config.yaml`: mark the `e2e` layer available. Commit: `docs(e2e): document Appium suite and CI job`.
- [ ] 12.2 PR3 verification record: scenarios mapped to evidence; SP4 outcome; `e2e-tests` green on the PR, failing-run upload checked once (throwaway), report shows e2e. PR body `Closes #7` (only here; PR1 and PR2 use `Refs #7`). Rollback: revert PR3 (job, report needs, Dependabot); `ci-gate` untouched.

---

## Dependencies and order

- Sequential PRs: PR1 -> PR2 -> PR3, each merged into `dev` before the next opens.
- PR1: 2 and 3 are independent RED/GREEN pairs; SP2 (1.1) runs after 3.5 and before PR1 merges; 4.1 can run any time but its positive-control RED needs the debug pair absent.
- PR2: 5.1 before 6; 6 -> 7 -> 8; 5.2 after 8.3; 9 independent of 7 and 8 once 6.4 exists.
- PR3: 10.1 and 11.1 may run in parallel; 11.2 -> 11.3; 11.4 independent; 12 after 11.
- Single writer; no parallel writers.
