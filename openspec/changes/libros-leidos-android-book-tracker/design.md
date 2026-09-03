# Design: Libros Leidos — Android Book Tracker (MVP)

## Technical Approach

Single Gradle module `:app`, hexagonal packages under `com.librosleidos` per proposal. Domain is pure Kotlin (no Android imports) so it runs on plain JVM tests; Room and HTTP sit behind `BookRepository` / `BookMetadataSource`. Compose follows container/presentational: a `*Route` binds the ViewModel, a stateless `*Screen(state, onEvent)` is directly testable. minSdk 26 gives native `java.time`, so no desugaring and no Room TypeConverters.

## Architecture Decisions

| # | Decision | Chosen | Rejected | Rationale |
|---|---|---|---|---|
| D1 | DI | Manual `AppContainer` on `Application` + `viewModelFactory` | Hilt; Koin | 2 screens, 5 use cases, 3 adapters. Hilt needs KSP + `HiltTestApplication` + custom Robolectric runner, amplifying risk R1. Fakes are constructor-injected with zero setup. |
| D2 | Google Books fallback | Ships **enabled**, gated by non-blank `BuildConfig.GOOGLE_BOOKS_API_KEY` from `local.properties` (default `""`) | Separate `ENABLE_GOOGLE_BOOKS` boolean | The key IS the switch. Boolean + key is two switches for one condition and an untestable state matrix. Blank key ⇒ returns empty, never throws. |
| D3 | Fallback placement | `ChainedMetadataSource(primary, fallback)` composite in `data/remote/` | Use case holding both ports | Domain stays unaware two providers exist; port contract unchanged. Chain advances on failure **or** empty. |
| D4 | Rating | `@JvmInline value class Rating`, validated at construction | Raw `Float` in domain | Enforces the 0.5-step invariant once. Multiples of 0.5 are exact in IEEE-754, so `(v*2) % 1 == 0f` needs no epsilon. Persisted as `Float`. |
| D5 | Date | `dateFinishedEpochDay: Long` ↔ `LocalDate` in mapper | Room TypeConverter | Drops converters entirely; sort column stays an indexable integer. |
| D6 | Sort/filter | Two explicit `@Query` methods chosen by `BookSort` | `ORDER BY CASE`; in-memory sort | SQL stays compile-checked by Room and verifiable by DAO tests. |
| D7 | Effects | State via `StateFlow`; one-shot nav/snackbar via `Channel().receiveAsFlow()` | Boolean flags in state | Removes reset-the-flag bugs after save/delete. |

## Interfaces / Contracts

```kotlin
data class Book(id: Long?, title: String, author: String, coverUrl: String?,
                rating: Rating, review: String, dateFinished: LocalDate) // id null = unsaved
data class BookMetadata(title: String, author: String, coverUrl: String?)
enum class BookSort { DATE_FINISHED_DESC, RATING_DESC }

interface BookRepository {
  fun observeBooks(sort: BookSort, minRating: Float): Flow<List<Book>>
  suspend fun findById(id: Long): Book?
  suspend fun save(book: Book): Long
  suspend fun delete(id: Long)
}
interface BookMetadataSource { suspend fun search(query: String): Result<List<BookMetadata>> }

@Entity(tableName = "books", indices = [Index("dateFinishedEpochDay")])
data class BookEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String,
  val author: String, val coverUrl: String?, val rating: Float, val review: String,
  val dateFinishedEpochDay: Long)
```

`Result` (not a bare list) is what lets D3 separate provider failure from a genuine no-match.

`BookDao`: `observeByDateDesc(minRating)` / `observeByRatingDesc(minRating)` → `Flow<List<BookEntity>>`; `findById`; `@Upsert suspend fun upsert(BookEntity): Long`; `deleteById`.

Use cases (`domain/usecase/`): `GetBooksUseCase`, `GetBookByIdUseCase`, `SaveBookUseCase` (non-blank title, valid `Rating`, `dateFinished` not future), `DeleteBookUseCase`, `SearchBookMetadataUseCase` (trim, ≥2 chars, failure→empty).

## Data Flow

```
BookEditRoute → BookEditViewModel → SearchBookMetadataUseCase → BookMetadataSource
              └→ ChainedMetadataSource → OpenLibrary ─(fail|empty)→ GoogleBooks
  pick result → fields prefill (still editable) → SaveBookUseCase → BookRepository → Room
BookListRoute ← StateFlow ← BookListViewModel ← GetBooksUseCase ←──────────────────┘
```

Query changes: `MutableStateFlow<ListQuery>` → `flatMapLatest` → `stateIn(WhileSubscribed(5_000))`. Network failure yields `SearchState.Unavailable`; saving is never blocked.

## UI State

```kotlin
data class BookListUiState(books, sort, minRating, isLoading, isEmpty)
data class BookEditUiState(id, title, author, coverUrl, rating, review, dateFinished,
                           search: SearchState, fieldErrors: Map<Field,String>, isSaving)
sealed interface SearchState { Idle | Loading | Results(List<BookMetadata>) | Unavailable }
```

## File Changes

All **Create**: `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`; `domain/` (model, port, usecase); `data/local/` (`BookEntity`, `BookDao`, `BooksDatabase`, `BookMapper`, `BookRepositoryImpl`); `data/remote/` (`OpenLibraryMetadataSource`, `GoogleBooksMetadataSource`, `ChainedMetadataSource`, DTOs); `ui/list/`, `ui/edit/` (Route + Screen + ViewModel + UiState); `di/AppContainer.kt`; `LibrosLeidosApp.kt`; mirrored `app/src/test/`; `app/schemas/`.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Domain | Rating invariant, use-case validation, chain fallback | Plain JUnit, no Android |
| DAO | sort + minRating queries, upsert/delete | Robolectric + `inMemoryDatabaseBuilder` |
| ViewModel | state transitions, degraded search | `coroutines-test` + Turbine + fakes |
| Compose | list render/empty/filter, edit validation, prefill | Robolectric + `createComposeRule()` |

### R1 resolved — Robolectric/Compose alignment (Task 0, blocks UI tasks only)

Structural rule: `androidx.compose:compose-bom` is the **sole** source of every Compose version **including** `ui-test-junit4` and `ui-test-manifest`; hand-pinning a test artifact outside the BOM is the mismatch cause. Also `testOptions.unitTests.isIncludeAndroidResources = true`, `@RunWith(AndroidJUnit4::class)`, and explicit `@Config(sdk = [34])` because Robolectric's supported SDK trails `compileSdk 36`. Exact BOM/Robolectric versions are confirmed by the spike, not assumed.

Acceptance: one trivial `createComposeRule` test asserting a `Text` node passes `./gradlew :app:testDebugUnitTest` twice on a clean cache. Timebox 1h. If red: move only Compose assertions to `androidTest`; domain/DAO/ViewModel TDD is unaffected, so R1 can never block the RED-GREEN loop.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. Untrusted API text renders as plain Compose `Text` (no HTML); `coverUrl` accepted only over `https`.

## Migration / Rollout

Greenfield, schema v1. Export schema via KSP `room.schemaLocation` to `app/schemas/` from the first commit so future migrations stay diffable. No `fallbackToDestructiveMigration`. Git-ignored `local.properties` holds the optional Google Books key; a fresh clone builds and tests green without it.

## Open Questions

None blocking. Exact Compose BOM / Robolectric version pair is an output of Task 0.
