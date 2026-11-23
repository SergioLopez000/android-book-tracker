# Exploration: e2e-appium (#7)

## Summary

Add an Appium (UiAutomator2) E2E suite with Page Objects. The app needs `testTag`s and a debug-only `testTagsAsResourceId`, and CI needs a new emulator job that publishes into the existing Allure report. The recommended client is TypeScript/WebdriverIO in a new `/e2e` npm project, which matches the user's reference project. The `e2e-tests` job runs on every PR and `dev` push and starts as non-gating. Delivery is 3 chained PRs of at most 800 changed lines each, with `package-lock.json` treated as generated.

## Current State (verified)

### App UI and data

- **Compose root.** There is one root: `MainActivity.onCreate` calls `setContent { RinconDeLecturaTheme { Scaffold { Surface { LibrosLeidosNavHost } } } }` (`app/src/main/java/com/librosleidos/MainActivity.kt:43-53`).
  - The NavHost starts at `list` and has the route `edit?bookId={bookId}` (`MainActivity.kt:57-100`).
  - Add mode uses the sentinel -1 (`MainActivity.kt:64-66`).
- **List screen** (`ui/list/BookListScreen.kt`):
  - "Add book" `TextButton` (L53).
  - Date-range filter buttons (L61-73).
  - Loading spinner and the empty text "No books yet. Add one to get started." (L78-85).
  - `LazyColumn` of items (L87-95). Each item is a `Column.clickable` (L127) with the cover, title, author, `StarRating`, date, review and a "Remove" `TextButton` (L142).
  - Delete confirmation is an `AlertDialog` (L100-115) with "Delete" and "Cancel".
- **Edit screen** (`ui/edit/BookEditScreen.kt:43-85`):
  - Search field, "Search" button and `SearchResults` buttons.
  - Title, Author and Comment `OutlinedTextField`s (L64, L69, L76).
  - `StarRating` and error `Text` nodes (L67, L74, L80).
  - "Cancel" and "Save" (L83-84). Saving emits `SaveCompleted`, which pops the back stack (`MainActivity.kt:97`).
  - `dateFinished` defaults to `LocalDate.now()` (`BookEditUiState.kt:23`) and there is no date picker.
- **Rating input.** `StarRating` has half and full tap targets with `contentDescription = "Set rating %.1f"` (`ui/components/StarRating.kt:109-122`). The accessibility id `~Set rating 4.0` can be a fallback; a stable tag is preferred.
- **Existing tags and tests.**
  - There are no `testTag` or `testTagsAsResourceId` usages in `app/src`.
  - There is no `androidTest` source set. UI tests are Robolectric Compose tests under `app/src/test` (`ComposeSpikeTest`, `BookListScreenTest`, `BookEditScreenTest`, `MainActivitySmokeTest`).
  - The convention already exists (`.claude/skills/compose-design/references/project-conventions.md:20-24`): `testTagsAsResourceId` goes on the root of each Route, tags use snake_case `<screen>_<element>[_<id>]`, and every interactive element and state container gets a tag.
- **Build types.**
  - Only `debug` and `release` exist, with `release` `isMinifyEnabled = false` (`app/build.gradle.kts:40-44`).
  - `buildConfig = true` (L51-54).
  - `applicationId` is `com.sergiolopez.rincondelectura`; minSdk 26, targetSdk 36, compileSdk 37.
  - There are no flavors. `debugImplementation` is already used (L115, L146).
- **Data.** Room "books.db" (`di/AppContainer.kt:47-51`).
- **Network.** Add, list, edit and delete make no network calls.
  - Metadata lookups (`ChainedMetadataSource(OpenLibrary, GoogleBooks)`, `AppContainer.kt:55-64`) fire only when the user taps Search (`BookEditViewModel.kt:107-114`).
  - Coil loads a cover only when `coverUrl` is set (`BookCoverImage.kt:38`).
