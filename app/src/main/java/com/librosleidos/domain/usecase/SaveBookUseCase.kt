package com.librosleidos.domain.usecase

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.Rating
import com.librosleidos.domain.port.BookRepository
import java.time.LocalDate

/**
 * Validates and persists a new or edited book (design: SaveBookUseCase).
 * Spec: "Add Book Manually", "Require Rating on Save", "Validate Half-Star
 * Rating", "Edit Existing Book", "Allow Duplicate Titles" (duplicate
 * title/author is never checked, so it never blocks a save).
 *
 * Takes a raw [SaveBookRequest] rather than a [Book] because `Book.rating`
 * is a [Rating] — a type that cannot represent an invalid value — so this
 * use case is the one place that turns a raw, possibly-invalid `Float?`
 * rating into either a persisted [Book] or a [SaveBookRejection].
 */
class SaveBookUseCase(private val repository: BookRepository) {

    suspend operator fun invoke(request: SaveBookRequest): SaveBookOutcome {
        if (request.title.isBlank()) {
            return SaveBookOutcome.Rejected(SaveBookRejection.BLANK_TITLE)
        }
        val ratingValue = request.rating
            ?: return SaveBookOutcome.Rejected(SaveBookRejection.MISSING_RATING)
        val rating = try {
            Rating(ratingValue)
        } catch (invalid: IllegalArgumentException) {
            return SaveBookOutcome.Rejected(SaveBookRejection.INVALID_RATING)
        }
        if (request.dateFinished.isAfter(LocalDate.now())) {
            return SaveBookOutcome.Rejected(SaveBookRejection.FUTURE_DATE_FINISHED)
        }

        val book = Book(
            id = request.id,
            title = request.title,
            author = request.author,
            coverUrl = request.coverUrl,
            rating = rating,
            review = request.review,
            dateFinished = request.dateFinished,
        )
        return SaveBookOutcome.Saved(repository.save(book))
    }
}

/**
 * Raw entry-form input for [SaveBookUseCase]. `rating` is nullable Float
 * (not [Rating]) because the form may legitimately have no rating yet
 * (spec: "Require Rating on Save").
 */
data class SaveBookRequest(
    val id: Long? = null,
    val title: String,
    val author: String,
    val coverUrl: String? = null,
    val rating: Float?,
    val review: String = "",
    val dateFinished: LocalDate,
)

sealed interface SaveBookOutcome {
    data class Saved(val id: Long) : SaveBookOutcome
    data class Rejected(val reason: SaveBookRejection) : SaveBookOutcome
}

enum class SaveBookRejection {
    BLANK_TITLE,
    MISSING_RATING,
    INVALID_RATING,
    FUTURE_DATE_FINISHED,
}
