package com.librosleidos.ui.edit

import com.librosleidos.domain.model.BookMetadata
import java.time.LocalDate

/**
 * Task 6.1 (design: UI State — `BookEditUiState(id, title, author, coverUrl,
 * rating, review, dateFinished, search, fieldErrors, isSaving)`). State
 * shape only, no logic — TDD-exempt scaffold.
 *
 * [searchQuery] is an addition beyond the design snippet: the metadata
 * search box needs its own editable text separate from the (possibly
 * already-prefilled) `title` field, so a user can search for one title
 * while still editing a different one.
 */
data class BookEditUiState(
    val id: Long? = null,
    val title: String = "",
    val author: String = "",
    val coverUrl: String? = null,
    val rating: Float? = null,
    val review: String = "",
    val dateFinished: LocalDate = LocalDate.now(),
    val searchQuery: String = "",
    val search: SearchState = SearchState.Idle,
    val fieldErrors: Map<BookEditField, String> = emptyMap(),
    val isSaving: Boolean = false,
)

/**
 * Metadata lookup state (design: `book-metadata-lookup`). [Unavailable] is
 * part of the design's literal type shape but is not currently reachable
 * through [com.librosleidos.domain.usecase.SearchBookMetadataUseCase] — see
 * [BookEditViewModel]'s doc comment for why.
 */
sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val results: List<BookMetadata>) : SearchState
    data object Unavailable : SearchState
}

/** Which entry-form field a [SaveBookRejection][com.librosleidos.domain.usecase.SaveBookRejection] maps to. */
enum class BookEditField { TITLE, RATING, DATE_FINISHED }
