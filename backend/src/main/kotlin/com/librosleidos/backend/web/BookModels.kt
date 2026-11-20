package com.librosleidos.backend.web

import com.librosleidos.backend.domain.model.Book
import com.librosleidos.backend.domain.model.BookDraft
import com.librosleidos.backend.domain.model.Rating
import java.time.LocalDate

/** Thrown when a body that passed the schema is still rejected by the domain (for example a blank title). */
class InvalidBookRequestException(cause: Throwable) : RuntimeException(cause)

/** Request body of create and update. There is deliberately no owner field. */
data class BookRequest(
    val title: String,
    val author: String,
    val coverUrl: String? = null,
    val rating: Float,
    val review: String = "",
    val dateFinished: LocalDate
) {
    fun toDraft(): BookDraft = try {
        BookDraft(title, author, coverUrl, Rating(rating), review, dateFinished)
    } catch (e: IllegalArgumentException) {
        throw InvalidBookRequestException(e)
    }
}

data class BookResponse(
    val id: Long,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val rating: Float,
    val review: String,
    val dateFinished: LocalDate
)

fun Book.toResponse() =
    BookResponse(id.value, title, author, coverUrl, rating.value, review, dateFinished)
