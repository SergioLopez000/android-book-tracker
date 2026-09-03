package com.librosleidos.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * Task 3.2 (design D6): [BookDao]'s two explicit sort queries apply the
 * `minRating` filter and ordering; `upsert`/`deleteById` mutate persisted
 * rows. Spec: "Sort Book List", "Filter by Minimum Rating", "Delete Book
 * Entry". Uses a real in-memory Room database under Robolectric (design:
 * Testing Strategy).
 *
 * Task 4.4 (design D8): both queries also apply a nullable-bind
 * `fromEpochDay` lower bound — `(:fromEpochDay IS NULL OR
 * dateFinishedEpochDay >= :fromEpochDay)` — AND-combined with `minRating`.
 * Spec: "Filter by Date Range", "Combine Date-Range and Minimum-Rating
 * Filters".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookDaoTest {

    private lateinit var database: BooksDatabase
    private lateinit var dao: BookDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BooksDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.bookDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeByDateDesc orders books by date finished descending`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "Older", rating = 3.0f, dateFinishedEpochDay = 100))
        dao.upsert(bookEntity(id = 2, title = "Newer", rating = 3.0f, dateFinishedEpochDay = 200))

        val result = dao.observeByDateDesc(minRating = 0f, fromEpochDay = null).first()

        assertEquals(listOf("Newer", "Older"), result.map { it.title })
    }

    @Test
    fun `observeByRatingDesc orders books by rating descending`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "Low", rating = 2.0f, dateFinishedEpochDay = 100))
        dao.upsert(bookEntity(id = 2, title = "High", rating = 4.5f, dateFinishedEpochDay = 100))

        val result = dao.observeByRatingDesc(minRating = 0f, fromEpochDay = null).first()

        assertEquals(listOf("High", "Low"), result.map { it.title })
    }

    @Test
    fun `observeByDateDesc filters out books below minRating`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "Below", rating = 2.0f, dateFinishedEpochDay = 100))
        dao.upsert(bookEntity(id = 2, title = "AtMinimum", rating = 3.5f, dateFinishedEpochDay = 200))

        val result = dao.observeByDateDesc(minRating = 3.5f, fromEpochDay = null).first()

        assertEquals(listOf("AtMinimum"), result.map { it.title })
    }

    @Test
    fun `observeByRatingDesc filters out books below minRating`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "Below", rating = 2.0f, dateFinishedEpochDay = 100))
        dao.upsert(bookEntity(id = 2, title = "AtMinimum", rating = 3.5f, dateFinishedEpochDay = 100))

        val result = dao.observeByRatingDesc(minRating = 3.5f, fromEpochDay = null).first()

        assertEquals(listOf("AtMinimum"), result.map { it.title })
    }

    @Test
    fun `observeByDateDesc with a near bound keeps only the most recent books`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "VeryOld", rating = 2.0f, dateFinishedEpochDay = 10))
        dao.upsert(bookEntity(id = 2, title = "MediumOld", rating = 3.0f, dateFinishedEpochDay = 60))
        dao.upsert(bookEntity(id = 3, title = "Recent", rating = 4.0f, dateFinishedEpochDay = 300))
        dao.upsert(bookEntity(id = 4, title = "VeryRecent", rating = 5.0f, dateFinishedEpochDay = 400))

        val result = dao.observeByDateDesc(minRating = 0f, fromEpochDay = 300).first()

        assertEquals(listOf("VeryRecent", "Recent"), result.map { it.title })
    }

    @Test
    fun `observeByDateDesc with a wide bound keeps books from that point on`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "VeryOld", rating = 2.0f, dateFinishedEpochDay = 10))
        dao.upsert(bookEntity(id = 2, title = "MediumOld", rating = 3.0f, dateFinishedEpochDay = 60))
        dao.upsert(bookEntity(id = 3, title = "Recent", rating = 4.0f, dateFinishedEpochDay = 300))
        dao.upsert(bookEntity(id = 4, title = "VeryRecent", rating = 5.0f, dateFinishedEpochDay = 400))

        val result = dao.observeByDateDesc(minRating = 0f, fromEpochDay = 60).first()

        assertEquals(listOf("VeryRecent", "Recent", "MediumOld"), result.map { it.title })
    }

    @Test
    fun `observeByDateDesc with fromEpochDay null returns every book regardless of date`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "VeryOld", rating = 2.0f, dateFinishedEpochDay = 10))
        dao.upsert(bookEntity(id = 2, title = "MediumOld", rating = 3.0f, dateFinishedEpochDay = 60))
        dao.upsert(bookEntity(id = 3, title = "Recent", rating = 4.0f, dateFinishedEpochDay = 300))
        dao.upsert(bookEntity(id = 4, title = "VeryRecent", rating = 5.0f, dateFinishedEpochDay = 400))

        val result = dao.observeByDateDesc(minRating = 0f, fromEpochDay = null).first()

        assertEquals(
            listOf("VeryRecent", "Recent", "MediumOld", "VeryOld"),
            result.map { it.title },
        )
    }

    @Test
    fun `observeByRatingDesc with a near bound keeps only the most recent books`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "VeryOld", rating = 2.0f, dateFinishedEpochDay = 10))
        dao.upsert(bookEntity(id = 2, title = "MediumOld", rating = 3.0f, dateFinishedEpochDay = 60))
        dao.upsert(bookEntity(id = 3, title = "Recent", rating = 4.0f, dateFinishedEpochDay = 300))
        dao.upsert(bookEntity(id = 4, title = "VeryRecent", rating = 5.0f, dateFinishedEpochDay = 400))

        val result = dao.observeByRatingDesc(minRating = 0f, fromEpochDay = 300).first()

        assertEquals(listOf("VeryRecent", "Recent"), result.map { it.title })
    }

    @Test
    fun `observeByRatingDesc with a wide bound keeps books from that point on`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "VeryOld", rating = 2.0f, dateFinishedEpochDay = 10))
        dao.upsert(bookEntity(id = 2, title = "MediumOld", rating = 3.0f, dateFinishedEpochDay = 60))
        dao.upsert(bookEntity(id = 3, title = "Recent", rating = 4.0f, dateFinishedEpochDay = 300))
        dao.upsert(bookEntity(id = 4, title = "VeryRecent", rating = 5.0f, dateFinishedEpochDay = 400))

        val result = dao.observeByRatingDesc(minRating = 0f, fromEpochDay = 60).first()

        assertEquals(listOf("VeryRecent", "Recent", "MediumOld"), result.map { it.title })
    }

    @Test
    fun `observeByRatingDesc with fromEpochDay null returns every book regardless of date`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "VeryOld", rating = 2.0f, dateFinishedEpochDay = 10))
        dao.upsert(bookEntity(id = 2, title = "MediumOld", rating = 3.0f, dateFinishedEpochDay = 60))
        dao.upsert(bookEntity(id = 3, title = "Recent", rating = 4.0f, dateFinishedEpochDay = 300))
        dao.upsert(bookEntity(id = 4, title = "VeryRecent", rating = 5.0f, dateFinishedEpochDay = 400))

        val result = dao.observeByRatingDesc(minRating = 0f, fromEpochDay = null).first()

        assertEquals(
            listOf("VeryRecent", "Recent", "MediumOld", "VeryOld"),
            result.map { it.title },
        )
    }

    @Test
    fun `date-range bound and minRating both apply together (AND)`() = runTest {
        dao.upsert(bookEntity(id = 1, title = "OldHighRated", rating = 5.0f, dateFinishedEpochDay = 10))
        dao.upsert(bookEntity(id = 2, title = "RecentLowRated", rating = 2.0f, dateFinishedEpochDay = 300))
        dao.upsert(bookEntity(id = 3, title = "RecentHighRated", rating = 4.5f, dateFinishedEpochDay = 400))

        val result = dao.observeByDateDesc(minRating = 3.5f, fromEpochDay = 60).first()

        assertEquals(listOf("RecentHighRated"), result.map { it.title })
    }

    @Test
    fun `upsert inserts a new book and findById returns it`() = runTest {
        val id = dao.upsert(bookEntity(id = 0, title = "Fresh", rating = 5.0f, dateFinishedEpochDay = 1))

        val found = dao.findById(id)

        assertEquals("Fresh", found?.title)
    }

    @Test
    fun `deleteById removes the book`() = runTest {
        val id = dao.upsert(bookEntity(id = 0, title = "ToDelete", rating = 1.0f, dateFinishedEpochDay = 1))

        dao.deleteById(id)

        assertNull(dao.findById(id))
    }

    private fun bookEntity(
        id: Long,
        title: String,
        rating: Float,
        dateFinishedEpochDay: Long,
    ) = BookEntity(
        id = id,
        title = title,
        author = "Author",
        coverUrl = null,
        rating = rating,
        review = "",
        dateFinishedEpochDay = dateFinishedEpochDay,
    )
}
