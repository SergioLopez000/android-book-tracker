# Tasks: Libros Leidos — Android Book Tracker (MVP)

TDD rule for every implementation task below: write the named failing test first (RED), write the minimal code to pass it (GREEN), then refactor. Scaffold-only tasks (no logic) are exempt.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1800–2400 (greenfield: gradle setup, domain, Room, 2 remote adapters, 2 screens+VMs, DI, mirrored tests) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 -> PR 2 -> PR 3 -> PR 4 -> PR 5 -> PR 6 -> PR 7 |
| Delivery strategy | ask-on-risk |
| Chain strategy | stacked-to-main (resolved) |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Spike + scaffold: gradle, package tree, empty `AppContainer` | PR 1 | `./gradlew :app:testDebugUnitTest --tests "*ComposeSpike*"` | Robolectric `createComposeRule`, run twice clean cache | Revert scaffold commit; no downstream code depends yet |
| 2 | Domain: `Book`, `Rating`, 4 use cases, pure JVM tests | PR 2 | `./gradlew :app:testDebugUnitTest --tests "com.librosleidos.domain.*"` | N/A — plain JUnit, no Android | Delete `domain/` package; nothing else references it yet |
| 3 | Data local: Room entity/DAO/DB/mapper/`BookRepositoryImpl` | PR 3 | `./gradlew :app:testDebugUnitTest --tests "com.librosleidos.data.local.*"` | Robolectric `inMemoryDatabaseBuilder` | Delete `data/local/`; domain ports remain stable |
| 4 | Data remote: OpenLibrary + GoogleBooks adapters + `ChainedMetadataSource` | PR 4 | `./gradlew :app:testDebugUnitTest --tests "com.librosleidos.data.remote.*"` | Fake HTTP client, no live network in tests | Delete `data/remote/`; independent of local package |
| 5 | UI: book list screen + ViewModel | PR 5 | `./gradlew :app:testDebugUnitTest --tests "com.librosleidos.ui.list.*"` | Robolectric `createComposeRule` | Revert `ui/list/`; list route not yet wired into nav |
| 6 | UI: add/edit screen + ViewModel | PR 6 | `./gradlew :app:testDebugUnitTest --tests "com.librosleidos.ui.edit.*"` | Robolectric `createComposeRule` | Revert `ui/edit/`; edit route not yet wired into nav |
| 7 | Wiring: `AppContainer` assembly, `MainActivity`/NavHost | PR 7 | `./gradlew :app:testDebugUnitTest :app:assembleDebug` | Robolectric smoke test of NavHost start destination | Revert wiring commit; screens still buildable standalone |

## Phase 0: Compose/Robolectric Spike

- [x] 0.1 In `app/build.gradle.kts` and `gradle/libs.versions.toml`, pin `androidx.compose:compose-bom` as sole source of all Compose versions (incl. `ui-test-junit4`, `ui-test-manifest`); set `testOptions.unitTests.isIncludeAndroidResources = true`.
- [x] 0.2 Write one trivial `createComposeRule` test asserting a `Text` node renders; `@RunWith(AndroidJUnit4::class)`, `@Config(sdk = [34])`.
- [x] 0.3 Run `./gradlew :app:testDebugUnitTest` twice on a clean cache; must pass both times within 1h timebox. If red, move Compose assertions to `androidTest` and note it; does not block Phase 2+.

## Phase 1: Project Scaffold

- [x] 1.1 Create `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`.
- [x] 1.2 Create `app/build.gradle.kts`: minSdk 26, KSP for Room, `BuildConfig.GOOGLE_BOOKS_API_KEY` from `local.properties` (default `""`).
- [x] 1.3 Create package skeleton: `domain/{model,port,usecase}`, `data/{local,remote}`, `ui/{list,edit}`, `di/`.
- [x] 1.4 Create empty `di/AppContainer.kt` and `LibrosLeidosApp.kt` (Application class, no wiring yet).
- [x] 1.5 Add `app/schemas/` KSP export path (`room.schemaLocation`) to `app/build.gradle.kts`.

## Phase 2: Domain Layer (JVM, TDD)

