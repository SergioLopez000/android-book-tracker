package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.Rating
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Task 2.5 (design: GetBookByIdUseCase — not-found returns null).
 */
class GetBookByIdUseCaseTest {

    private val existingBook = Book(
        id = 1L,
        title = "Dune",
        author = "Frank Herbert",
        coverUrl = null,
        rating = Rating(4.5f),
        review = "Great book",
        dateFinished = LocalDate.now(),
    )
    private val repository = FakeBookRepository(seed = listOf(existingBook))
    private val useCase = GetBookByIdUseCase(repository)

    @Test
    fun `returns the book when found`() = runTest {
        val result = useCase(1L)

        assertEquals(existingBook, result)
    }

    @Test
    fun `returns null when the book does not exist`() = runTest {
        val result = useCase(999L)

        assertNull(result)
    }
}
