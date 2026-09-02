package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.BookSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 2.5 (design: GetBooksUseCase delegates sort/minRating to the
 * repository). Spec: "List Read Books", "Sort Book List", "Filter by
 * Minimum Rating".
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
}
