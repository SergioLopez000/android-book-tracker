```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:1623d0b02e22a33ea7c99c728df01526eef6d0705672ffbfa3cc3121058f5d5c
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 15/15
scenarios: 63/63
test_command: cd e2e && npm ci && npm run typecheck && npm run test:unit
test_exit_code: 0
test_output_hash: sha256:9a3cbadbf306d3fbbf219ceed0bbfd8789bd438a91fa110daa2113bfaad29baf
build_command: ./gradlew :app:testDebugUnitTest --rerun-tasks && ./gradlew :app:testReleaseUnitTest --tests com.librosleidos.ui.E2eRootReleaseTest && bash scripts/test-release-no-test-tags.sh && bash scripts/test-e2e-ci-config.sh
build_exit_code: 0
build_output_hash: sha256:ec578d761cc3f848e37b961da755eedbfa15a1b06ecf0ebed63843e5e8559a9e
```

## Verification Report

**Change**: e2e-appium (issue #7)
**Version**: N/A (one new spec `e2e-testing`; deltas for `ci-pipeline` and `code-quality-gates`)
**Mode**: Strict TDD
**Commit verified**: `dev` at fca9dd7 (plan #75, PR1 #76, PR2 #77, PR3 #78 merged). Evidence revision = sha256 of `git tree bd2d7f90d7f2541a80aa1f35cf790606a404d71f` plus the expected untracked inventory `sha256:da069611...b8e0b`.

Envelope counts: 15 requirements (e2e-testing 8, ci-pipeline 6, code-quality-gates 1) and 63 scenarios (21 + 29 + 13), counted from the `### Requirement:` / `#### Scenario:` headings. All 63 are counted complete: each has a passing test or CI run, or a recorded run. The scenarios backed only by a recorded manual run or by emulator/CI evidence are marked in the matrix (R, CI). Nothing is pending promotion.

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 53 |
| Tasks complete | 53 |
| Tasks incomplete | 0 |

All 53 boxes are `[x]` in `tasks.md` and match the code on `dev`: `E2eRoot.kt` (debug and release), the tag objects, `e2e/**`, `.nvmrc`, `e2e-tests` in `ci.yml`, the npm Dependabot entry, the `e2e-typecheck` hook, and `scripts/test-{release-no-test-tags,e2e-ci-config}.sh`. Tasks 3.6, 10.1 and 12.2 are checked with the recorded outcomes (3.6: no app fallback, caveat accepted; 10.1: SP4 PASS; 12.2: green PR run). `apply-progress.md` PR1 header still lists `[ ] 3.6` as pending, but `tasks.md` closes it (see S1).

### Build & Tests Execution
Node 24.22.3 (`nvm use`), JDK/Gradle as on the repo.

| Command | Exit | Result |
|---|---|---|
| `./gradlew :app:testDebugUnitTest --rerun-tasks` | 0 | 33/33 tasks executed; 126 tests, 0 failed, 0 errors, 0 skipped (XML totals) |
| `./gradlew :app:testReleaseUnitTest --tests com.librosleidos.ui.E2eRootReleaseTest` | 0 | BUILD SUCCESSFUL (2 tests; the up-to-date task is the same code) |
| `bash scripts/test-release-no-test-tags.sh` | 0 | "release test-tag checks passed" (release bytecode 0 hits, debug positive control >0) |
| `bash scripts/test-e2e-ci-config.sh` | 0 | "e2e CI config checks passed" |
| `actionlint` | 0 | clean |
| `shellcheck scripts/*.sh` | 0 | clean |
| `cd e2e && npm ci` | 0 | `package-lock.json` unchanged (`git status` clean) |
| `cd e2e && npm run typecheck` | 0 | `tsc --noEmit`, 0 errors |
| `cd e2e && npm run test:unit` | 0 | 20 tests, 20 pass, 0 fail |
| Lockfile drift probe (scratch copy, `appium` 3.9.1 -> 3.9.0 in `package.json`) | 1 | `npm ci` EUSAGE "package.json and package-lock.json are not in sync" |

**Hosted CI evidence (read-only `gh`)**:
| Run | Head | Result |
|---|---|---|
| 45176525899 (PR) | 0a6109a | `e2e-tests` failure, `ci-gate` success (non-gating proven), `report` success; artifact `allure-results-e2e` 57,890 B (PNG screenshots) |
| 45177627167 (PR) | 5ed924f | `e2e-tests` failure, `allure-results-e2e` 57,900 B |
| 45178670276 (PR) | b43158f | all jobs success incl. `e2e-tests` (2 passing), `ci-gate` success, `report` success, `publish-image` skipped; artifact `allure-results-e2e` 1,179 B (no PNG); report 153 tests |
| 45179795807 (PR) | b313507 | all jobs success incl. `e2e-tests` |
| 45175826475 (push dev) | a3e2064 (PR2 merge) | success |
| 45170275513 (push dev) | a388c3d (PR1 merge) | `unit-tests` failure: 11 test classes `classMethod FAILED`, cause `MavenArtifactFetcher ... IOException` (Robolectric android-all download); `ci-gate` failure. Not reproducible in the PR run before or after (see W1) |
| 45180858808 (push dev) | fca9dd7 (PR3 merge) | in progress at verification time: unit, backend, contract, secret-scan, ci-gate, publish-image success; `e2e-tests` still running (see W2) |

Repository visibility is public (`private: false`), which answers the SP4 open question (hosted runner and KVM available).

### TDD Compliance
| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | OK | "TDD Cycle Evidence" tables exist for PR1 (6 rows), PR2 (9 rows) and PR3 (2 rows) in `apply-progress.md` |
| All tasks have tests | OK | 17/17 table rows name a test file, harness or a recorded manual RED/GREEN (rows 12.1 docs and 8.4/9.x are non-file by nature) |
| RED confirmed (tests exist) | OK | Every named file exists: `BookListScreenTest`, `BookEditScreenTest`, `StarRatingTest`, `E2eRootDebugTest`, `E2eRootReleaseTest`, `test-release-no-test-tags.sh`, `pins/config/tags/structure/evidence .test.ts`, `bookLifecycle.e2e.ts`, `test-e2e-ci-config.sh` |
| GREEN confirmed (tests pass) | OK | 126/126 app, 2/2 release, 20/20 node, both shell harnesses exit 0, all re-run here |
| Triangulation adequate | OK | Multi-case: tag builders (7, 0, 12345), rating suffixes (4_5, 5_0, 0_5, 1_0, 3_5), `StarRating` tagged/untagged/display, two item ids, dialog on/off, status x4, config default/override/empty/APP_ID, evidence failed/passed/throwing, PR3 mutation set (8 mutations, each failing one named check). Row 7.4/7.5 (Page Objects) is covered by `tsc` plus the emulator run only (single path by nature) |
| Safety Net for modified files | OK | PR1 modified files show 102/102 and 114/114 baselines; new files show "N/A (new)"; PR3 `ci.yml` modification shows `test-image-config.sh` passing before and after |

**TDD Compliance**: 6/6 checks passed. The PR1 table uses plain values (e.g. "15/15", "Written, compile error") instead of the check-mark glyphs, which is the same information (see S2).

### Test Layer Distribution
| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit (JVM, no UI) | 2 | 1 | JUnit (`E2eRootReleaseTest`) |
| Integration (Compose, Robolectric) | 55 | 4 | Robolectric + Compose UI test (`BookListScreenTest` 15, `BookEditScreenTest` 14, `StarRatingTest` 12, `E2eRootDebugTest` 3, plus unchanged files) |
| Node unit (`node:test`) | 20 | 5 | `pins`, `config`, `tags`, `structure`, `evidence` |
| Static shell harness | 2 scripts | 2 | `test-release-no-test-tags.sh`, `test-e2e-ci-config.sh` |
| E2E (emulator, Appium) | 2 | 1 | WebdriverIO 10.2.0 / Appium 3.9.1 (hosted CI only, not re-run here) |
| **Total in scope** | **79 + 2 harnesses** | **13** | |

(The Robolectric count lists only the files changed by this change; the app suite total is 126.)

### Changed File Coverage
Coverage analysis skipped for this change: no coverage run was requested and the e2e/node and shell layers have no coverage tool. App Kover exists in CI (`coverage-unit` artifact present) but no per-file threshold applies. Informational only.

### Assertion Quality
Audit of the node, Robolectric and harness tests added by this change: no tautologies, no assertions without a production call, no smoke-only tests. Loops are guarded: `structure.test.ts` asserts `outside.length > 0` before iterating, and the two `StarRatingTest` `forEach` lists are literal non-empty. `evidence.test.ts` asserts attachment counts and the swallowed throw. Mock-heavy files: none.

**Assertion quality**: 0 CRITICAL, 0 WARNING.

### Quality Metrics
**Linter**: `actionlint` and `shellcheck` clean. **Type check**: `tsc --noEmit` 0 errors in `/e2e`. **Formatter**: not re-run (spotless owned by the hooks; app suite and Kotlin compile are green).

### Spec Compliance Matrix

Evidence legend: **T** passing automated test in this run; **CI** hosted run (ids above); **R** recorded in `apply-progress.md`, not re-executed here; **I** inspection of config/source.

#### e2e-testing (21 scenarios; 8 requirements)
| Requirement | Scenario | Evidence | Result |
|---|---|---|---|
| Test Tag Naming | List screen tagged | T `BookListScreenTest > tags_actionsAndFilters_*`, `tags_loadingState_*`, `tags_emptyState_*`, `tagBuilders_produceExpectedNames` (list container, add action, empty state, item) | COMPLIANT |
| Test Tag Naming | Edit screen tagged | T `BookEditScreenTest > tags_formControls_*`, `tags_ratingTapTargets_*`, `tags_fieldErrors_*`, `tags_searchResults_*`, `tags_searchStatus_*`; `StarRatingTest` suffix and tap-target tests | COMPLIANT |
| Test Tag Naming | Delete dialog tagged | T `BookListScreenTest > tags_deleteDialog_hasDialogAndBothButtonTags`, `tags_noPendingDelete_*` | COMPLIANT |
| Test Tag Naming | Per-item tags are unique | T `BookListScreenTest > tags_twoBooks_haveDistinctItemAndRemoveTags` | COMPLIANT |
| Debug-Only Resource-Id Exposure | Debug exposes tags as resource ids | T `E2eRootDebugTest` (3: activity root, tags under the flagged root, dialog window); R SP2 uiautomator dump on API 36 listing every list, dialog and edit id; CI green runs resolve elements by `resourceId` | COMPLIANT |
| Debug-Only Resource-Id Exposure | Release exposes nothing | T `scripts/test-release-no-test-tags.sh` exit 0 (greps `src/main` and `src/release` sources and release `.class` output; debug positive control >0) | COMPLIANT |
| Debug-Only Resource-Id Exposure | Release behavior unchanged | T `E2eRootReleaseTest` (identity and chained modifier) | COMPLIANT |
| Page Objects | Selectors encapsulated | T `structure.test.ts > selector strings appear only under src/pages and src/support`; I `grep` finds no selector in `src/support`, `wdio.conf.ts` or `src/tests` | COMPLIANT |
| Page Objects | Both screens covered | T `structure.test.ts > both Page Objects exist` | COMPLIANT |
| Happy-Path Flow | Full flow passes | CI runs 45178670276 and b313507: `bookLifecycle.e2e.ts` "adds, edits and deletes a book ending on the empty state" passing; R 6 local passes | COMPLIANT |
| Happy-Path Flow | Cancel keeps the book | CI same runs: "keeps the book when the delete dialog is cancelled" passing | COMPLIANT |
| Isolation and Offline Execution | Data reset between tests | CI/R: `beforeTest` `mobile: clearApp`, both tests start from the empty state in order cancel-then-lifecycle and pass; I `wdio.conf.ts` | COMPLIANT |
| Isolation and Offline Execution | No network needed | CI: job runs `adb shell cmd connectivity airplane-mode enable` before `npm run e2e`, passing; R 3 local offline passes | COMPLIANT |
| Isolation and Offline Execution | APK override | T `config.test.ts` (default, override, empty-string fallback) | COMPLIANT |
| Failure Evidence Policy | Failure attaches a screenshot | T `evidence.test.ts` (failed attaches one PNG); CI runs 45176525899 and 45177627167 uploaded 57 KB `allure-results-e2e` with PNGs; R SP3 render | COMPLIANT |
| Failure Evidence Policy | Pass attaches none | T `evidence.test.ts` (passed attaches 0 and never screenshots); CI run 45178670276 artifact 1,179 B, no PNG | COMPLIANT |
| Synthetic Data Only | Fixture data synthetic | I `e2e/src/data/books.ts` ("The Clockwork Teapot", "A. N. Example"); no credentials; CI `secret-scan` success. Review-by-inspection scenario, no automated check | COMPLIANT (inspection) |
| Pinned Dependencies and Runtime | No version ranges | T `pins.test.ts > every dependency version is exact` | COMPLIANT |
| Pinned Dependencies and Runtime | Pinned versions match | T `pins.test.ts` (Appium 3.9.1, driver 8.7.3, WDIO 10.2.0; every `@wdio/*` 10.2.0; `.nvmrc` 24.22.3 satisfies engines) | COMPLIANT |
| Pinned Dependencies and Runtime | Reproducible install | T `npm ci` exit 0, lockfile unchanged | COMPLIANT |
| Pinned Dependencies and Runtime | Lockfile drift fails | T drift probe exit 1 EUSAGE (this run); R 6.1-6.3 | COMPLIANT |

**e2e-testing: 21/21 covered** (17 T or T+CI, 3 CI/R-led, 1 inspection).

#### ci-pipeline (29 scenarios; 6 requirements)
| Requirement | Scenario | Evidence | Result |
|---|---|---|---|
| E2E Test Job | Runs on every trigger | T `test-e2e-ci-config.sh`; I `e2e-tests` has no `needs`, no path or label filters; CI PR and dev-push runs all include `e2e-tests` | COMPLIANT |
| E2E Test Job | Failure still uploads results | T harness (upload `if: always()`, `retention-days: 7`); CI failing runs 45176525899 and 45177627167 uploaded `allure-results-e2e` | COMPLIANT |
| E2E Test Job | Read-only and secret-free | T harness; I job `permissions: contents: read`, no `secrets.*` | COMPLIANT |
| E2E Test Job | Fork PR is safe | I runs under `pull_request`, `contents: read`, no secrets; no fork PR available to execute | COMPLIANT (inspection) |
| E2E Test Job | Non-gating | T harness (`ci-gate` needs exclude `e2e-tests`); CI run 45176525899: `e2e-tests` failure with `ci-gate` success | COMPLIANT |
| Workflow Triggers and Runner | PR triggers the pipeline | CI PR runs exist for every head; I `on: pull_request`, no path filters | COMPLIANT |
| Workflow Triggers and Runner | Push to dev triggers the pipeline | CI push runs 45170275513, 45175826475, 45180858808 | COMPLIANT |
| Workflow Triggers and Runner | Least privilege | T `test-e2e-ci-config.sh` and `scripts/test-image-config.sh` unchanged; I `e2e-tests` holds only `contents: read`, no `pull_request_target` | COMPLIANT |
| Artifact Contract | Unit suite artifacts | CI run 45178670276 artifacts `allure-results-unit`, `coverage-unit` | COMPLIANT |
| Artifact Contract | Backend suites artifacts | CI same run: `allure-results-backend`, `coverage-backend`, `allure-results-contract`, `coverage-contract`, `pit-contract` | COMPLIANT |
| Artifact Contract | E2E suite artifact | CI same run: `allure-results-e2e` exists, no `coverage-e2e`; T harness retention 7 | COMPLIANT |
| Artifact Contract | Unknown future suite is merged | I `report` downloads `pattern: allure-results-*` with `merge-multiple` (unchanged) | COMPLIANT |
| Report Generation and Publishing | PR report published | CI PR runs `report` success; `report-site` artifact present | COMPLIANT |
| Report Generation and Publishing | Dev report published | CI dev push `report` success (pre-existing, run a3e2064) | COMPLIANT |
| Report Generation and Publishing | Report on failing tests | CI run 45176525899: `e2e-tests` failed and `report` success | COMPLIANT |
| Report Generation and Publishing | Report waits for e2e | T harness (`report` needs includes `e2e-tests`); I `needs: [unit-tests, backend-tests, contract-tests, e2e-tests]` | COMPLIANT |
| Report Generation and Publishing | Backend results shown | CI PR run 45178670276 `report` success with backend and contract artifacts; unchanged logic | COMPLIANT |
| Report Generation and Publishing | E2E results shown | R/CI: report 153 tests (151 + 2 e2e) on run 45178670276; R SP3 failure screenshot rendered in Allure 2.46.2. The hosted gh-pages page itself was not opened | COMPLIANT (recorded) |
| Report Generation and Publishing | Missing results | T harness (Detect accepts `*-result.json`); pre-existing no-results path | COMPLIANT |
| Report Generation and Publishing | Fork PR | I unchanged `publish` condition | COMPLIANT |
| Report Generation and Publishing | Concurrent publishes | I unchanged `concurrency: gh-pages` | COMPLIANT |
| Out of Scope Boundaries | Only expected jobs | I `ci.yml` jobs: unit-tests, backend-tests, contract-tests, secret-scan, e2e-tests, report, ci-gate, publish-image | COMPLIANT |
| Out of Scope Boundaries | No forbidden tooling | I no SonarCloud, no `tsc` or other quality gate in any workflow (`tsc` appears only in `lefthook.yml`); e2e is the only extra suite | COMPLIANT |
| Dependabot Configuration | Config valid and scoped | T actionlint clean; I all entries weekly, `target-branch: dev`, prefixes, grouped, limits | COMPLIANT |
| Dependabot Configuration | Backend Gradle covered | I entry `/backend` gradle (unchanged) | COMPLIANT |
| Dependabot Configuration | Docker base image covered | I docker entry `/backend` (unchanged) | COMPLIANT |
| Dependabot Configuration | npm e2e covered | T harness (Dependabot dir `/e2e`); I `npm` `/e2e`, weekly Monday, `target-branch: dev` | COMPLIANT |
| Dependabot Configuration | Security features enabled | `gh api` read-only: vulnerability-alerts HTTP 204 (enabled); security updates recorded in the previous change | COMPLIANT |
| Dependabot Configuration | Retargeting documented | I `README.md:304` (Spanish) states security PRs target `main` and how to retarget | COMPLIANT |

**ci-pipeline: 29/29 covered.**

#### code-quality-gates (13 scenarios; 1 requirement)
| Requirement | Scenario | Evidence | Result |
|---|---|---|---|
| Pre-commit Hook | Misformatted staged file blocked | Pre-existing spotless job; I `lefthook.yml` | COMPLIANT |
| Pre-commit Hook | Misformatted staged backend file blocked | Pre-existing; I spotless target and glob include `backend/**` | COMPLIANT |
| Pre-commit Hook | Staged fake secret blocked | Pre-existing gitleaks job; also exercised in practice by the PR3 `generic-api-key` false positive on a literal AVD key (R deviation) | COMPLIANT |
| Pre-commit Hook | Broken workflow blocked | I actionlint job; T `actionlint` exit 0 on the new `ci.yml` | COMPLIANT |
| Pre-commit Hook | Clean commit passes | T actionlint, shellcheck, typecheck all exit 0; R PR commits landed through the hook | COMPLIANT |
| Pre-commit Hook | Android source change triggers Lint | Pre-existing; I glob `app/**` unchanged | COMPLIANT |
| Pre-commit Hook | Unrelated change skips Lint | Pre-existing; I glob | COMPLIANT |
| Pre-commit Hook | Staged Kotlin triggers detekt | Pre-existing; I glob `*.kt` | COMPLIANT |
| Pre-commit Hook | Hook bypass is an accepted risk for quality only | I no quality gate in CI; secret-scan success on all runs | COMPLIANT |
| Pre-commit Hook | Staged e2e TypeScript with a type error blocked | R 9.1-9.3: `lefthook run pre-commit` exit 0 before the job, exit 1 with the TS error and fix hint after, for `e2e/src/pages/base.page.ts` and `e2e/wdio.conf.ts`; I `lefthook.yml:22-24` (hint text). Manual recorded run, not re-executed here (see W3) | COMPLIANT (recorded) |
| Pre-commit Hook | Staged e2e TypeScript clean passes | R 9.x clean `.ts` exit 0; T `npm run typecheck` exit 0 | COMPLIANT (recorded) |
| Pre-commit Hook | Non-e2e change skips type check | R 9.x staged `.md` only skips the job; I glob `{e2e/*.ts,e2e/**/*.ts}` | COMPLIANT (recorded) |
| Pre-commit Hook | Type check not repeated in CI | T harness and `grep`: no `tsc` in any workflow | COMPLIANT |

**code-quality-gates: 13/13 covered.** Hook scenarios are verified by configuration inspection plus running each underlying tool; no automated test drives the lefthook runner.

#### Totals
| Spec | Scenarios | Covered | Recorded or inspection only | Not covered |
|---|---|---|---|---|
| e2e-testing | 21 | 21 | 1 (Synthetic Data, inspection) | 0 |
| ci-pipeline | 29 | 29 | 2 (Fork PR safe, E2E results shown) | 0 |
| code-quality-gates | 13 | 13 | 3 (e2e hook, manual recorded) | 0 |
| **Total** | **63** | **63** | **6** | **0** |

Requirements: 15 total, 15 evidenced. Only provable after the fact or manually: the real emulator run (CI evidence only, never re-run here), the lefthook e2e scenarios (recorded manual run), and the fork-PR scenario (inspection).

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|------------|--------|-------|
| Test Tag Naming | Implemented | `BookListTags`, `BookEditTags` objects; `StarRating` `tapTargetTagPrefix` + `ratingTagSuffix` (locale-free) |
| Debug-Only Resource-Id Exposure | Implemented | `E2eRoot.kt` in `src/debug` (flag) and `src/release` (identity); applied at the activity root and the dialog |
| Page Objects | Implemented | `bookList.page.ts`, `bookEdit.page.ts`, `base.page.ts`, `tags.ts` |
| Happy-Path Flow | Implemented | `bookLifecycle.e2e.ts` (cancel, then add/edit/delete to empty state) |
| Isolation and Offline Execution | Implemented | `clearApp` + `activateApp` in `beforeTest`, `E2E_APK_PATH`, airplane mode in CI |
| Failure Evidence Policy | Implemented | `evidence.ts` + `afterTest`; passing run 1 KB artifact |
| Synthetic Data Only | Implemented | `books.ts` fixtures |
| Pinned Dependencies and Runtime | Implemented | exact pins, committed lockfile, `.nvmrc` 24.22.3 |
| CI `e2e-tests`, report wiring, Dependabot npm | Implemented | `ci.yml:165`, `report needs`, `dependabot.yml:63`; `ci-gate` untouched |
| `e2e-typecheck` hook | Implemented | `lefthook.yml:22` |

### Coherence (Design)
| Decision | Followed? | Notes |
|----------|-----------|-------|
| `Modifier.e2eRoot()` in `src/debug` and `src/release` | Yes | Identity in release |
| Applied once at the activity root and inside the dialog | Yes | `E2eRootDebugTest` covers both windows |
| Release proof = shell harness plus identity test; CI step in the non-gating job | Yes | Harness runs inside `e2e-tests` before emulator work |
| ESM, strict `tsconfig`, Node type stripping | Yes | typescript 7.0.2 resolved at apply time, accepted |
| Driver as an exact devDependency, `APPIUM_HOME=<e2e>` | Yes | SP1 PASS, no fallback |
| Reset by `clearApp`, `enforceAppInstall` | Yes | |
| Evidence on failure only, no page source | Yes | |
| AVD API 34 `default` x86_64, KVM, AVD cache saved only on dev | Yes | SP4 PASS; AVD cache name moved to `env.AVD_CACHE` (gitleaks false positive), recorded deviation |
| `e2e-tests` non-gating, `ci-gate` untouched | Yes | CI-proven |
| Page Object item lookup `resourceIdMatches` + `childSelector(text)` | Yes (adapted) | SP2 caveat 1: item text is on a child node, handled in the Page Object |
| Rating via `element.click()` | Deviation, accepted | Page Object uses a coordinate tap (SP2 caveat 2, user-accepted known limitation) |
| `reveal()` closes the keyboard then scrolls (not in the design) | Addition | Needed for the 320x640 CI screen; recorded in 10.1 |
| PR size 800 budget | Deviation, accepted | PR1 792 and PR2 862 authored lines (`size:exception` accepted by the user); `package-lock.json` counted as generated; ledger budget reset for PR2 approved by the user |
| `android.onlyEnableUnitTestForTheTestedBuildType=false`, detekt `FunctionNaming` excludes `testDebug`/`testRelease` | Deviation, recorded | Needed for `testReleaseUnitTest` on AGP 9 |

### Issues Found
**CRITICAL**: None.

**WARNING**:
- **W1 Flaky Robolectric artifact download on a dev push**: run 45170275513 (a388c3d, PR1 merge) failed `unit-tests` with 11 class-level failures from `MavenArtifactFetcher` `IOException` (Robolectric android-all download), so `ci-gate` failed on `dev`. The same suite passed on the PR run before and every run after, and passes here (126/126). Infrastructure flake, not a code defect; consider Robolectric offline mode or an android-all cache if it repeats.
- **W2 Hosted dev run for the final commit not complete at verification time**: run 45180858808 (fca9dd7) had `e2e-tests` in progress; all other jobs were success. The same code passed on PR runs 45178670276 and b313507 and the PR3 branch is the merged code. Re-check once it finishes, and confirm the first dev-push AVD cache save (`Save AVD cache`) succeeds.
- **W3 e2e hook scenarios are manual and recorded**: the three `e2e-typecheck` scenarios (blocked, clean, skip) rest on the recorded `lefthook run pre-commit` runs (apply-progress 9.1-9.3). No automated test drives the hook runner (same pattern as the existing hooks).
- **W4 Known and user-accepted items (report only)**: (1) SP2 half-star bounds limitation: Robolectric bounds are correct and the Page Object uses a geometry tap, so the rating target `book_edit_rating_*` cannot be clicked directly on a device; (2) PR1 792 and PR2 862 authored lines with `size:exception` accepted, `package-lock.json` as generated; (3) ledger budget reset for PR2 approved; (4) `e2e-tests` is intentionally non-gating, so the release no-test-tags proof in CI is also non-gating and a release-tag regression shows as a red job, not a blocked merge.

**SUGGESTION**:
- S1 `apply-progress.md` PR1 header still shows `[ ] 3.6 SP2 fallback: NOT applied; pending...` while `tasks.md` closes it with the accepted limitation; align the text at archive.
- S2 The PR1 TDD table uses plain values instead of the check-mark glyphs of the format; content is complete, so no action beyond consistency.
- S3 `structure.test.ts` allows selector markers in `src/support` as well as `src/pages`, while the spec says selectors appear only inside Page Object files. Today `src/support` has none (verified), so tightening `ALLOWED_DIRS` to `pages` would match the spec literally.
- S4 Open design questions remain: gh-pages attachment growth above 50 MB, the `e2e-tests` timeout and burn-in length before it becomes gating.
- S5 Spec "Debug exposes tags as resource ids" for the dialog is covered by Robolectric and one recorded SP2 dump; an on-device regression is only caught by the CI e2e run (non-gating). Consider moving `e2e-tests` to gating after a burn-in period.
- S6 The main specs (`ci-pipeline`, `code-quality-gates`) need the MODIFIED text merged at archive, plus `project-conventions.md` line 22 (activity-root convention), as listed in the design.

### Verdict
PASS WITH WARNINGS

No CRITICAL issues: 53/53 tasks done, app (126), release (2), node (20) and both shell harnesses green on this revision, all 63 scenarios mapped (57 with a passing test or CI run, 6 recorded or inspection-only), and the hosted PR runs prove failing-run screenshots, non-gating behavior and a passing happy path. Review W1-W4 and archive.
