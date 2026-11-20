package com.librosleidos.backend.domain.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BookDraftTest {
    private fun draft(
        title: String = "Dune",
        author: String = "Frank Herbert",
        coverUrl: String? = null,
        rating: Float = 4.5f,
        review: String = "",
        dateFinished: LocalDate = LocalDate.of(2026, 1, 2)
    ) = BookDraft(title, author, coverUrl, Rating(rating), review, dateFinished)

    @Test
    fun `valid draft is built with optional cover`() {
        assertNull(draft().coverUrl)
        draft(title = "t".repeat(BookDraft.MAX_TITLE), author = "a".repeat(BookDraft.MAX_AUTHOR))
        draft(
            coverUrl = "u".repeat(BookDraft.MAX_COVER_URL),
            review = "r".repeat(BookDraft.MAX_REVIEW)
        )
    }

    @Test
    fun `title and author are required`() {
        assertFailsWith<IllegalArgumentException> { draft(title = " ") }
        assertFailsWith<IllegalArgumentException> { draft(author = "") }
    }

    @Test
    fun `fields are bounded`() {
        assertFailsWith<IllegalArgumentException> {
            draft(title = "t".repeat(BookDraft.MAX_TITLE + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            draft(author = "a".repeat(BookDraft.MAX_AUTHOR + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            draft(coverUrl = "u".repeat(BookDraft.MAX_COVER_URL + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            draft(review = "r".repeat(BookDraft.MAX_REVIEW + 1))
        }
    }
}
