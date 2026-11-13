# Proposal: IMDb-Style UI and Date-Range Filters

## Intent

The shipped MVP renders ratings as plain text and never renders `Book.coverUrl` (persisted end-to-end, unused), under a bare `MaterialTheme {}`. Filtering has one axis: minimum rating. Users want an IMDb-like library — branded dark theme, cover art, graphical half-stars — plus recency filtering.

## Scope

### In Scope
- `ui/theme/`: custom dark `ColorScheme` + `Typography`, applied at the composition root.
- `ui/components/`: `BookCoverImage` (Coil3 `AsyncImage`, placeholder/error) and `StarRating` (custom Canvas/Icon row, 0.5 increments, display + input modes).
- Both reused in `BookListScreen` and `BookEditScreen` (replacing numeric `RatingInput`).
- `DateRangeFilter` (LAST_MONTH 30d / LAST_YEAR 365d / ALL_TIME default), threaded `GetBooksUseCase` → `BookRepository` → `BookDao`, AND-combined with `minRating`.
- Coil3 pinned in `gradle/libs.versions.toml`.

### Out of Scope
- Light theme and API-31 dynamic color (dark-only branded look).
- Arbitrary date ranges or date pickers — three fixed buckets only.
- Changes to `Book`, `Rating`, metadata lookup, or Room schema (no migration).
- Navigation, DI, or container/presentational restructuring.

## Capabilities

### New Capabilities
- `app-theme`: app-wide dark Material3 theme and its application at the composition root.
- `star-rating`: graphical 0.5-increment star display and star-based rating input.

### Modified Capabilities
- `book-library`: adds \"Filter by Date Range\" (3 buckets, AND-combined with minimum rating); \"List Read Books\" now requires cover and star rendering, including missing/broken `coverUrl`.
- `book-entry`: \"Validate Half-Star Rating\" input mechanism becomes the star component; the 0.5-step invariant is unchanged.

## Approach

Extend, don't replace. Domain gains `DateRangeFilter`; the use case resolves buckets to epoch-day lower bounds from an injected clock. New components are stateless, covered by the existing Robolectric + Compose harness. Strict TDD: a failing test precedes each work unit.

Design must decide (not decided here): how `BookDao` absorbs a third filter axis without exploding decision D6's explicit-`@Query` pattern — more explicit queries, nullable-bind (`:from IS NULL OR col >= :from`), or `@RawQuery`.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `ui/theme/`, `ui/components/` | New | Dark theme; `BookCoverImage`, `StarRating` |
| `ui/list/*`, `ui/edit/BookEditScreen.kt` | Modified | Covers, stars, date-range control, `dateRange` in UiState |
| `domain/model/DateRangeFilter.kt` | New | Three-bucket type |
| `domain/{port,usecase}`, `data/local/{BookDao,BookRepositoryImpl}` | Modified | Filter param, date-bounded queries |
| `libs.versions.toml`, `app/build.gradle.kts`, app root | Modified | Coil3 pin; wrap in theme |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Third filter axis breaks D6 explicit-query pattern | High | Design picks one approach; DAO tests cover each combo |
| Coil3 version vs pinned Compose BOM mismatch | Med | Verify Maven Central + BOM cross-check in design |
| Coil3 `AsyncImage` untested under Robolectric | Med | Timeboxed spike; fall back to fake image loader |
| No `main` branch (only `pr1/spike-scaffold`) | High | Base-branch choice escalated to delivery planning |
| Theme overhaul regresses Compose tests | Med | Theme additive; assert semantics, not colors |

## Rollback Plan

Revert the feature branch. Coil3 removal is a two-line Gradle revert. No Room migration or schema change, so persisted data is unaffected either direction. Reverting `ui/theme/` restores stock `MaterialTheme {}`; reverting the filter restores the two original DAO queries.

## Dependencies

- Coil3 (`io.coil-kt.coil3:coil-compose` + network engine) — version verified against Maven Central and the pinned Compose BOM during design.
- No new permissions expected (`INTERNET` already used); verify `AndroidManifest.xml`.

## Success Criteria

- [ ] Both screens render under the custom dark theme, not Compose defaults.
- [ ] List items show covers with graceful placeholder for null/failed `coverUrl`.
- [ ] Ratings render as half-step stars and are set via stars in edit.
- [ ] The three date buckets filter correctly, AND-combined with min rating.
- [ ] All new work RED-first; full suite green.
