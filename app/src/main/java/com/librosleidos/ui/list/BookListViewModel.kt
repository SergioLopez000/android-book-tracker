package com.librosleidos.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librosleidos.domain.model.BookSort
import com.librosleidos.domain.usecase.DeleteBookUseCase
import com.librosleidos.domain.usecase.GetBooksUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class ListQuery(val sort: BookSort, val minRating: Float)

/**
 * Task 5.2 (design: Data Flow — `BookListRoute ← StateFlow ←
 * BookListViewModel ← GetBooksUseCase`). Spec: "List Read Books",
 * "Sort Book List", "Filter by Minimum Rating", "Delete confirmed",
 * "Delete cancelled".
 *
 * Query changes flow through `MutableStateFlow<ListQuery> → flatMapLatest →
 * stateIn(WhileSubscribed(5_000))` exactly as design's Data Flow section
 * describes; [pendingDeleteId] is combined in separately since it does not
 * affect the underlying repository query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookListViewModel(
    private val getBooks: GetBooksUseCase,
    private val deleteBook: DeleteBookUseCase,
) : ViewModel() {

    private val query = MutableStateFlow(ListQuery(sort = BookSort.DATE_FINISHED_DESC, minRating = 0f))
    private val pendingDeleteId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<BookListUiState> = query
        .flatMapLatest { current ->
            getBooks(current.sort, current.minRating).map { books ->
                BookListUiState(
                    books = books,
                    sort = current.sort,
                    minRating = current.minRating,
                    isLoading = false,
                    isEmpty = books.isEmpty(),
                )
            }
        }
        .combine(pendingDeleteId) { state, pendingId -> state.copy(pendingDeleteId = pendingId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookListUiState(isLoading = true),
        )

    fun onSortSelected(sort: BookSort) {
        query.value = query.value.copy(sort = sort)
    }

    fun onMinRatingSelected(minRating: Float) {
        query.value = query.value.copy(minRating = minRating)
    }

    fun onDeleteRequested(id: Long) {
        pendingDeleteId.value = id
    }

    fun onDeleteCancelled() {
        pendingDeleteId.value = null
    }

    fun onDeleteConfirmed() {
        val id = pendingDeleteId.value ?: return
        pendingDeleteId.value = null
        viewModelScope.launch {
            deleteBook(id)
        }
    }
}
