package com.librosleidos.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.BookSort
import com.librosleidos.domain.model.DateRangeFilter
import com.librosleidos.ui.components.BookCoverImage
import com.librosleidos.ui.components.StarRating

/**
 * Task 5.3 (design: container/presentational split). Stateless — the
 * container [BookListRoute] binds [BookListViewModel] to this composable.
 * Spec: "Books present", "Empty library", "Delete confirmed",
 * "Delete cancelled".
 *
 * [onAddRequested] and [onBookClicked] (task 7.2: list<->add<->edit
 * navigation) default to no-ops — pure event forwarding with no branching
 * logic, TDD-exempt scaffold, same as [BookListRoute]. `MainActivity`'s
 * NavHost supplies the real navigation callbacks.
 */
@Composable
fun BookListScreen(
    state: BookListUiState,
    onSortSelected: (BookSort) -> Unit,
    onMinRatingSelected: (Float) -> Unit,
    onDeleteRequested: (Long) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onDateRangeSelected: (DateRangeFilter) -> Unit = {},
    onAddRequested: () -> Unit = {},
    onBookClicked: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TextButton(onClick = onAddRequested) { Text("Add book") }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DateRangeFilter.values().forEach { filter ->
                val label = when (filter) {
                    DateRangeFilter.LAST_MONTH -> "Last Month"
                    DateRangeFilter.LAST_YEAR -> "Last Year"
                    DateRangeFilter.ALL_TIME -> "All Time"
                }
                TextButton(
                    onClick = { onDateRangeSelected(filter) },
                    enabled = state.dateRange != filter
                ) {
                    Text(text = label)
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.isEmpty -> Text(
                    text = "No books yet. Add one to get started.",
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = state.books, key = { it.id ?: 0L }) { book ->
                        BookListItem(
                            book = book,
                            onRemoveRequested = onDeleteRequested,
                            onClicked = onBookClicked,
                        )
                    }
                }
            }
        }
    }

    val pendingId = state.pendingDeleteId
    if (pendingId != null) {
        val pendingBook = state.books.find { it.id == pendingId }
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            title = { Text("Delete book?") },
            text = {
                Text(
                    "Remove \"${pendingBook?.title.orEmpty()}\" from your library? " +
                        "This cannot be undone.",
                )
            },
            confirmButton = { TextButton(onClick = onDeleteConfirmed) { Text("Delete") } },
            dismissButton = { TextButton(onClick = onDeleteCancelled) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BookListItem(
    book: Book,
    onRemoveRequested: (Long) -> Unit,
    onClicked: (Long) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = book.id != null) { book.id?.let(onClicked) }
            .padding(16.dp),
    ) {
        BookCoverImage(
            coverUrl = book.coverUrl,
            contentDescription = "${book.title} cover",
            modifier = Modifier.size(64.dp),
        )
        Text(text = book.title)
        Text(text = book.author)
        StarRating(rating = book.rating.value)
        Text(text = "${book.dateFinished}")
        TextButton(onClick = { book.id?.let(onRemoveRequested) }) { Text("Remove") }
    }
}
