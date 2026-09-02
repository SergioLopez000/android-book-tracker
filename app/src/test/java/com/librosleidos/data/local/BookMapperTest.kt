package com.librosleidos.data.local

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.Rating
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Task 3.4 (design D5): [Book] <-> [BookEntity] mapping, including
 * `LocalDate` <-> epoch-day round-trip. Pure JVM — no Room/Robolectric
 * boundary in the mapper itself.
 */
class BookMapperTest {

    @Test
    fun `maps a domain Book to a BookEntity`() {
        val book = Book(
            id = 7L,
            title = "Dune",
            author = "Frank Herbert",
            coverUrl = "https://example.com/dune.jpg",
            rating = Rating(4.5f),
            review = "Excellent",
            dateFinished = LocalDate.of(2024, 3, 15),
        )

        val entity = book.toEntity()

        assertEquals(7L, entity.id)
        assertEquals("Dune", entity.title)
        assertEquals("Frank Herbert", entity.author)
        assertEquals("https://example.com/dune.jpg", entity.coverUrl)
        assertEquals(4.5f, entity.rating)
        assertEquals("Excellent", entity.review)
        assertEquals(LocalDate.of(2024, 3, 15).toEpochDay(), entity.dateFinishedEpochDay)
    }

    @Test
    fun `maps an unsaved Book (null id) to entity id zero`() {
        val book = Book(
            id = null,
            title = "New",
            author = "Someone",
            coverUrl = null,
            rating = Rating(1.0f),
            review = "",
            dateFinished = LocalDate.of(2024, 1, 1),
        )

        val entity = book.toEntity()

        assertEquals(0L, entity.id)
    }

    @Test
    fun `maps a BookEntity back to a domain Book, round-tripping the date`() {
        val entity = BookEntity(
            id = 3,
            title = "1984",
            author = "George Orwell",
            coverUrl = null,
            rating = 5.0f,
            review = "",
            dateFinishedEpochDay = LocalDate.of(2023, 1, 1).toEpochDay(),
        )

        val book = entity.toDomain()

        assertEquals(3L, book.id)
        assertEquals("1984", book.title)
        assertEquals("George Orwell", book.author)
        assertNull(book.coverUrl)
        assertEquals(Rating(5.0f), book.rating)
        assertEquals("", book.review)
        assertEquals(LocalDate.of(2023, 1, 1), book.dateFinished)
    }

    @Test
    fun `round-trips a Book through entity and back unchanged`() {
        val original = Book(
            id = 42L,
            title = "Foundation",
            author = "Isaac Asimov",
            coverUrl = null,
            rating = Rating(3.5f),
            review = "Classic",
            dateFinished = LocalDate.of(2022, 6, 30),
        )

        val roundTripped = original.toEntity().toDomain()

        assertEquals(original, roundTripped)
    }
}
