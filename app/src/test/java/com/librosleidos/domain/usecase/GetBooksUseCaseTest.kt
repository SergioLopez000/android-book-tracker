package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.BookSort
import com.librosleidos.domain.model.DateRangeFilter
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Task 2.5 (design: GetBooksUseCase delegates sort/minRating to the
 * repository). Spec: "List Read Books", "Sort Book List", "Filter by
 * Minimum Rating".
 *
 * Task 4.1 (design D8): [DateRangeFilter] resolves to a nullable
 * epoch-day lower bound via an injected [Clock], deterministically
 * testable with [Clock.fixed]. Spec: "Filter by Date Range".
 */
class GetBooksUseCaseTest {

    private val repository = FakeBookRepository()
    private val useCase = GetBooksUseCase(repository)

    @Test
    fun `delegates sort and minRating to the repository`() = runTest {
        useCase(BookSort.RATING_DESC, minRating = 3.5f).first()

        assertEquals(BookSort.RATING_DESC, repository.lastRequestedSort)
        assertEquals(3.5f, repository.lastRequestedMinRating)
    }

    @Test
    fun `ALL_TIME requests no date lower bound`() = runTest {
        useCase(BookSort.DATE_FINISHED_DESC, minRating = 0f, range = DateRangeFilter.ALL_TIME).first()

        assertNull(repository.lastRequestedFromEpochDay)
    }

    @Test
    fun `LAST_MONTH resolves the exact 30-day lower bound`() = runTest {
        val fixedToday = LocalDate.of(2026, 3, 31)
        val clock = Clock.fixed(fixedToday.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        val useCaseWithFixedClock = GetBooksUseCase(repository, clock)

        useCaseWithFixedClock(
            BookSort.DATE_FINISHED_DESC,
            minRating = 0f,
            range = DateRangeFilter.LAST_MONTH,
        ).first()

        assertEquals(LocalDate.of(2026, 3, 1).toEpochDay(), repository.lastRequestedFromEpochDay)
    }

    @Test
    fun `LAST_YEAR resolves the exact 365-day lower bound`() = runTest {
        val fixedToday = LocalDate.of(2026, 3, 31)
        val clock = Clock.fixed(fixedToday.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        val useCaseWithFixedClock = GetBooksUseCase(repository, clock)

        useCaseWithFixedClock(
            BookSort.DATE_FINISHED_DESC,
            minRating = 0f,
            range = DateRangeFilter.LAST_YEAR,
        ).first()

        assertEquals(LocalDate.of(2025, 3, 31).toEpochDay(), repository.lastRequestedFromEpochDay)
    }
}
