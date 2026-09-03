package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.BookSort
import com.librosleidos.domain.model.DateRangeFilter
import com.librosleidos.domain.port.BookRepository
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Task 2.5 (design: delegates sort/minRating to the repository).
 * Spec: "List Read Books", "Sort Book List", "Filter by Minimum Rating".
 *
 * Task 4.2 (design D8): resolves [DateRangeFilter] to a nullable epoch-day
 * lower bound via an injected [Clock] (default `systemDefaultZone()`),
 * making bucket boundaries deterministically testable with
 * [Clock.fixed]. `range` defaults to [DateRangeFilter.ALL_TIME] (spec:
 * "histórico" is the default bucket) so existing callers that do not yet
 * pass a bucket — `BookListViewModel`/`AppContainer`, wired to the date
 * filter in PR5 — keep compiling unchanged in this domain-only PR.
 */
class GetBooksUseCase(
    private val repository: BookRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    operator fun invoke(
        sort: BookSort,
        minRating: Float,
        range: DateRangeFilter = DateRangeFilter.ALL_TIME,
    ): Flow<List<Book>> {
        val today = LocalDate.now(clock)
        val fromEpochDay = when (range) {
            DateRangeFilter.LAST_MONTH -> today.minusDays(LAST_MONTH_DAYS).toEpochDay()
            DateRangeFilter.LAST_YEAR -> today.minusDays(LAST_YEAR_DAYS).toEpochDay()
            DateRangeFilter.ALL_TIME -> null
        }
        return repository.observeBooks(sort, minRating, fromEpochDay)
    }

    private companion object {
        const val LAST_MONTH_DAYS = 30L
        const val LAST_YEAR_DAYS = 365L
    }
}
