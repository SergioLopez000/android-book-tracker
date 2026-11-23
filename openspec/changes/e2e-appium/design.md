# Design: Appium E2E Suite With Page Objects (#7)

## Technical Approach

Stable Compose test tags live in `main`. A `Modifier.e2eRoot()` pair in `src/debug` and `src/release` exposes them as resource ids in debug only. A new `/e2e` npm project (WDIO 10.2.0, Appium 3.9.1, UiAutomator2 8.7.3, Mocha, ESM TypeScript) drives the debug APK through Page Objects. A non-gating `e2e-tests` CI job runs it on an emulator and feeds Allure JSON into the existing `report` job. The spikes run first; a failed spike switches to its fallback below.

## Tag Map (`<screen>_<element>[_<id>]`)

| Screen | Tags |
|---|---|
| list | `book_list_add_button`, `book_list_filter_<last_month\|last_year\|all_time>` (enum `name.lowercase()`), `book_list_loading`, `book_list_empty_state`, `book_list` (LazyColumn), `book_list_item_<id>`, `book_list_remove_button_<id>` |
| dialog | `book_list_delete_dialog` (on the `AlertDialog` `modifier`), `book_list_delete_confirm_button`, `book_list_delete_cancel_button` |
| edit | `book_edit_search_field`, `book_edit_search_button`, `book_edit_search_result_<index>`, `book_edit_search_status`, `book_edit_title_field`, `book_edit_author_field`, `book_edit_review_field`, `book_edit_rating_<w>_<f>` (`4_5`, `5_0`), `book_edit_error_<title\|rating\|date_finished>`, `book_edit_cancel_button`, `book_edit_save_button` |

- Constants live in `ui/list/BookListTags.kt` and `ui/edit/BookEditTags.kt` (`object` with `const val` plus `fun item(id: Long)`). Robolectric tests and screens share them. `e2e/src/pages/tags.ts` mirrors them; Robolectric pins the Kotlin side and the e2e run pins the TypeScript side.
- `StarRating` gets `tapTargetTagPrefix: String? = null`. When it is set, each tap target adds `Modifier.testTag("${prefix}_${ratingTagSuffix(value)}")`. `internal fun ratingTagSuffix(v: Float) = "%d_%d".format(Locale.ROOT, whole, tenths)` is locale-free. Display mode stays untagged because `clearAndSetSemantics` merges it.
- **Unknown new id.** `BookListPage.itemIdByTitle(title)` selects `resourceIdMatches("^book_list_item_\\d+$").textContains(title)` (SP2 fallback: `descriptionContains`, then `childSelector(text)`). It reads the `resource-id` attribute and parses the numeric suffix, then builds `book_list_remove_button_<id>`.

