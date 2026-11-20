package com.librosleidos.backend.domain.model

import java.time.LocalDate

/** Client-supplied book data, validated on construction. The owner is never part of the draft. */
data class BookDraft(
    val title: String,
    val author: String,
    val coverUrl: String?,
    val rating: Rating,
    val review: String,
    val dateFinished: LocalDate
) {
    init {
        require(title.isNotBlank() && title.length <= MAX_TITLE) { "Invalid title" }
        require(author.isNotBlank() && author.length <= MAX_AUTHOR) { "Invalid author" }
        require(coverUrl == null || coverUrl.length <= MAX_COVER_URL) { "Invalid cover URL" }
        require(review.length <= MAX_REVIEW) { "Invalid review" }
    }

    companion object {
        const val MAX_TITLE = 200
        const val MAX_AUTHOR = 200
        const val MAX_COVER_URL = 2048
        const val MAX_REVIEW = 5000
    }
}