- **Gotcha.** `AlertDialog` renders in its own window, so a root-level `testTagsAsResourceId` does not cover it. The dialog content needs the flag too (spike).

### CI

- **`ci.yml` basics.** Triggers are `pull_request` and `push: dev` (L10-13). Default permissions are `contents: read` (L15-16). Concurrency cancels in-progress runs on PRs (L18-20).
- **Jobs.** `unit-tests`, `backend-tests`, `contract-tests`, `secret-scan`, `report`, `ci-gate` and `publish-image`.
- **Artifacts.** `allure-results-*` (JUnit XML), `coverage-*` and `pit-*`, all with `retention-days: 14`.
- **`report`** (`needs: [unit-tests, backend-tests, contract-tests]`, `if: !cancelled()`):
  - Merges `allure-results-*` with `merge-multiple` (L185-191).
  - The "Detect results" step only checks `*.xml` (L207).
  - Runs Allure 2.46.2 pinned by sha (L175-176, L231).
  - Publishes to gh-pages with `peaceiris/actions-gh-pages@v4`, `keep_files: true`, under `pr-N` or `dev` (L289-296).
  - Never publishes for Dependabot (L271-275).
  - Posts a sticky PR comment (L297-321).
- **`ci-gate`** needs `unit-tests`, `backend-tests`, `contract-tests` and `secret-scan` (L325-337).
- **`codeql.yml`** analyzes `java-kotlin` only (L34-53).
- **`dependabot.yml`** covers gradle `/`, gradle `/backend`, docker `/backend` and github-actions. There is no `npm` entry.
- **`lefthook.yml`** runs spotless, android-lint, detekt, gitleaks, actionlint, shellcheck and the conventional-commit check. Nothing covers TypeScript.

### Specs touched

- **`openspec/specs/ci-pipeline/spec.md`:**
  - Workflow Triggers: no path filters, actions pinned by major (L9).
  - Artifact Contract: "future suites (E2E) attach without changing the report job" (L62-63).
  - Report job `needs` is explicit (L80-81).
  - ci-gate depends only on the four listed jobs (L136-137).
  - **Out of Scope Boundaries forbids E2E jobs (L241-242); it MUST be MODIFIED.**
  - Dependabot ecosystems are listed explicitly (L328-329).
- **Prior statements reserving E2E for #7:** `README.md:277`, `openspec/changes/archive/2026-11-17-pr-pipeline-with-reports/explore.md:7`, and `openspec/config.yaml:35` (`e2e: { available: false, tool: null }`, to update).
- **`book-entry` and `book-library`** define the happy-path behavior. They need no delta unless the UI behavior changes.
- **`code-quality-gates`** has no E2E or npm mention. A local-only `tsc --noEmit` is optional; CI stays free of static analysis (L26).

### Reference project (`appium-ts`, read-only)

- **Stack.** WDIO 9, Mocha, `@wdio/appium-service` and the UiAutomator2 driver, with TypeScript strict and CommonJS. Reporters are spec and Allure (`outputDir ./allure-results`, `wdio.conf.ts:19-29`).
- **Dependency ranges.** `@wdio/*` ^9.27.0, `appium-uiautomator2-driver` ^7.1.0 and TypeScript ~5.9. `appium` is not listed explicitly, and caret ranges conflict with this repo's pinning rule.
- **Layout.** `src/pages/*.page.ts` holds singleton Page Objects over an abstract `BasePage`, tests live in `src/tests/*.e2e.ts`, plus `src/support/{session,evidence,rootHooks}.ts` and base and android WDIO configs.
- **Failure evidence.** A screenshot and the page source are attached to Allure (`evidence.ts:9-31`).
- **Lessons.** Evidence can contain personal data. Tests must restore the data they change. Specs never use `$` directly.
- **Not needed here.** Libros Leidos has no login and no WebView, so the session and user machinery is unnecessary.

## Affected Areas

