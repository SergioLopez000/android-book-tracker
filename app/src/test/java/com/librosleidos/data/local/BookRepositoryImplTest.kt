package com.librosleidos.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.BookSort
import com.librosleidos.domain.model.Rating
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 3.5: [BookRepositoryImpl] implements [com.librosleidos.domain.port.BookRepository]
 * against [BookDao]; `observeBooks` reflects sort/minRating changes. Uses a
 * real in-memory Room database under Robolectric — no DAO fakes, so the
 * mapper and query wiring are exercised end to end (design: Testing
 * Strategy).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookRepositoryImplTest {

    private lateinit var database: BooksDatabase
    private lateinit var repository: BookRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BooksDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = BookRepositoryImpl(database.bookDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `save persists a new book and assigns an id`() = runTest {
        val id = repository.save(book(id = null, title = "Dune"))

        val found = repository.findById(id)

        assertEquals("Dune", found?.title)
    }

    @Test
    fun `save updates an existing book and keeps its id`() = runTest {
        val id = repository.save(book(id = null, title = "Original"))

        val updatedId = repository.save(book(id = id, title = "Updated"))

        assertEquals(id, updatedId)
        assertEquals("Updated", repository.findById(id)?.title)
    }

    @Test
    fun `findById returns null for a book that does not exist`() = runTest {
        val found = repository.findById(999L)

        assertNull(found)
    }

    @Test
    fun `delete removes the book from persistence`() = runTest {
        val id = repository.save(book(id = null, title = "ToDelete"))

        repository.delete(id)

        assertNull(repository.findById(id))
    }

    @Test
    fun `observeBooks reflects a sort change from date to rating`() = runTest {
        repository.save(
            book(id = null, title = "LowRatingRecent", rating = 2.0f, date = LocalDate.of(2024, 1, 1)),
        )
        repository.save(
            book(id = null, title = "HighRatingOld", rating = 4.5f, date = LocalDate.of(2020, 1, 1)),
        )

        val byDate = repository.observeBooks(BookSort.DATE_FINISHED_DESC, minRating = 0f).first()
        val byRating = repository.observeBooks(BookSort.RATING_DESC, minRating = 0f).first()

        assertEquals(listOf("LowRatingRecent", "HighRatingOld"), byDate.map { it.title })
        assertEquals(listOf("HighRatingOld", "LowRatingRecent"), byRating.map { it.title })
    }

    @Test
    fun `observeBooks reflects a minRating filter change`() = runTest {
        repository.save(book(id = null, title = "Below", rating = 2.0f))
        repository.save(book(id = null, title = "AtMinimum", rating = 3.5f))

        val unfiltered = repository.observeBooks(BookSort.RATING_DESC, minRating = 0f).first()
        val filtered = repository.observeBooks(BookSort.RATING_DESC, minRating = 3.5f).first()

        assertEquals(listOf("AtMinimum", "Below"), unfiltered.map { it.title })
        assertEquals(listOf("AtMinimum"), filtered.map { it.title })
    }

    private fun book(
        id: Long?,
        title: String,
        rating: Float = 4.0f,
        date: LocalDate = LocalDate.of(2023, 5, 1),
    ) = Book(
        id = id,
        title = title,
        author = "Author",
        coverUrl = null,
        rating = Rating(rating),
        review = "",
        dateFinished = date,
    )
}
