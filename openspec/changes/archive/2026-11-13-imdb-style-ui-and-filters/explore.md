# SDD Exploration: imdb-style-ui-and-filters

**What**: Explored feasibility of an IMDb-style presentational UI (cover art, star rating) plus last-month/last-year/historic date filters, on top of the shipped MVP (`libros-leidos-android-book-tracker`, archived, 34/34 tasks).

**Why**: User requested a friendlier UI (IMDb-inspired: covers, graphical stars) and new search filters (last month / last year / historic) for the existing Android book tracker.

**Where**:
- `app/src/main/java/com/librosleidos/ui/list/BookListScreen.kt`
- `app/src/main/java/com/librosleidos/ui/edit/BookEditScreen.kt`
- `app/src/main/java/com/librosleidos/domain/model/{Book,Rating}.kt`
- `app/build.gradle.kts`, `gradle/libs.versions.toml`

**Learned**:
1. `Book.coverUrl: String?` already exists end-to-end but is persisted-and-unused. Adding cover art is a pure UI addition, zero domain/data changes needed.
2. `dateFinished: LocalDate` is stored as `dateFinishedEpochDay: Long`. This is ideal for last-month/last-year/historic range filtering.
3. Shipped MVP filter capability is minimum-rating only.
4. Extending filtering to 3 axes (sort × minRating × dateRange) breaks D6's "two explicit queries" pattern.
5. No image-loading library is present; adding cover-image rendering requires a new Gradle dependency (Coil3-Compose).
6. No custom theme exists; MainActivity wraps content in a bare MaterialTheme.
7. Architecture layering is intact: presentational additions belong under `ui/list`/`ui/edit`.
8. Prior spec, design, tasks, and archive-report are the baseline this change extends.
9. Repo base-branch choice was corrected to main.