- [x] 2.1 `domain/model/Rating.kt`: test-first — reject non-0.5-step and out-of-range values, accept 0.5–5.0 inclusive (spec: Validate Half-Star Rating).
- [x] 2.2 `domain/model/Book.kt`, `BookMetadata.kt`, `BookSort.kt`: plain data classes, no tests needed (no logic).
- [x] 2.3 `domain/port/BookRepository.kt`, `BookMetadataSource.kt`: interfaces only.
- [x] 2.4 `domain/usecase/SaveBookUseCase.kt`: test-first — reject blank title, invalid `Rating`, future `dateFinished`; accept valid save; allow duplicate titles (spec: Add Book Manually, Require Rating on Save, Allow Duplicate Titles).
- [x] 2.5 `domain/usecase/GetBooksUseCase.kt` + `GetBookByIdUseCase.kt`: test-first — delegates sort/minRating to repository; not-found returns null.
- [x] 2.6 `domain/usecase/DeleteBookUseCase.kt`: test-first — delegates delete by id (spec: Delete Book Entry).
- [x] 2.7 `domain/usecase/SearchBookMetadataUseCase.kt`: test-first — trims query, rejects <2 chars, maps source failure to empty result (spec: Graceful Degradation).

## Phase 3: Data Layer — Local/Room (Robolectric, TDD)

- [x] 3.1 `data/local/BookEntity.kt`: `@Entity` with `Index("dateFinishedEpochDay")`.
- [x] 3.2 `data/local/BookDao.kt`: test-first — `observeByDateDesc`/`observeByRatingDesc` apply `minRating` filter and ordering; `@Upsert`; `deleteById` (spec: Sort Book List, Filter by Minimum Rating, Delete Book Entry).
- [x] 3.3 `data/local/BooksDatabase.kt`: Room DB builder, `inMemoryDatabaseBuilder` used in tests.
- [x] 3.4 `data/local/BookMapper.kt`: test-first — `Book` ↔ `BookEntity` round-trip incl. `LocalDate` ↔ epoch day.
- [x] 3.5 `data/local/BookRepositoryImpl.kt`: test-first — implements `BookRepository` against DAO, `observeBooks` reflects sort/filter changes.

## Phase 4: Data Layer — Remote Metadata (TDD)

- [x] 4.1 `data/remote/OpenLibraryMetadataSource.kt` + DTO: test-first — maps API response to `BookMetadata`, returns `Result.failure` on error (spec: Search Open Library First).
- [x] 4.2 `data/remote/GoogleBooksMetadataSource.kt` + DTO: test-first — same contract; skipped when `BuildConfig.GOOGLE_BOOKS_API_KEY` is blank, returns empty (spec: Fallback to Google Books).
- [x] 4.3 `data/remote/ChainedMetadataSource.kt`: test-first — advances to fallback on primary failure OR empty result; never advances if primary has matches (spec: Search Open Library First, Fallback to Google Books, Graceful Degradation).

## Phase 5: UI — Book List (Robolectric, TDD)

- [x] 5.1 `ui/list/BookListUiState.kt`: state shape only.
- [x] 5.2 `ui/list/BookListViewModel.kt`: test-first (Turbine + fake repo) — loads books, applies sort/minRating via `flatMapLatest`, empty-state flag (spec: List Read Books, Sort Book List, Filter by Minimum Rating).
- [x] 5.3 `ui/list/BookListScreen.kt` + `BookListRoute.kt`: test-first (`createComposeRule`) — renders list, empty-state message, delete confirmation dialog (spec: Empty library, Delete confirmed/cancelled).

## Phase 6: UI — Book Add/Edit (Robolectric, TDD)

- [x] 6.1 `ui/edit/BookEditUiState.kt`: state shape incl. `SearchState`, `fieldErrors`.
- [x] 6.2 `ui/edit/BookEditViewModel.kt`: test-first (Turbine + fakes) — search triggers `SearchBookMetadataUseCase`, selecting a result prefills editable fields, save runs `SaveBookUseCase` and surfaces validation errors (spec: Autofill Editable Fields, Add Book via Metadata Lookup, Validate Half-Star Rating).
- [x] 6.3 `ui/edit/BookEditScreen.kt` + `BookEditRoute.kt`: test-first (`createComposeRule`) — manual entry without lookup, blocked save with empty rating, edit prefill of existing book (spec: Add Book Manually, Require Rating on Save, Edit Existing Book).

## Phase 7: Wiring & Integration

- [x] 7.1 Complete `di/AppContainer.kt`: assemble `BooksDatabase`, `BookRepositoryImpl`, `ChainedMetadataSource`, all use cases, `viewModelFactory`.
- [x] 7.2 `MainActivity.kt`: NavHost with list/edit destinations backed by `AppContainer`.
- [x] 7.3 Robolectric smoke test: app launches to list screen, empty-state shown on fresh DB.
- [x] 7.4 Verify a fresh clone (no `local.properties`) builds and tests green with Google Books disabled.

## Status: 34/34 tasks complete. APP COMPLETE — see apply-progress for final evidence.
