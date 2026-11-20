package com.librosleidos.backend.domain.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pins every exposed value, so a mutant that returns a default instead is caught. */
class AccessorsTest {
    private val day = LocalDate.of(2026, 1, 2)

    @Test
    fun `value objects expose exactly what they were built with`() {
        assertEquals(7L, BookId(7).value)
        assertEquals("alice", OwnerId("alice").value)
        assertEquals(3.5f, Rating(3.5f).value)
    }

    @Test
    fun `a draft exposes every field`() {
        val draft =
            BookDraft("Dune", "Frank Herbert", "https://x.test/c.png", Rating(4.5f), "Great", day)
        assertEquals("Dune", draft.title)
        assertEquals("Frank Herbert", draft.author)
        assertEquals("https://x.test/c.png", draft.coverUrl)
        assertEquals(4.5f, draft.rating.value)
        assertEquals("Great", draft.review)
        assertEquals(day, draft.dateFinished)
    }

    @Test
    fun `a book exposes every field`() {
        val book = Book(BookId(1), "Dune", "Frank Herbert", null, Rating(4.5f), "Great", day)
        assertEquals(1L, book.id.value)
        assertEquals("Dune", book.title)
        assertEquals("Frank Herbert", book.author)
        assertNull(book.coverUrl)
        assertEquals(4.5f, book.rating.value)
        assertEquals("Great", book.review)
        assertEquals(day, book.dateFinished)
        assertEquals("https://x.test/c.png", book.copy(coverUrl = "https://x.test/c.png").coverUrl)
    }
}
