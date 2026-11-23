# Apply Progress: e2e-appium (#7) - PR1 (tasks 1.1-4.6)

**Mode**: Strict TDD | **Branch**: `feat/7-e2e-appium-app` (stacked on `docs/7-e2e-appium-plan`)

## Completed Tasks
- [x] 1.1 SP2 resource-id dump (PASS with caveats, see below)
- [x] 2.1-2.7 tags (commit 8c664eb)
- [x] 3.1-3.5 debug-only e2eRoot (commit 9506f7f)
- [ ] 3.6 SP2 fallback: NOT applied; pending orchestrator/user decision on the caveats
- [x] 4.1-4.5 release proof harness (commit 015d48a)
- [x] 4.6 verification record (this file)

PR2 and PR3 tasks untouched.

## TDD Cycle Evidence
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2.1 | `ui/list/BookListScreenTest.kt` | Robolectric | 102/102 | Written, compile error (`BookListTags`) | 15/15 | 7 new cases (loading vs empty vs list, 2 ids, dialog on/off) | Clean |
| 2.2 | `ui/edit/BookEditScreenTest.kt` | Robolectric | 102/102 | Written, tags absent | 14/14 | 9 new cases (errors present/absent, results indexed, status x4) | Clean |
| 2.3/2.5/2.6 | `ui/components/StarRatingTest.kt` | Robolectric + unit | 102/102 | Written, compile error (`ratingTagSuffix`, `tapTargetTagPrefix`) | 12/12 | suffix 4_5/5_0/0_5 plus 1_0/3_5; tagged vs untagged vs display | detekt `TopLevelPropertyNaming` fix: dropped the constant, `%.1f` + replace |
| 3.1 | `testDebug/.../E2eRootDebugTest.kt` | Robolectric | 114/114 | 3 tests failed (property absent) | 3/3 | activity root, ancestor of add button, dialog window | Clean |
| 3.2 | `testRelease/.../E2eRootReleaseTest.kt` | JUnit | N/A (new variant) | compile error (`e2eRoot` unresolved) | 2/2 | identity + chained modifier | Clean |
| 4.1-4.3 | `scripts/test-release-no-test-tags.sh` | Shell harness | N/A (new) | exit 1 at debug positive control | exit 0 | leak probe in `src/main`: exit 1 naming the file and the class; reverted: exit 0 | shellcheck clean; class dirs fixed (AGP built-in kotlinc) |

Total: app suite 102 -> 126 tests, 0 failures (`:app:testDebugUnitTest`); `:app:testReleaseUnitTest --tests E2eRootReleaseTest` 2/2.

## Work Unit Evidence
| Evidence | Value |
|---|---|
| Focused tests | `./gradlew :app:testDebugUnitTest` 126/126; `:app:testReleaseUnitTest --tests com.librosleidos.ui.E2eRootReleaseTest` 2/2; `spotlessCheck` clean |
| Runtime harness | `bash scripts/test-release-no-test-tags.sh` exit 0 ("release test-tag checks passed"); SP2 on a real emulator (below) |
| Rollback | Revert the 3 commits; release unaffected (identity modifier) |

## SP2 (emulator, API 36, Phone_Speckit, debug APK, uiautomator dump)
Resource-ids confirmed: `book_list_add_button`, `book_list_filter_*`, `book_list_empty_state`, `book_list`, `book_list_item_1`, `book_list_remove_button_1`, `book_list_delete_dialog`, `book_list_delete_confirm_button`, `book_list_delete_cancel_button`, all `book_edit_*` and `book_edit_rating_*`.
Caveats:
1. Item text is on a CHILD `TextView` (`text="The Clockwork Teapot"`, empty resource-id); the item node has `text=""`. `resourceIdMatches(item).textContains(title)` on one node will not match. Needs the design `childSelector(text)` fallback (Appium side, no app change).
2. Rating half-target bounds are shifted one slot (42px) left in the dump (`book_edit_rating_4_5` = [336,378], real star half at [378,420]). Tapping the centre of the reported 4_5 bounds (x=357) saved 4.0; tapping x=399 saved 4.5. A plain `element.click()` on `book_edit_rating_4_5` is therefore expected to set the wrong value; PR2 needs a verified approach (coordinate tap or an app-side fix).

## Deviations from design
- `gradle.properties`: `android.onlyEnableUnitTestForTheTestedBuildType=false` (AGP 9 creates no `testReleaseUnitTest` otherwise).
- `config/detekt/detekt.yml`: FunctionNaming excludes `testDebug` and `testRelease`.
- Harness greps `app/build/intermediates/built_in_kotlinc/{debug,release}/compile*Kotlin/classes` (AGP 9 output path).
