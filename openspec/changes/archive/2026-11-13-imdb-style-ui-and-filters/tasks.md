# Tasks: IMDb-Style UI and Date-Range Filters

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~700-950 (5 slices, new components + tests + DAO + theme) |
| 400-line budget risk | Medium (per-slice Low, cumulative Medium) |
| Chained PRs recommended | Yes |
| Suggested split | PR1 Theme+Manifest -> PR2 StarRating -> PR3 Coil/Cover -> PR4 Date-range domain/DAO -> PR5 Date-range UI |
| Delivery strategy | ask-on-risk |
| Chain strategy | stacked-to-main |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Dark theme + manifest fix | PR1 | `./gradlew :app:testDebugUnitTest --tests "*ThemeTest"` | Robolectric compose smoke render | Revert `ui/theme/`, `MainActivity.kt`, manifest/themes.xml |
| 2 | StarRating + edit/list wiring | PR2 | `./gradlew :app:testDebugUnitTest --tests "*StarRating*" --tests "*BookEditScreenTest*"` | Robolectric + createComposeRule | Revert `StarRating.kt`, edit/list callers, restore RatingInput |
| 3 | Coil3 pin + spike + BookCoverImage | PR3 (split PR3a toolchain + PR3b Coil/cover) | `./gradlew :app:testDebugUnitTest --tests "*BookCoverImage*" --tests "*CoilTestRule*"` | Robolectric + CoilTestRule/FakeImageLoaderEngine | Revert Gradle pin (2 lines) + `BookCoverImage.kt` |
| 4 | Date-range domain -> DAO | PR4 | `./gradlew :app:testDebugUnitTest --tests "*GetBooksUseCaseTest*" --tests "*BookDaoTest*"` | Robolectric inMemoryDatabaseBuilder | Revert `DateRangeFilter.kt`, DAO/repo/use-case param |
| 5 | Date-range UI wiring | PR5 | `./gradlew :app:testDebugUnitTest --tests "*BookListViewModelTest*" --tests "*BookListScreenTest*"` | coroutines-test+Turbine; Robolectric compose | Revert bucket selector + UiState/VM field |

## Phase 1: Dark Theme & Manifest (PR1)

- [x] 1.1 (scaffold) Create `ui/theme/Color.kt` — dark `ColorScheme` values
- [x] 1.2 (scaffold) Create `ui/theme/Type.kt` — `AppTypography`
- [x] 1.3 (scaffold) Create `ui/theme/Theme.kt` — parameterless `RinconDeLecturaTheme(content)` = `MaterialTheme(DarkColorScheme, AppTypography, content)` (D12)
- [x] 1.4 Modify `MainActivity.kt`: `MaterialTheme {` -> `RinconDeLecturaTheme {`
- [x] 1.5 Modify `AndroidManifest.xml` + `app/src/main/res/values/themes.xml`: `android:theme` from `Theme.Material.Light.NoActionBar` to a dark NoActionBar platform theme

## Phase 2: StarRating Component (PR2) — satisfies spec `star-rating`

- [x] 2.1 RED: `StarRatingTest` — display: `rating=4.0f` -> 4 filled/1 empty; `rating=3.5f` -> 3 filled/1 half/1 empty; no numeric text (spec scenarios)
- [x] 2.2 GREEN: create `ui/components/StarRating.kt` — display mode, fraction-clip drawing (D10)
- [x] 2.3 RED: `StarRatingTest` — input: tap sets 0.5-increment value; result always 0.5-5.0
- [x] 2.4 GREEN: extend `StarRating.kt` — input mode via `onRatingChange`, half-width tap targets, `contentDescription="Set rating N"`
- [x] 2.5 REFACTOR: dedupe display/input drawing path
- [x] 2.6 Modify `ui/edit/BookEditScreen.kt`: delete private `RatingInput`, call `StarRating`
- [x] 2.7 Modify `ui/list/BookListScreen.kt` (item): replace numeric rating text with `StarRating`
- [x] 2.8 Update `BookEditScreenTest`: migrate numeric `"0.5"` TextButton assertions to star semantics (expected churn, not regression)

## Phase 3: Coil3 + BookCoverImage (PR3, delivered as PR3a toolchain bump + PR3b Coil wiring) — satisfies spec cover scenarios

