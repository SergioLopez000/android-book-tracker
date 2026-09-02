package com.librosleidos.domain.usecase

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 2.4 (design: SaveBookUseCase — non-blank title, valid Rating,
 * `dateFinished` not future). Spec: "Add Book Manually", "Require Rating on
 * Save", "Validate Half-Star Rating", "Allow Duplicate Titles".
 *
 * [SaveBookUseCase] accepts a raw [SaveBookRequest] rather than a [Book][
 * com.librosleidos.domain.model.Book] directly: `Book.rating` is a
 * validated [Rating][com.librosleidos.domain.model.Rating] that cannot
 * represent an invalid value, so the use case itself must own turning a raw
 * `Float?` rating into either a persisted book or a rejection reason.
 */
class SaveBookUseCaseTest {

    private val repository = FakeBookRepository()
    private val useCase = SaveBookUseCase(repository)
    private val today = LocalDate.now()

    private fun validRequest(
        title: String = "Dune",
        author: String = "Frank Herbert",
        rating: Float? = 4.5f,
        dateFinished: LocalDate = today,
    ) = SaveBookRequest(
        title = title,
        author = author,
        rating = rating,
        dateFinished = dateFinished,
    )

    @Test
    fun `saves a valid book and returns its id`() = runTest {
        val outcome = useCase(validRequest())

        assertTrue(outcome is SaveBookOutcome.Saved)
        val savedId = (outcome as SaveBookOutcome.Saved).id
        assertEquals("Dune", repository.findById(savedId)?.title)
    }

    @Test
    fun `rejects a blank title`() = runTest {
        val outcome = useCase(validRequest(title = "   "))

        assertEquals(SaveBookOutcome.Rejected(SaveBookRejection.BLANK_TITLE), outcome)
    }

    @Test
    fun `rejects a missing rating`() = runTest {
        val outcome = useCase(validRequest(rating = null))

        assertEquals(SaveBookOutcome.Rejected(SaveBookRejection.MISSING_RATING), outcome)
    }

    @Test
    fun `rejects an invalid rating`() = runTest {
        val outcome = useCase(validRequest(rating = 3.7f))

        assertEquals(SaveBookOutcome.Rejected(SaveBookRejection.INVALID_RATING), outcome)
    }

    @Test
    fun `rejects a future date finished`() = runTest {
        val outcome = useCase(validRequest(dateFinished = today.plusDays(1)))

        assertEquals(SaveBookOutcome.Rejected(SaveBookRejection.FUTURE_DATE_FINISHED), outcome)
    }

    @Test
    fun `does not persist a book when rejected`() = runTest {
        useCase(validRequest(title = ""))

        assertNull(repository.findById(1L))
    }

    @Test
    fun `allows saving a second book with a duplicate title`() = runTest {
        useCase(validRequest(dateFinished = today.minusDays(10)))

        val second = useCase(validRequest(dateFinished = today))

        assertTrue(second is SaveBookOutcome.Saved)
    }
}