- `app/.../MainActivity.kt`: debug-only root modifier.
- `app/.../ui/list/BookListScreen.kt`: tags, plus the flag on the dialog content.
- `app/.../ui/edit/BookEditScreen.kt`: tags.
- `app/.../ui/components/StarRating.kt`: a tag parameter for the star targets.
- New `app/src/debug/.../E2eTestTags.kt` and `app/src/release/.../E2eTestTags.kt` (or `BuildConfig.DEBUG`).
- `app/src/test/...`: Robolectric tests for the tags and the root flag.
- New `/e2e/`: package files, tsconfig, wdio config, Page Objects, happy-path spec, evidence helper and README.
- `.github/workflows/ci.yml`: the `e2e-tests` job and the `report` needs.
- `.github/dependabot.yml`: an `npm` entry for `/e2e`.
- `.gitignore`.
- `openspec/specs/ci-pipeline/spec.md`: deltas.
- `README.md` and `openspec/config.yaml`.
- `codeql.yml` (optional).

## Proposed Tag Map (`<screen>_<element>[_<id>]`)

| Screen | Tags |
|---|---|
| list | `book_list_add_button`, `book_list_filter_last_month`, `book_list_filter_last_year`, `book_list_filter_all_time`, `book_list_loading`, `book_list_empty_state`, `book_list`, `book_list_item_<id>`, `book_list_remove_button_<id>`, `book_list_delete_dialog`, `book_list_delete_confirm_button`, `book_list_delete_cancel_button` |
| edit | `book_edit_search_field`, `book_edit_search_button`, `book_edit_title_field`, `book_edit_author_field`, `book_edit_review_field`, `book_edit_rating_<value>`, `book_edit_save_button`, `book_edit_cancel_button`, `book_edit_error_<field>` |

The id of a newly saved book is unknown to the test, so the Page Object finds the item by title or by `resourceIdMatches(".*book_list_item_\\d+")`.

## Approaches

### (a) Appium client language

| Option | Pros | Cons | Effort |
|---|---|---|---|
| **A1. TypeScript + WebdriverIO in `/e2e` (recommended)** | Matches the reference conventions. The Appium service starts the server. The Allure reporter attaches screenshots. Appium is npm-based anyway. | Adds a Node toolchain and a Dependabot `npm` entry. Results are Allure JSON, not JUnit XML. | Medium |
| A2. Kotlin/Java client in a Gradle module | One language, and Dependabot gradle already covers it. JUnit XML fits the existing Detect step. | The Appium server needs npm anyway. Screenshots need allure-junit5 plus AspectJ. A root-build module is configured on every task, or a third build is needed. | Medium-High |

### (b) Emulator in GitHub Actions

| Option | Pros | Cons | Effort |
|---|---|---|---|
| **B1. `reactivecircus/android-emulator-runner@v2` (recommended)** | v2.38.0 (2026-07-05). Supports a KVM udev step, `x86_64` and AVD caching (snapshot on cache miss, then `no-snapshot-save`). `@v2` fits the major-pin convention. | Boot costs minutes per run, and flakiness is a known issue. | Medium |
| B2. Firebase Test Lab or a device farm | No emulator to maintain. | Paid and needs secrets, which conflicts with "no paid tooling" and fork safety. | High |
| B3. Self-hosted or macOS runners | None. | Slower and costlier. | High |

- **Spike settings.** API 34 (or the highest stable), `x86_64`, a `default` or `google_apis` target, `disable-animations: true`, headless.
- **AVD cache.** It is branch-scoped: build it on `dev` pushes and restore it read-only on PRs.
- **Runtime.** Unmeasured; roughly 8-15 minutes per PR.
- **Trigger.** Every PR and `dev` push (no path filters, per spec L9). A filtered gating job would be skipped, and a skipped job fails `ci-gate`.
- **Gating.** Start non-gating (only in the `report` needs) for a flake burn-in, then make it gating in a follow-up delta.

### (c) Allure integration