- [x] 3.1 Modify `gradle/libs.versions.toml` + `app/build.gradle.kts`: pin `coil="3.6.1"`, add `coil-compose` + `coil-network-okhttp`, `testImplementation(coil-test)` (D9) — delivered in PR3a `pr3a/toolchain-bump` @ `b568a02` (as a dependency-graph smoke test alongside the toolchain bump; reused as-is by PR3b, no re-add)
- [x] 3.2 Verify `3.6.1` on Maven Central; run `./gradlew :app:dependencies --configuration debugRuntimeClasspath`, confirm all `androidx.compose.*` resolve to BOM `2026.08.00` (D9 gate, version corrected from original `2025.09.01` by the toolchain bump) — verified in PR3a (`:app:dependencies` clean resolution + `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` BUILD SUCCESSFUL); re-confirmed on PR3b's own branch via the full test run below
- [x] 3.3 Spike (timebox ~1h, D13): create `app/src/test/.../ui/CoilTestRule.kt` — `FakeImageLoaderEngine` via `SingletonImageLoader.setSafe` in `before()`, reset in `after()`; prove one Robolectric compose test loads a fake image — delivered in PR3b `pr3b/book-cover-image` @ `a5527f8`. Spike test `CoilSpikeTest` PASSED first attempt (AsyncImage reaches `onSuccess` via the fake engine) — D13 risk resolved, kept permanently as a regression spike (mirrors `ComposeSpikeTest`/R1 precedent)
- [x] 3.4 RED: `BookCoverImageTest` — `coverUrl==null` -> placeholder; non-https `coverUrl` -> placeholder (threat-matrix carry-forward); valid https -> loaded image, via `CoilTestRule` — delivered in PR3b @ `a5527f8`. Confirmed RED: compile failure (`Unresolved reference 'BookCoverImage'`) before 3.5
- [x] 3.5 GREEN: create `ui/components/BookCoverImage.kt` wrapping `AsyncImage`; null/blank/non-https short-circuit before Coil (D11) — delivered in PR3b @ `a5527f8`. 3/3 tests passing after minimal implementation, no further triangulation needed (3 tests already exercise both branches)
- [x] 3.6 Modify `ui/list/BookListScreen.kt` (item) + `ui/edit/BookEditScreen.kt`: render `BookCoverImage` — delivered in PR3b @ `a5527f8`. No dedicated RED task per design (matches Phase 2's 2.6/2.7 wiring-task precedent); existing `BookListScreenTest` (7/7) and `BookEditScreenTest` (5/5) re-run green as the regression guard, confirming the new composable didn't break existing screen assertions

## Phase 4: Date-Range Domain -> DAO (PR4) — satisfies spec `Filter by Date Range`

- [x] 4.1 RED: `GetBooksUseCaseTest` — LAST_MONTH/LAST_YEAR/ALL_TIME -> epoch-day bound via `Clock.fixed`, incl. 30/365-day boundary
- [x] 4.2 GREEN: create `domain/model/DateRangeFilter.kt` enum; modify `GetBooksUseCase` — inject `Clock`, resolve bucket -> `fromEpochDay: Long?`
- [x] 4.3 Modify `domain/port/BookRepository.kt`: add `fromEpochDay: Long?` to `observeBooks` (D8)
- [x] 4.4 RED: `BookDaoTest` — 6 sort x bucket combos + AND with `minRating`; `ALL_TIME` null bind returns all, via Robolectric `inMemoryDatabaseBuilder`
- [x] 4.5 GREEN: modify `data/local/BookDao.kt` — nullable-bind predicate `(:fromEpochDay IS NULL OR dateFinishedEpochDay >= :fromEpochDay)` on both `@Query` methods (D8)
- [x] 4.6 GREEN: modify `data/local/BookRepositoryImpl.kt` — thread `fromEpochDay` through

## Phase 5: Date-Range UI Wiring (PR5)

- [x] 5.1 RED: `BookListViewModelTest` — `dateRange` change re-queries via `GetBooksUseCase`, coroutines-test + Turbine + fake repo
- [x] 5.2 GREEN: modify `ui/list/BookListUiState.kt` + `BookListViewModel.kt` — add `dateRange: DateRangeFilter` (default `ALL_TIME`)
- [x] 5.3 RED: `BookListScreenTest` — 3 mutually-exclusive bucket options; selecting one narrows list (spec scenario)
- [x] 5.4 GREEN: modify `ui/list/BookListScreen.kt` — bucket selector bound to `uiState.dateRange`
- [x] 5.5 REFACTOR: confirm min-rating + date-range combine as AND; remove dead branches
