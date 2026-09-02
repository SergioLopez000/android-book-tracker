package com.librosleidos.ui.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.librosleidos.domain.model.BookMetadata
import com.librosleidos.domain.model.Rating

/**
 * Task 6.3 (design: container/presentational split). Stateless — the
 * container [BookEditRoute] binds [BookEditViewModel] to this composable.
 * Spec: "Add Book via Metadata Lookup", "Add Book Manually", "Validate
 * Half-Star Rating", "Require Rating on Save", "Edit Existing Book",
 * "Autofill Editable Fields".
 */
@Composable
fun BookEditScreen(
    state: BookEditUiState,
    onTitleChanged: (String) -> Unit,
    onAuthorChanged: (String) -> Unit,
    onRatingChanged: (Float) -> Unit,
    onReviewChanged: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearchRequested: () -> Unit,
    onResultSelected: (BookMetadata) -> Unit,
    onSaveRequested: () -> Unit,
    onCancelRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChanged,
            label = { Text("Search by title") },
        )
        Button(onClick = onSearchRequested) { Text("Search") }

        SearchResults(search = state.search, onResultSelected = onResultSelected)

        OutlinedTextField(value = state.title, onValueChange = onTitleChanged, label = { Text("Title") })
        state.fieldErrors[BookEditField.TITLE]?.let { ErrorText(it) }

        OutlinedTextField(value = state.author, onValueChange = onAuthorChanged, label = { Text("Author") })

        RatingInput(rating = state.rating, onRatingChanged = onRatingChanged)
        state.fieldErrors[BookEditField.RATING]?.let { ErrorText(it) }

        OutlinedTextField(value = state.review, onValueChange = onReviewChanged, label = { Text("Comment") })

        state.fieldErrors[BookEditField.DATE_FINISHED]?.let { ErrorText(it) }

        Row {
            TextButton(onClick = onCancelRequested) { Text("Cancel") }
            Button(onClick = onSaveRequested, enabled = !state.isSaving) { Text("Save") }
        }
    }
}

@Composable
private fun SearchResults(search: SearchState, onResultSelected: (BookMetadata) -> Unit) {
    when (search) {
        is SearchState.Results -> if (search.results.isEmpty()) {
            Text("No matches found. You can still enter the details manually.")
        } else {
            Column {
                search.results.forEach { result ->
                    TextButton(onClick = { onResultSelected(result) }) {
                        Text("${result.title} — ${result.author}")
                    }
                }
            }
        }
        SearchState.Loading -> Text("Searching…")
        SearchState.Unavailable -> Text("Search unavailable. You can still enter the details manually.")
        SearchState.Idle -> Unit
    }
}

@Composable
private fun RatingInput(rating: Float?, onRatingChanged: (Float) -> Unit) {
    Column {
        Text("Rating")
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            ratingSteps.forEach { step ->
                val label = "%.1f".format(step)
                TextButton(onClick = { onRatingChanged(step) }) {
                    Text(text = label, fontWeight = if (rating == step) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(text = message, color = MaterialTheme.colorScheme.error)
}

private val ratingSteps: List<Float> =
    generateSequence(Rating.MIN) { it + 0.5f }.takeWhile { it <= Rating.MAX }.toList()
