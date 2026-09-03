# Design: IMDb-Style UI and Date-Range Filters

## Technical Approach

Additive extension of the shipped MVP: no Room migration, no layering change. Domain gains `DateRangeFilter`; `GetBooksUseCase` resolves it to a nullable epoch-day lower bound via an injected `java.time.Clock` (default `systemDefaultZone()`), making bucket boundaries deterministically testable. The bound flows as `Long?` through `BookRepository` → `BookDao`, AND-combined with `minRating`. UI gains `ui/theme/` (dark-only) and `ui/components/` (`StarRating`, `BookCoverImage`), consumed by the existing presentational `BookListScreen` / `BookEditScreen`. Rating callback signatures in ViewModel/UiState are unchanged.

## Architecture Decisions

| # | Decision | Chosen | Rejected | Rationale |
|---|---|---|---|---|
| D8 | 3rd filter axis | Nullable-bind predicate inside the two existing `@Query` methods | (a) 6 explicit sort×bucket queries; (c) `@RawQuery` | The 3 buckets differ only by a lower bound, so one nullable param collapses them and the DAO stays at 2 observe methods. **Upholds D6 rather than breaking it**: SQL stays a compile-time literal Room verifies; only the predicate's activeness is data-dependent. (a) is compile-checked too but triples methods with drift risk (12 at a 4th axis). (c) builds SQL at runtime — the only option that truly forfeits D6. |
| D9 | Coil3 pin + engine | `coil = "3.6.1"`, `io.coil-kt.coil3:coil-compose` + `coil-network-okhttp` | `coil-network-ktor3`; reusing `HttpUrlConnectionClient` | Coil ships no `HttpURLConnection` engine, so reuse is unavailable. Ktor3 needs `ktor-client-core` + a platform engine (OkHttp on Android anyway) = 3+ artifacts; okhttp is 1 and is Coil's default. `data/remote` is **not** refactored onto OkHttp. minSdk 26 ≥ Coil's 23. |
| D10 | Star rating | Own `ui/components/StarRating.kt`; one composable, mode chosen by `onRatingChange` nullability | `compose-ratingbar`; split display/input composables | Avoids a stale dependency (last tag Apr 2024, 0.5-API unverified); one component keeps display and input pixel-identical. |
| D11 | Cover image | `ui/components/BookCoverImage.kt` wrapping `AsyncImage`; null/blank/non-https short-circuits before Coil | `SubcomposeAsyncImage`; inline per-screen `AsyncImage` | Coil-free fallback paths are testable with zero image-loader setup and survive a red D13 spike. Non-https is treated as absent, carrying forward the MVP threat-matrix rule. |
| D12 | Theme | `RinconDeLecturaTheme` = `MaterialTheme(DarkColorScheme, AppTypography, content)`, no parameters | Official `darkTheme: Boolean` + `isSystemInDarkTheme()` + dynamic color | Light and dynamic color are out of scope; the branch would be dead code and an untestable state. |
| D13 | Coil × Robolectric | Task 0b spike (1h): `coil-test` `FakeImageLoaderEngine` installed by a `CoilTestRule` via `SingletonImageLoader.setSafe`, reset in `after()` | Assume interop works | Mirrors the MVP's proven R1/Task 0 pattern; `coil-test` is Coil3's documented test seam. |
| D14 | Base branch | Create `main` at `pr1/spike-scaffold` tip, make it default, branch `feat/imdb-style-ui-and-filters` from it | Branch from `pr1/spike-scaffold` directly | A chained-PR stack rooted on a branch named `pr1/spike-scaffold` is unreadable. Recommendation only; delivery/tasks owns the call. |

**D9 verification gate (apply time):** research reported conflicting versions (3.3.0/3.4.0/3.6.1). Confirm `3.6.1` on Maven Central, then run `./gradlew :app:dependencies --configuration debugRuntimeClasspath` and confirm every `androidx.compose.*` artifact still resolves to BOM `2025.09.01`. Coil is not a Compose artifact, so R1's "BOM is the sole source of Compose versions" holds only if Coil's transitive Compose does not out-rank the BOM constraint.

## Interfaces

```kotlin
enum class DateRangeFilter { LAST_MONTH, LAST_YEAR, ALL_TIME }  // domain/model

class GetBooksUseCase(
    private val repository: BookRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    operator fun invoke(sort: BookSort, minRating: Float, range: DateRangeFilter): Flow<List<Book>> {
        val today = LocalDate.now(clock)
        val from = when (range) {
            DateRangeFilter.LAST_MONTH -> today.minusDays(30).toEpochDay()
            DateRangeFilter.LAST_YEAR  -> today.minusDays(365).toEpochDay()
            DateRangeFilter.ALL_TIME   -> null
        }
        return repository.observeBooks(sort, minRating, from)
    }
}
// port + impl: observeBooks(sort, minRating, fromEpochDay: Long?)
```

