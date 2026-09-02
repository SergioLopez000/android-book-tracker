package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.Rating
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Task 2.6 (design: delegates delete by id). Spec: "Delete Book Entry".
 */
class DeleteBookUseCaseTest {

    private val existingBook = Book(
        id = 1L,
        title = "Dune",
        author = "Frank Herbert",
        coverUrl = null,
        rating = Rating(4.5f),
        review = "",
        dateFinished = LocalDate.now(),
    )
    private val repository = FakeBookRepository(seed = listOf(existingBook))
    private val useCase = DeleteBookUseCase(repository)

    @Test
    fun `removes the book from the repository`() = runTest {
        useCase(1L)

        assertNull(repository.findById(1L))
    }

    @Test
    fun `delegates the id to the repository`() = runTest {
        useCase(1L)

        assertEquals(1L, repository.lastDeletedId)
    }
}
