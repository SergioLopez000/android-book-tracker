package com.librosleidos.ui.list

import app.cash.turbine.test
import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.Rating
import com.librosleidos.domain.model.BookSort
import com.librosleidos.domain.usecase.DeleteBookUseCase
import com.librosleidos.domain.usecase.FakeBookRepository
import com.librosleidos.domain.usecase.GetBooksUseCase
import com.librosleidos.ui.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Task 5.2 (design: Data Flow — BookListRoute ← StateFlow ←
 * BookListViewModel ← GetBooksUseCase). Spec: "List Read Books",
 * "Sort Book List", "Filter by Minimum Rating", "Delete confirmed",
 * "Delete cancelled".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun book(id: Long, title: String, rating: Float, daysAgo: Long) = Book(
        id = id,
        title = title,
        author = "Author $id",
        coverUrl = null,
        rating = Rating(rating),
        review = "",
        dateFinished = LocalDate.now().minusDays(daysAgo),
    )

    private fun viewModel(repository: FakeBookRepository) =
        BookListViewModel(GetBooksUseCase(repository), DeleteBookUseCase(repository))

    @Test
    fun `books present are exposed and isEmpty is false`() = runTest {
        val repository = FakeBookRepository(
            seed = listOf(book(1, "Dune", 4.5f, 1), book(2, "Neuromancer", 3.5f, 2)),
        )
        val vm = viewModel(repository)

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.books.size)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isEmpty)
        }
    }

    @Test
    fun `no persisted books yields isEmpty true`() = runTest {
        val vm = viewModel(FakeBookRepository())

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(true, state.isEmpty)
            assertEquals(false, state.isLoading)
        }
    }

    @Test
    fun `selecting a sort re-queries the repository with the new sort`() = runTest {
        val repository = FakeBookRepository(seed = listOf(book(1, "Dune", 4.5f, 1)))
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem()
            vm.onSortSelected(BookSort.RATING_DESC)
            val state = awaitItem()
            assertEquals(BookSort.RATING_DESC, state.sort)
            assertEquals(BookSort.RATING_DESC, repository.lastRequestedSort)
        }
    }

    @Test
    fun `setting a minimum rating filters the list`() = runTest {
        val repository = FakeBookRepository(
            seed = listOf(
                book(1, "Low", 2.0f, 1),
                book(2, "Mid", 3.5f, 2),
                book(3, "High", 5.0f, 3),
            ),
        )
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem()
            vm.onMinRatingSelected(3.5f)
            val state = awaitItem()
            assertEquals(3.5f, state.minRating)
            assertEquals(setOf("Mid", "High"), state.books.map { it.title }.toSet())
        }
    }

    @Test
    fun `delete request sets pendingDeleteId without deleting yet`() = runTest {
        val repository = FakeBookRepository(seed = listOf(book(1, "Dune", 4.5f, 1)))
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem()
            vm.onDeleteRequested(1L)
            val state = awaitItem()
            assertEquals(1L, state.pendingDeleteId)
            assertNull(repository.lastDeletedId)
            assertEquals(1, state.books.size)
        }
    }

    @Test
    fun `confirming delete removes the book and clears the pending id`() = runTest {
        val repository = FakeBookRepository(seed = listOf(book(1, "Dune", 4.5f, 1)))
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem()
            vm.onDeleteRequested(1L)
            awaitItem()
            vm.onDeleteConfirmed()
            // pendingDeleteId clears synchronously; the repository update
            // (and resulting books list change) lands in a follow-up emission.
            awaitItem()
            val state = awaitItem()
            assertNull(state.pendingDeleteId)
            assertEquals(1L, repository.lastDeletedId)
            assertEquals(0, state.books.size)
        }
    }

    @Test
    fun `cancelling delete clears the pending id and leaves the book untouched`() = runTest {
        val repository = FakeBookRepository(seed = listOf(book(1, "Dune", 4.5f, 1)))
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem()
            vm.onDeleteRequested(1L)
            awaitItem()
            vm.onDeleteCancelled()
            val state = awaitItem()
            assertNull(state.pendingDeleteId)
            assertNull(repository.lastDeletedId)
            assertEquals(1, state.books.size)
        }
    }

    @Test
    fun `setting a date range filter filters the list`() = runTest {
        val repository = FakeBookRepository(
            seed = listOf(
                book(1, "Recent", 5.0f, 2),
                book(2, "Last Month", 5.0f, 40),
                book(3, "Older", 5.0f, 400),
            ),
        )
        val vm = viewModel(repository)

        vm.uiState.test {
            val initial = awaitItem()
            assertEquals(com.librosleidos.domain.model.DateRangeFilter.ALL_TIME, initial.dateRange)
            assertEquals(3, initial.books.size)

            vm.onDateRangeSelected(com.librosleidos.domain.model.DateRangeFilter.LAST_MONTH)
            val stateMonth = awaitItem()
            assertEquals(com.librosleidos.domain.model.DateRangeFilter.LAST_MONTH, stateMonth.dateRange)
            assertEquals(setOf("Recent"), stateMonth.books.map { it.title }.toSet())

            vm.onDateRangeSelected(com.librosleidos.domain.model.DateRangeFilter.LAST_YEAR)
            val stateYear = awaitItem()
            assertEquals(com.librosleidos.domain.model.DateRangeFilter.LAST_YEAR, stateYear.dateRange)
            assertEquals(setOf("Recent", "Last Month"), stateYear.books.map { it.title }.toSet())
        }
    }
}