```sql
SELECT * FROM books
WHERE rating >= :minRating
  AND (:fromEpochDay IS NULL OR dateFinishedEpochDay >= :fromEpochDay)
ORDER BY dateFinishedEpochDay DESC   -- second method: ORDER BY rating DESC
```

```kotlin
@Composable fun StarRating(rating: Float?, modifier: Modifier = Modifier,
                           onRatingChange: ((Float) -> Unit)? = null)
@Composable fun BookCoverImage(coverUrl: String?, contentDescription: String?,
                               modifier: Modifier = Modifier)
```

`StarRating` draws an outline star row and overlays a filled row clipped to `fraction = (rating ?: 0f) / 5f` with `drawWithContent { clipRect(right = size.width * fraction) { drawContent() } }` — one fraction covers 0.5 steps without `StarHalf` assets or RTL quirks. Input mode splits each star into two half-width tap targets carrying `contentDescription = "Set rating N"`; steps derive from `Rating.MIN`/`Rating.MAX` so the domain keeps owning the invariant. Display mode uses `clearAndSetSemantics` with the formatted value.

## Data Flow

```
BookListScreen ──dateRange──→ BookListViewModel(ListQuery+dateRange)
                                    └→ GetBooksUseCase ─(Clock)→ fromEpochDay: Long?
                                            └→ BookRepository → BookDao (2 queries, nullable bind)
BookListItem ─→ BookCoverImage ─→ Coil ImageLoader ─→ OkHttp (cover) | placeholder (null/non-https)
             └→ StarRating(book.rating.value)
```

## File Changes

| File | Action | Description |
|---|---|---|
| `domain/model/DateRangeFilter.kt` | Create | Three-bucket enum |
| `domain/port/BookRepository.kt`, `domain/usecase/GetBooksUseCase.kt` | Modify | `fromEpochDay: Long?` param; injected `Clock` |
| `data/local/BookDao.kt`, `BookRepositoryImpl.kt` | Modify | Nullable-bind date predicate |
| `ui/theme/{Color,Type,Theme}.kt` | Create | Dark scheme, typography, `RinconDeLecturaTheme` |
| `ui/components/{StarRating,BookCoverImage}.kt` | Create | Shared presentational components |
| `ui/list/{BookListScreen,BookListUiState,BookListViewModel}.kt` | Modify | Cover + stars in item; `dateRange` in state/query; bucket selector |
| `ui/edit/BookEditScreen.kt` | Modify | Delete private `RatingInput`; call `StarRating` |
| `MainActivity.kt` | Modify | `MaterialTheme {` → `RinconDeLecturaTheme {` |
| `AndroidManifest.xml` (+ `values/themes.xml`) | Modify | **`android:theme` is `Theme.Material.Light.NoActionBar` today — a white window background behind a dark Compose UI.** Switch to a dark NoActionBar platform theme. |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modify | Coil pin, `coil-network-okhttp`, `testImplementation(coil-test)` |
| `app/src/test/.../ui/CoilTestRule.kt` | Create | Fake `ImageLoader` rule (D13) |

`INTERNET` is already declared — no manifest permission change.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Domain | Bucket → epoch-day bounds, incl. 30/365-day boundary days | JUnit with `Clock.fixed` |
| DAO | 6 sort×bucket combos + AND with `minRating`; `ALL_TIME` null bind returns all | Robolectric `inMemoryDatabaseBuilder` |
| ViewModel | `dateRange` change re-queries; state carries the bucket | coroutines-test + Turbine + fake |
| Compose | Stars render/click per 0.5 step; cover placeholder for null/blank/non-https; loaded cover; theme supplies the dark scheme | Robolectric + `createComposeRule` + `CoilTestRule` |

Existing `BookEditScreenTest` assertions targeting the numeric `"0.5"` TextButtons must migrate to star semantics — expected, in-scope test churn, not a regression.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. New network egress is image fetching only; the MVP's https-only `coverUrl` rule is enforced in `BookCoverImage` (D11) and gets a RED test.

## Migration / Rollout

No migration. Schema, entity, and `Book` are untouched; the date filter reads the existing indexed `dateFinishedEpochDay`. Rollback is a branch revert plus a two-line Gradle removal.

## Open Questions

- [ ] Exact Coil version confirmed against Maven Central + BOM resolution (D9 gate, resolved at apply time).
- [ ] Base-branch creation is a delivery decision (D14 recommends; tasks/delivery decides).

## Notes for tasks

Task 0b (Coil×Robolectric spike) blocks only cover-image component tests; domain/DAO/ViewModel/star work proceeds RED-first regardless. Suggested slices: (1) theme + manifest, (2) `StarRating` + edit/list wiring, (3) Coil pin + spike + `BookCoverImage`, (4) date-range domain→DAO, (5) date-range UI. Each is independently revertable and well inside the 400-line review budget.