- The new artifact is `allure-results-e2e` (WDIO Allure JSON plus attachments) with `retention-days: 7`.
- Add `e2e-tests` to the `report` needs (L165).
- The Detect step stays as is: it only matches `*.xml`, so an E2E-only run would be skipped, which is acceptable.
- Spike: check that Allure 2.46.2 renders `allure-js-commons` ^3.10 JSON (from `@wdio/allure-reporter` 10.2.0) next to the JUnit XML. If it does not, use the spec's Allure 3 escape hatch (L81) or pin a 2-compatible reporter.
- `merge-multiple` causes no collisions because result files are named by UUID.
- **gh-pages growth.** `keep_files: true` keeps stale failure screenshots under `dev/allure`. Mitigate by attaching screenshots on failure only and optionally clearing `dev/allure` before publishing (decide in design). `pr-<N>/` is already cleaned by `pr-cleanup.yml`.

### (d) Data reset and network avoidance

- **Reset.** Run `mobile: clearApp`, or `pm clear com.sergiolopez.rincondelectura`, before each test and then relaunch the app. Avoid `fullReset`.
- **Network.** The manual-entry happy path needs no stubbing. A future search test would need a debug-only fake `BookMetadataSource` behind the `AppContainer` seam (`AppContainer.kt:55-64`).

## Recommendation

1. **App.** Add the tags with Strict TDD using Robolectric (`onNodeWithTag`).
   - Add a debug/release source-set pair that defines `Modifier.e2eRoot()`. In debug it sets `semantics { testTagsAsResourceId = true }`; in release it returns the modifier unchanged. Release bytecode then contains no reference to the flag.
   - Apply it at the `MainActivity` root and inside the dialog content.
2. **Client.** TypeScript/WebdriverIO in `/e2e`.
   - Pin exact versions, commit the lockfile and install with `npm ci`.
   - Add the drivers as devDependencies and let the Appium service start the server.
   - Copy the BasePage, Page Object and evidence shape from `appium-ts` without the session machinery, and add `clearApp` per test.
3. **Test.** One happy path: add a book manually, check it in the list, edit it, check again, delete it through the dialog, then check the empty state.
4. **CI.** An `e2e-tests` job on `ubuntu-24.04`:
   - `contents: read`, and no secrets.
   - JDK 17 and `setup-gradle` with a read-only cache off `dev`, then `:app:assembleDebug`.
   - `setup-node` with a pinned Node version, then `npm ci`.
   - The KVM step, then `android-emulator-runner@v2` with a script that runs WDIO.
   - Upload `allure-results-e2e` with `retention-days: 7` and `if: always()`.
   - Add it to the `report` needs and keep it out of `ci-gate` at first.
5. **Dependabot.** An `npm` entry for `/e2e`: weekly, `target-branch: dev`, prefix `build`, `include: scope`, and groups `wdio`, `appium` and `allure`.
6. **Specs.** `ci-pipeline` deltas:
   - MODIFIED: Out of Scope Boundaries, Artifact Contract, the Report job `needs`, least privilege, and Dependabot.
   - ADDED: E2E Test Job.
   - ci-gate, only if gating is chosen.

## Verified Versions (npm registry and GitHub API, 2026-11-23)

| Component | Version | Notes |
|---|---|---|
| `appium` | 4.0.0 | engines node `^22.22.2 \|\| ^24.15.0 \|\| >=26.0.0`, npm >=10 |
| `appium-uiautomator2-driver` | 10.0.0 | peer `appium ^4.0.0-beta.8` |
| `webdriverio`, `@wdio/cli` | 10.2.0 | `@wdio/cli` node `>=22.23.4 <23 \|\| >=24.22.1 <25 \|\| >=26.12.1` |
| `@wdio/appium-service` | 10.2.0 | `appium` is an optional peer `>=3` |
| `@wdio/allure-reporter` | 10.2.0 | depends on `allure-js-commons ^3.10.0` |
| `reactivecircus/android-emulator-runner` | v2.38.0 (2026-07-05) | pin `@v2` |
| Reference `appium-ts` | wdio ^9.27, uiautomator2 ^7.1 | Appium 3 era |