## Architecture Decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Debug-only flag | `fun Modifier.e2eRoot(): Modifier` in package `com.librosleidos.ui`, file `E2eRoot.kt`, in both `app/src/debug/java` and `app/src/release/java`. Debug: `semantics { testTagsAsResourceId = true }`. Release: `= this` | `BuildConfig.DEBUG` branch | The release bytecode never links the property (D2). |
| Where applied | `Scaffold(modifier = Modifier.e2eRoot())` in `MainActivity`; `AlertDialog(modifier = Modifier.e2eRoot().testTag(...))` | Once per Route (current convention text) | One activity root covers both Routes. The dialog is a separate window (research C13). Update `project-conventions.md` line 22 to match. |
| Release proof | `scripts/test-release-no-test-tags.sh` (pattern of `test-image-config.sh`): greps `app/src/{main,release}` sources, runs `:app:compileReleaseKotlin :app:compileDebugKotlin`, greps the release `.class` output for `TestTagsAsResourceId` (must be 0) and the debug output (must be >0, a positive control so the check is not vacuous). Plus the plain JUnit `app/src/testRelease/.../E2eRootReleaseTest` (`assertSame(Modifier, Modifier.e2eRoot())`) | Release unit test only | A unit test cannot prove that a reference is absent. The spec scenario is literally a bytecode and source search. |
| Release proof in CI | The script also runs `:app:testReleaseUnitTest --tests 'com.librosleidos.ui.E2eRootReleaseTest'`. The whole CI release proof (bytecode grep and identity test) lives in a step of the non-gating `e2e-tests` job (PR3), plus a local `verify` rule in `config.yaml` (PR1) | `unit-tests` step | The `unit-tests` spec fixes its command to `:app:testDebugUnitTest`, so `testRelease` never runs there. The e2e job already owns this domain. It is non-gating, so a release regression shows as a red job, not a blocked merge. |
| Module system | ESM (`"type": "module"`, `module`/`moduleResolution: NodeNext`), strict options copied from the reference `appium-ts` | CommonJS (reference) | Appium 3 and WDIO 10 are ESM-first. SP1 fallback: CommonJS. |
| Driver install | Driver as an exact devDependency. The Appium service runs local `node_modules/.bin/appium` with `APPIUM_HOME=<e2e dir>` set in `wdio.conf.ts`, so Appium detects the project-local extension. No `allowInsecure`, no chromedriver autodownload | `appium driver install` at runtime | No runtime downloads; Dependabot sees the version. SP1 fallback: `appium driver install --source=local ./node_modules/appium-uiautomator2-driver` in `postinstall`. |
| Reset | `beforeTest`: `mobile: clearApp {appId}`, then `activateApp(appId)`; caps `appium:noReset: true`, `appium:enforceAppInstall: true` (the given APK is always reinstalled, even if a build with the same version is on the device), `appium:app` from `E2E_APK_PATH` (default `../app/build/outputs/apk/debug/app-debug.apk`) | `fullReset` | Fast. The APK is installed once per session. |
| Evidence | `afterTest`: if `!passed`, `takeScreenshot` then `allureReporter.addAttachment(..., 'image/png')` in try/catch. Allure reporter `disableWebdriverStepsReporting` and `disableWebdriverScreenshotsReporting: true`. No page-source attachment | Reference screenshot + page source | Spec: screenshot on failure only. Less data exposure. |
| Offline | The CI script runs `adb shell cmd connectivity airplane-mode enable` before WDIO | Network stubbing | Proves the "No network needed" scenario. Manual entry makes no calls. |
| Node | `.nvmrc` `24.22.3` (repo root; latest patch, 2026-11-10). `engines.node` keeps the floor `>=24.22.1 <25` | 22.x | Active LTS. Satisfies WDIO `>=24.22.1 <25`, driver `>=24.0.0` and Appium 3 engines. |
| npm cache on PRs | Keep `cache: npm` on every run. GitHub scopes a cache saved by a PR run to that PR's ref; `dev` and other PRs cannot restore it. The key is the lockfile hash | A dev-only npm cache | `setup-node` has no read-only mode, and PR-ref isolation already prevents cache poisoning. A dev-only cache would make every PR install cold. |
| AVD/emulator defaults | API 34, `target: default`, `arch: x86_64` | 35/36, `google_apis` | Lightest stable image; the app needs no Play services. SP4 fallback: 35 `google_apis`, then 33. |
| gh-pages screenshots | Accept growth: failure-only PNGs, `pr-<N>/` already removed by `pr-cleanup.yml`, so only failing `dev` pushes accumulate under `dev/allure/data/attachments` | Pre-publish deletion of `dev/allure` (an extra gh-pages checkout and push) | Low volume. Revisit above 50 MB (open question). |
| Lefthook missing deps | Fail closed with the hint `cd e2e && npm ci` | Skip silently | Same as the existing `brew install` hints. |

## `/e2e` Layout

| File | Content |
|---|---|
| `package.json` | `private`, `type: module`, `engines.node: ">=24.22.1 <25"`, scripts `e2e` (`wdio run ./wdio.conf.ts`), `typecheck` (`tsc --noEmit`), `test:unit` (`node --test "src/**/*.test.ts"`, using Node 24 type stripping). devDependencies, exact: `appium 3.9.1`, `appium-uiautomator2-driver 8.7.3`, `webdriverio 10.2.0`, `@wdio/{cli,local-runner,mocha-framework,spec-reporter,allure-reporter,appium-service,globals} 10.2.0`. `typescript`, `@types/node` (24.x), `@types/mocha` and `expect-webdriverio` are resolved at apply time and pinned exactly. |
| `package-lock.json` | Committed; generated (D6). |
| `tsconfig.json` | `noEmit`, `allowImportingTsExtensions`, `erasableSyntaxOnly` (no enums, namespaces or parameter properties, which Node type stripping rejects), `strict`, `exactOptionalPropertyTypes`, `noUncheckedIndexedAccess`, types `node`, `@wdio/globals/types`, `@wdio/mocha-framework`, `expect-webdriverio`; includes `src/**/*.ts` and `wdio.conf.ts`. |
| `wdio.conf.ts` | One file: runner local, `maxInstances: 1`, mocha (60 s timeout), reporters spec plus allure `./allure-results`, appium service (`args.log ./logs/appium.log`), the capability above, `language en`/`locale US`, `beforeTest` reset, `afterTest` evidence. |
| `src/support/{config,evidence}.ts` | `resolveApkPath(env)`, `APP_ID = "com.sergiolopez.rincondelectura"`; `attachFailureScreenshot()`. |
| `src/pages/base.page.ts` | `byTag(tag)` returns `$('android=new UiSelector().resourceId("<tag>")')`, `byTagPattern(re)`, `tap`, `typeText`, `waitForVisible` (20 s). |
| `src/pages/{bookList,bookEdit}.page.ts`, `tags.ts` | Singletons; private locator getters; actions plus `assert*`. The dialog belongs to `bookList`. |
| `src/data/books.ts` | Synthetic fixtures ("The Clockwork Teapot", "A. N. Example"). |
| `src/tests/bookLifecycle.e2e.ts` | Add, then assert in the list; edit (title, rating) and assert; open the dialog and cancel, assert the book is still there; confirm the delete and assert the empty state. |
| `src/**/*.test.ts` | `node:test` suites: exact pins and versions, `resolveApkPath`, tag builders, and "no selector strings outside `src/pages`". |

