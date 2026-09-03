package com.librosleidos.ui.list

import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.BookSort
import com.librosleidos.domain.model.DateRangeFilter

/**
 * Task 5.1 (design: UI State — `BookListUiState(books, sort, minRating,
 * isLoading, isEmpty)`). State shape only, no logic — TDD-exempt scaffold.
 *
 * [pendingDeleteId] is an addition beyond the design snippet, needed to
 * drive the delete-confirmation dialog (spec: "Delete confirmed",
 * "Delete cancelled") without a separate one-shot channel.
 */
data class BookListUiState(
    val books: List<Book> = emptyList(),
    val sort: BookSort = BookSort.DATE_FINISHED_DESC,
    val minRating: Float = 0f,
    val dateRange: DateRangeFilter = DateRangeFilter.ALL_TIME,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val pendingDeleteId: Long? = null,
)
