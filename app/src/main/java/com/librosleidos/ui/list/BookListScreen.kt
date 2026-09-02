package com.librosleidos.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

/**
 * Task 5.3 (design: container/presentational split). Stateless — the
 * container [BookListRoute] binds [BookListViewModel] to this composable.
 * Spec: "Books present", "Empty library", "Delete confirmed",
 * "Delete cancelled".
 */
@Composable
fun BookListScreen(
    state: BookListUiState,
    onSortSelected: (BookSort) -> Unit,
    onMinRatingSelected: (Float) -> Unit,
    onDeleteRequested: (Long) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.isEmpty -> Text(
                text = "No books yet. Add one to get started.",
                modifier = Modifier.align(Alignment.Center),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.books, key = { it.id ?: 0L }) { book ->
                    BookListItem(book = book, onRemoveRequested = onDeleteRequested)
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
private fun BookListItem(book: Book, onRemoveRequested: (Long) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = book.title)
        Text(text = book.author)
        Text(text = "${book.rating.value}")
        Text(text = "${book.dateFinished}")
        TextButton(onClick = { book.id?.let(onRemoveRequested) }) { Text("Remove") }
    }
}