## CI (`.github/workflows/ci.yml`, PR3)

```
e2e-tests (ubuntu-24.04, no needs, permissions: contents: read, timeout-minutes: 30)
  checkout@v7 -> setup-java@v6 17 -> setup-gradle@v6 (cache-read-only: ref != dev)
  -> ./gradlew :app:assembleDebug -> scripts/test-release-no-test-tags.sh
  -> actions/setup-node@v7 (node-version-file .nvmrc, cache npm, cache-dependency-path e2e/package-lock.json) -> npm ci (e2e)
  -> Enable KVM (99-kvm4all.rules, udevadm reload/trigger)
  -> actions/cache/restore@v6 key avd-api34-default-x86_64-v1 (~/.android/avd/*, ~/.android/adb*)
  -> [dev && miss] reactivecircus/android-emulator-runner@v2: create snapshot (script: echo) -> actions/cache/save@v6
  -> reactivecircus/android-emulator-runner@v2: api-level/target/arch, force-avd-creation false, disable-animations true,
       emulator-options "-no-snapshot-save -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none",
       working-directory e2e, script: airplane mode on; npm run e2e
  -> upload-artifact@v7 allure-results-e2e (e2e/allure-results, if: always(), if-no-files-found ignore, retention-days 7)
report: needs += e2e-tests; Detect = compgen *.xml || compgen *-result.json; summary text "no test results".
ci-gate: untouched.
```

Action majors: `checkout@v7`, `setup-java@v6`, `gradle/actions/setup-gradle@v6` and `upload-artifact@v7` reuse the majors already in `ci.yml`. `setup-node@v7`, `cache/restore@v6` and `cache/save@v6` are new to the repo (latest release majors verified 2026-11-23: setup-node v7.1.0, cache v6.1.0); Dependabot `github-actions` then keeps them current.

`.github/dependabot.yml` adds `npm` `/e2e`: weekly on Monday, `target-branch: dev`, prefix `build` with `include: scope`, `open-pull-requests-limit: 5`. Groups: `wdio` (`webdriverio`, `@wdio/*`, `expect-webdriverio`), `appium` (`appium`, `appium-*`), `typescript` (`typescript`, `@types/*`). It ignores semver-major updates for `appium`, `appium-uiautomator2-driver`, `webdriverio` and `@wdio/*` (D3).

`lefthook.yml` adds the job `e2e-typecheck`, glob `{e2e/*.ts,e2e/**/*.ts}`. The first alternative matches the root `e2e/wdio.conf.ts`, in case gobwas `**` needs at least one directory level; verify both files trigger the job at apply time. It runs `cd e2e && { [ -d node_modules ] || { echo "Run: cd e2e && npm ci"; exit 1; }; } && npx --no-install tsc --noEmit || { echo "Fix the TypeScript errors (cd e2e && npm run typecheck)"; exit 1; }`.

## File Changes

| File | Action | PR |
|---|---|---|
| `app/src/{debug,release}/java/com/librosleidos/ui/E2eRoot.kt` | Create | 1 |
| `MainActivity.kt`, `BookListScreen.kt`, `BookEditScreen.kt`, `StarRating.kt`, `ui/{list,edit}/*Tags.kt` | Modify/Create | 1 |
| `app/src/test/.../{BookListScreenTest,BookEditScreenTest,StarRatingTest}.kt`, `app/src/testDebug/.../E2eRootDebugTest.kt`, `app/src/testRelease/.../E2eRootReleaseTest.kt` | Modify/Create | 1 |
| `scripts/test-release-no-test-tags.sh`, `openspec/config.yaml` (verify rule), `project-conventions.md` | Create/Modify | 1 |
| `e2e/**`, `.nvmrc`, `lefthook.yml`, `.gitignore` (`/e2e/{node_modules,allure-results,allure-report,logs,test-results}/`) | Create/Modify | 2 |
| `ci.yml`, `dependabot.yml`, `scripts/test-e2e-ci-config.sh`, `README.md`, `openspec/config.yaml` (`e2e` layer) | Modify/Create | 3 |

