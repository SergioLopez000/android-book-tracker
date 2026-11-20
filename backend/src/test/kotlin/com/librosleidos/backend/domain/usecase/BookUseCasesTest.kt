package com.librosleidos.backend.domain.usecase

import com.librosleidos.backend.domain.model.BookDraft
import com.librosleidos.backend.domain.model.BookId
import com.librosleidos.backend.domain.model.OwnerId
import com.librosleidos.backend.domain.model.Rating
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BookUseCasesTest {
    private val repo = InMemoryBookRepository()
    private val create = CreateBookUseCase(repo)
    private val get = GetBookUseCase(repo)
    private val list = ListBooksUseCase(repo)
    private val update = UpdateBookUseCase(repo)
    private val delete = DeleteBookUseCase(repo)

    private val alice = OwnerId("alice")
    private val bob = OwnerId("bob")

    private fun draft(title: String = "Dune") =
        BookDraft(title, "Frank Herbert", null, Rating(4.5f), "great", LocalDate.of(2026, 1, 2))

    @Test
    fun `create stores the book for the owner and returns it`() {
        val book = create(alice, draft())
        assertEquals("Dune", book.title)
        assertEquals(book, assertIs<GetBookOutcome.Found>(get(alice, book.id)).book)
    }

    @Test
    fun `get returns not found for an unknown id`() {
        assertIs<GetBookOutcome.NotFound>(get(alice, BookId(99)))
    }

    @Test
    fun `get of another owner's book is not found`() {
        val book = create(alice, draft())
        assertIs<GetBookOutcome.NotFound>(get(bob, book.id))
    }

    @Test
    fun `list returns only the caller's books`() {
        val a = create(alice, draft("A"))
        create(bob, draft("B"))
        assertEquals(listOf(a), list(alice))
        assertEquals(listOf("B"), list(bob).map { it.title })
    }

    @Test
    fun `update changes the owner's book`() {
        val book = create(alice, draft())
        val updated = assertIs<UpdateBookOutcome.Updated>(
            update(alice, book.id, draft("Messiah"))
        ).book
        assertEquals(book.id, updated.id)
        assertEquals("Messiah", updated.title)
    }

    @Test
    fun `update of another owner's book is not found and changes nothing`() {
        val book = create(alice, draft())
        assertIs<UpdateBookOutcome.NotFound>(update(bob, book.id, draft("Hijack")))
        assertEquals(book, assertIs<GetBookOutcome.Found>(get(alice, book.id)).book)
    }

    @Test
    fun `delete removes the owner's book`() {
        val book = create(alice, draft())
        assertIs<DeleteBookOutcome.Deleted>(delete(alice, book.id))
        assertIs<GetBookOutcome.NotFound>(get(alice, book.id))
    }

    @Test
    fun `delete of another owner's book is not found and changes nothing`() {
        val book = create(alice, draft())
        assertIs<DeleteBookOutcome.NotFound>(delete(bob, book.id))
        assertIs<GetBookOutcome.Found>(get(alice, book.id))
    }
}
