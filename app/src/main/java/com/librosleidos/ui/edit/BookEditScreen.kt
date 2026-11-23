package com.librosleidos.ui.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.librosleidos.domain.model.BookMetadata
import com.librosleidos.ui.components.BookCoverImage
import com.librosleidos.ui.components.StarRating

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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        BookCoverImage(
            coverUrl = state.coverUrl,
            contentDescription = "${state.title} cover",
            modifier = Modifier.size(96.dp)
        )

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.testTag(BookEditTags.SEARCH_FIELD),
            label = { Text("Search by title") }
        )
        Button(
            onClick = onSearchRequested,
            modifier = Modifier.testTag(BookEditTags.SEARCH_BUTTON)
        ) { Text("Search") }

        SearchResults(search = state.search, onResultSelected = onResultSelected)

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChanged,
            modifier = Modifier.testTag(BookEditTags.TITLE_FIELD),
            label = { Text("Title") }
        )
        state.fieldErrors[BookEditField.TITLE]?.let { ErrorText(BookEditField.TITLE, it) }

        OutlinedTextField(
            value = state.author,
            onValueChange = onAuthorChanged,
            modifier = Modifier.testTag(BookEditTags.AUTHOR_FIELD),
            label = { Text("Author") }
        )

        StarRating(
            rating = state.rating,
            onRatingChange = onRatingChanged,
            tapTargetTagPrefix = BookEditTags.RATING_PREFIX
        )
        state.fieldErrors[BookEditField.RATING]?.let { ErrorText(BookEditField.RATING, it) }

        OutlinedTextField(
            value = state.review,
            onValueChange = onReviewChanged,
            modifier = Modifier.testTag(BookEditTags.REVIEW_FIELD),
            label = { Text("Comment") }
        )

        state.fieldErrors[BookEditField.DATE_FINISHED]?.let {
            ErrorText(BookEditField.DATE_FINISHED, it)
        }

        Row {
            TextButton(
                onClick = onCancelRequested,
                modifier = Modifier.testTag(BookEditTags.CANCEL_BUTTON)
            ) { Text("Cancel") }
            Button(
                onClick = onSaveRequested,
                enabled = !state.isSaving,
                modifier = Modifier.testTag(BookEditTags.SAVE_BUTTON)
            ) { Text("Save") }
        }
    }
}

@Composable
private fun SearchResults(search: SearchState, onResultSelected: (BookMetadata) -> Unit) {
    when (search) {
        is SearchState.Results -> if (search.results.isEmpty()) {
            Text(
                "No matches found. You can still enter the details manually.",
                modifier = Modifier.testTag(BookEditTags.SEARCH_STATUS)
            )
        } else {
            Column {
                search.results.forEachIndexed { index, result ->
                    TextButton(
                        onClick = { onResultSelected(result) },
                        modifier = Modifier.testTag(BookEditTags.searchResult(index))
                    ) {
                        Text("${result.title} — ${result.author}")
                    }
                }
            }
        }

        SearchState.Loading -> Text(
            "Searching…",
            modifier = Modifier.testTag(BookEditTags.SEARCH_STATUS)
        )

        SearchState.Unavailable -> Text(
            "Search unavailable. You can still enter the details manually.",
            modifier = Modifier.testTag(BookEditTags.SEARCH_STATUS)
        )

        SearchState.Idle -> Unit
    }
}

@Composable
private fun ErrorText(field: BookEditField, message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(BookEditTags.error(field))
    )
}