## PR Slicing (chained into `dev`, 800-line budget, lockfile excluded)

| PR | Estimate | Done when | Rollback |
|---|---|---|---|
| 1 App | ~300 (tests ~170, main ~90, script ~40) | Robolectric is green and the harness passes | Revert; release is unaffected |
| 2 `/e2e` | ~480 (configs ~140, pages ~170, spec and tests ~130, docs ~40) | `npm ci`, `typecheck` and `test:unit` are green, and the local happy path passes | Revert; app untouched |
| 3 CI | ~220 (job ~80, report ~8, Dependabot ~25, harness ~60, docs ~50) | The job passes on the PR itself | Revert; `ci-gate` untouched |

## Testing Strategy (Strict TDD per work unit)

| Unit | RED first | Approach |
|---|---|---|
| Tags (PR1) | `onNodeWithTag(...).assertExists()` for every tag, including the dialog and two distinct item ids; `ratingTagSuffix` cases | Robolectric `@Config(sdk=[34])`, existing screen tests |
| Debug flag (PR1) | `testDebug`: `MainActivity` and the open dialog have a node with `SemanticsPropertiesAndroid.TestTagsAsResourceId == true` (`useUnmergedTree`) | Robolectric |
| Release (PR1) | Identity test; the harness fails before `E2eRoot` exists (positive control) | JUnit and the shell harness |
| `/e2e` (PR2) | `node:test` suites; `tsc` fails before the Page Objects exist | Then the happy path on a local emulator (SP1, SP3) |
| Lefthook tsc (PR2) | Stage a `e2e/src/**/*.ts` file with a type error, then `e2e/wdio.conf.ts` with a type error: `lefthook run pre-commit` exits non-zero with the hint for each. A clean staged file exits 0. Only a staged `.md` skips the job | Manual RED/GREEN, recorded in the PR |
| CI (PR3) | `scripts/test-e2e-ci-config.sh` (awk `job_body`), with these RED checks: `contents: read` only, no `secrets.`, not in `ci-gate` needs, in `report` needs, retention 7, `if: always()`, `node-version-file: .nvmrc`, a Detect step with the `*-result.json` branch, and a Dependabot `npm` entry with `directory: /e2e`, weekly and `target-branch: dev` | Run locally with actionlint |

## Spikes

| Spike | Pass | Fallback |
|---|---|---|
| SP1 Appium 3.9.1 + driver 8.7.3 + WDIO 10.2.0, local debug APK | `appium driver list --installed` shows uiautomator2 from npm; a session starts and `byTag` finds `book_list_add_button` | CommonJS config; `postinstall` local driver install; otherwise reopen D3 |
| SP2 Page-source dump on the list (1 item) and with the dialog open | `book_list_item_<id>`, `book_list_remove_button_<id>` and both dialog buttons appear as `resource-id`; item text is searchable | Apply `e2eRoot` on the dialog `text`/buttons content; tag inner `Text`s; switch the selector to `descriptionContains` |
| SP3 Allure 2.46.2 on mixed JUnit XML and WDIO JSON with a forced-failure PNG | The e2e suite and the screenshot render | Allure 3 (spec escape hatch) |
| SP4 API 34 `default` x86_64 on `ubuntu-24.04` | Boot under 5 min (warm cache under 2 min); job under 20 min | 35 `google_apis`, then 33 |

Timing: SP2 runs locally before PR1 merges. It uses a throwaway local WDIO or `adb shell uiautomator dump` against the PR1 debug APK, because its fallback changes PR1 app code. SP1 and SP3 run during PR2; SP4 runs on the PR3 branch.

## Threat Matrix

N/A for documentation paths, repository selection, commit, push and PR rows: no VCS or PR automation is added. Process and CI boundary (applicable): `e2e-tests` runs fork code under `pull_request` with `contents: read` and no secrets. The Gradle and AVD caches are read-only on PRs. The npm cache is saved on PR runs but scoped to the PR ref, so it cannot reach `dev` or other PRs. RED: `scripts/test-e2e-ci-config.sh`. Shell scripts take no external input.

## Migration / Rollout

No migration. PR1 ships tags (harmless in release). PR2 is runnable locally only. PR3 enables CI. Roll back by reverting PR3 (job, Dependabot, report needs), then PR2 (`/e2e`, hook), then PR1 (the identity modifier makes release behavior unchanged either way). `ci-gate` is never touched.

## Open Questions

- [ ] Should the `dev/allure` attachments be cleaned up once they exceed 50 MB (follow-up issue)?
- [ ] Should the `e2e-tests` timeout and the burn-in length be set before gating?
- [ ] Is the repository public (runner size, KVM) for SP4 timings?
