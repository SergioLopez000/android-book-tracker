package com.librosleidos.ui.edit

import app.cash.turbine.test
import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.BookMetadata
import com.librosleidos.domain.model.Rating
import com.librosleidos.domain.port.BookMetadataSource
import com.librosleidos.domain.usecase.FakeBookRepository
import com.librosleidos.domain.usecase.GetBookByIdUseCase
import com.librosleidos.domain.usecase.SaveBookUseCase
import com.librosleidos.domain.usecase.SearchBookMetadataUseCase
import com.librosleidos.ui.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Task 6.2 (design: Data Flow — `BookEditRoute → BookEditViewModel →
 * SearchBookMetadataUseCase → BookMetadataSource`, `pick result → fields
 * prefill (still editable) → SaveBookUseCase`). Spec: "Autofill Editable
 * Fields", "Add Book via Metadata Lookup", "Validate Half-Star Rating",
 * "Edit Existing Book".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeBookMetadataSource(
        private val result: Result<List<BookMetadata>> = Result.success(emptyList()),
    ) : BookMetadataSource {
        override suspend fun search(query: String): Result<List<BookMetadata>> = result
    }

    private val duneMetadata = BookMetadata(title = "Dune", author = "Frank Herbert", coverUrl = "https://covers/dune.jpg")

    private val existingBook = Book(
        id = 7L,
        title = "Neuromancer",
        author = "William Gibson",
        coverUrl = null,
        rating = Rating(4.0f),
        review = "Classic cyberpunk.",
        dateFinished = LocalDate.of(2026, 1, 10),
    )

    private fun viewModel(
        repository: FakeBookRepository = FakeBookRepository(),
        metadataSource: BookMetadataSource = FakeBookMetadataSource(),
        bookId: Long? = null,
    ) = BookEditViewModel(
        saveBook = SaveBookUseCase(repository),
        searchMetadata = SearchBookMetadataUseCase(metadataSource),
        getBookById = GetBookByIdUseCase(repository),
        bookId = bookId,
    )

    @Test
    fun `initial state for a new book has no id and empty fields`() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.id)
            assertEquals("", state.title)
            assertNull(state.rating)
        }
    }

    @Test
    fun `constructing with an existing book id prefills fields from GetBookByIdUseCase`() = runTest {
        val repository = FakeBookRepository(seed = listOf(existingBook))
        val vm = viewModel(repository = repository, bookId = 7L)

        vm.uiState.test {
            // `UnconfinedTestDispatcher` runs the init block's load eagerly,
            // synchronously, before this collector attaches — so the first
            // (and only relevant) item already carries the loaded book.
            val loaded = awaitItem()
            assertEquals(7L, loaded.id)
            assertEquals("Neuromancer", loaded.title)
            assertEquals("William Gibson", loaded.author)
            assertEquals(4.0f, loaded.rating)
            assertEquals("Classic cyberpunk.", loaded.review)
            assertEquals(LocalDate.of(2026, 1, 10), loaded.dateFinished)
        }
    }

    @Test
    fun `search request calls SearchBookMetadataUseCase and exposes results`() = runTest {
        val vm = viewModel(metadataSource = FakeBookMetadataSource(Result.success(listOf(duneMetadata))))

        vm.uiState.test {
            awaitItem() // initial
            vm.onSearchQueryChanged("dune")
            awaitItem() // query text updated
            vm.onSearchRequested()
            assertEquals(SearchState.Loading, awaitItem().search)
            val state = awaitItem()
            assertEquals(SearchState.Results(listOf(duneMetadata)), state.search)
        }
    }

    @Test
    fun `selecting a search result prefills title, author and cover while staying editable`() = runTest {
        val vm = viewModel(metadataSource = FakeBookMetadataSource(Result.success(listOf(duneMetadata))))

        vm.uiState.test {
            awaitItem()
            vm.onResultSelected(duneMetadata)
            val state = awaitItem()
            assertEquals("Dune", state.title)
            assertEquals("Frank Herbert", state.author)
            assertEquals("https://covers/dune.jpg", state.coverUrl)
            assertEquals(SearchState.Idle, state.search)

            // still editable after prefill
            vm.onTitleChanged("Dune (Deluxe Edition)")
            assertEquals("Dune (Deluxe Edition)", awaitItem().title)
        }
    }

    @Test
    fun `save runs SaveBookUseCase and emits SaveCompleted on success`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onTitleChanged("Dune")
            vm.onAuthorChanged("Frank Herbert")
            vm.onRatingChanged(4.5f)
            vm.onSaveRequested()

            assertEquals(BookEditEffect.SaveCompleted, awaitItem())
        }
    }

    @Test
    fun `missing rating blocks save and surfaces a field error`() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.onTitleChanged("Dune")
            awaitItem()
            vm.onSaveRequested()
            // onSaveRequested emits twice: isSaving=true first, then the
            // rejected outcome with fieldErrors set (same shape as PR5's
            // documented two-step emission through a launched coroutine).
            awaitItem()
            val state = awaitItem()
            assertTrue(state.fieldErrors.containsKey(BookEditField.RATING))
            assertEquals(false, state.isSaving)
        }
    }

    @Test
    fun `invalid half-star rating blocks save and surfaces a field error`() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            awaitItem()
            vm.onTitleChanged("Dune")
            awaitItem()
            vm.onRatingChanged(3.7f)
            awaitItem()
            vm.onSaveRequested()
            awaitItem() // isSaving=true, see the note above
            val state = awaitItem()
            assertTrue(state.fieldErrors.containsKey(BookEditField.RATING))
        }
    }
}