Not verified:

- Appium 4 and driver 10 stability in practice.
- Allure 2.46.2 compatibility with `allure-js-commons` 3.
- Whether `testTagsAsResourceId` requires an opt-in annotation.
- System-image availability per API level.
- Runner vCPU and KVM availability, which depend on repo visibility.

## Open Decisions (for the proposal gate)

1. Client language: TypeScript/WebdriverIO (recommended) vs a Kotlin/Java client.
2. Debug-only mechanism: a `src/debug`/`src/release` pair (recommended) vs `BuildConfig.DEBUG`.
3. Gating: non-gating at first (recommended) vs added to `ci-gate` now.
4. Trigger scope: every PR and `dev` push (recommended) vs a path or label filter.
5. Allure screenshots: on failure only (recommended), and whether to clear stale `dev/allure` files.
6. E2E artifact retention: 7 days (recommended) vs 14 days.
7. Emulator API level and image (decided by the spike).
8. Node version source (`.nvmrc` vs a workflow input) and the exact-pin policy.
9. Lockfile and the 800-line budget: treat `package-lock.json` as generated.
10. CodeQL `javascript-typescript` for `/e2e`.
11. A local-only `tsc --noEmit` in lefthook, or leave it manual.
12. An `ANDROID_APP_PATH`-style override for local runs.

## Risks

- **New major versions.** Appium 4.0.0 and driver 10.0.0 are a major jump from the reference stack. Spike a local run first; the fallback is Appium 3 with wdio 9.
- **Strict Node engines.** Pin the runner's Node version explicitly, or `npm ci` may fail.
- **Resource-id exposure.** It may not cover the dialog window or nodes merged into the clickable list item; check the "Remove" button with a page-source dump.
- **Emulator flakiness and cost.** Mitigate with a non-gating start, retries limited to infrastructure steps, and `disable-animations`.
- **Allure compatibility.** WDIO JSON next to JUnit XML is unverified, and the Detect step only matches `*.xml`.
- **gh-pages growth.** `keep_files: true` keeps old screenshots under `dev/`.
- **Synthetic data.** There are no accounts, so "test accounts" reduces to synthetic book data. Page source can contain typed text, so keep the data synthetic.
- **Spec conflict.** `ci-pipeline` forbids E2E jobs (L241-242), so the change must carry the delta.
- **Lockfile size.** `package-lock.json` alone can exceed the 800-line budget.
- **Repo visibility.** It is unknown, and it affects the runner size and KVM.
- **Stale context.** `openspec/config.yaml` still has `e2e: available false`.

## Rough PR Slicing (chained into `dev`, each with CI green)

| PR | Scope | Estimate |
|---|---|---|
| 1 | App: tags, debug/release root modifier, dialog flag, `StarRating` tag parameter, Robolectric tests | ~250 lines |
| 2 | `/e2e` project: configs, Page Objects, happy-path spec, evidence helper, `.gitignore`, README (lockfile counted as generated) | ~400 lines |
| 3 | CI `e2e-tests` job, `report` needs, Dependabot `npm`, `ci-pipeline` deltas, README and `openspec/config.yaml` | ~200 lines |

- PR1 proves the debug-only criterion before any CI cost.
- PR2 runs locally against an emulator.
- PR3 brings the emulator cost and the spec delta together, so `dev` never has a spec that forbids a job that exists.

## Ready for Proposal

Yes, once the open decisions are confirmed. Early spikes for design:

1. Appium 4 and driver 10 against a local debug APK.
2. Resource-id exposure in the dialog and the list item.
3. Allure 2.46.2 rendering WDIO JSON.
4. Emulator API level and boot time on `ubuntu-24.04`.
