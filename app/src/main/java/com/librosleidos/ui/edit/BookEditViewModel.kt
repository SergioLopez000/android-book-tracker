package com.librosleidos.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.librosleidos.domain.model.BookMetadata
import com.librosleidos.domain.usecase.GetBookByIdUseCase
import com.librosleidos.domain.usecase.SaveBookOutcome
import com.librosleidos.domain.usecase.SaveBookRejection
import com.librosleidos.domain.usecase.SaveBookRequest
import com.librosleidos.domain.usecase.SaveBookUseCase
import com.librosleidos.domain.usecase.SearchBookMetadataUseCase
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One-shot events (design D7: "one-shot nav/snackbar via
 * `Channel().receiveAsFlow()`"). [SaveCompleted] is genuinely single-fire —
 * unlike PR5's delete-confirmation dialog (persistent, assertable state kept
 * in [BookEditUiState] via `search`/`fieldErrors`), a completed save should
 * fire navigation exactly once and never replay on recomposition.
 */
sealed interface BookEditEffect {
    data object SaveCompleted : BookEditEffect
}

/**
 * Task 6.2 (design: Data Flow — `BookEditRoute → BookEditViewModel →
 * SearchBookMetadataUseCase → BookMetadataSource`, `pick result → fields
 * prefill (still editable) → SaveBookUseCase → BookRepository`). Spec:
 * "Autofill Editable Fields", "Add Book via Metadata Lookup", "Validate
 * Half-Star Rating", "Edit Existing Book".
 *
 * [SearchState.Unavailable] is part of the design's literal type shape but
 * is not reachable through this ViewModel today:
 * [SearchBookMetadataUseCase] already collapses a source failure to an
 * empty result (Phase 2/4 design decision), so a network failure and a
 * genuine no-match both surface here as `SearchState.Results(emptyList())`.
 * This is a known simplification carried up from the use-case boundary, not
 * a bug introduced in this screen — flagged per the spec's "Graceful
 * Degradation" requirement, whose two scenarios ("No results from either
 * source" vs. "Network failure during lookup") call for distinguishable
 * messaging that the current use-case contract cannot provide.
 */
class BookEditViewModel(
    private val saveBook: SaveBookUseCase,
    private val searchMetadata: SearchBookMetadataUseCase,
    private val getBookById: GetBookByIdUseCase,
    bookId: Long? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookEditUiState(id = bookId))
    val uiState: StateFlow<BookEditUiState> = _uiState.asStateFlow()

    private val _effects = Channel<BookEditEffect>(Channel.BUFFERED)
    val effects: Flow<BookEditEffect> = _effects.receiveAsFlow()

    init {
        if (bookId != null) {
            viewModelScope.launch {
                val book = getBookById(bookId) ?: return@launch
                _uiState.update {
                    it.copy(
                        id = book.id,
                        title = book.title,
                        author = book.author,
                        coverUrl = book.coverUrl,
                        rating = book.rating.value,
                        review = book.review,
                        dateFinished = book.dateFinished,
                    )
                }
            }
        }
    }

    fun onTitleChanged(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun onAuthorChanged(author: String) {
        _uiState.update { it.copy(author = author) }
    }

    fun onRatingChanged(rating: Float?) {
        _uiState.update { it.copy(rating = rating) }
    }

    fun onReviewChanged(review: String) {
        _uiState.update { it.copy(review = review) }
    }

    fun onDateFinishedChanged(dateFinished: LocalDate) {
        _uiState.update { it.copy(dateFinished = dateFinished) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchRequested() {
        val query = _uiState.value.searchQuery
        _uiState.update { it.copy(search = SearchState.Loading) }
        viewModelScope.launch {
            val results = searchMetadata(query)
            _uiState.update { it.copy(search = SearchState.Results(results)) }
        }
    }

    fun onResultSelected(result: BookMetadata) {
        _uiState.update {
            it.copy(
                title = result.title,
                author = result.author,
                coverUrl = result.coverUrl,
                search = SearchState.Idle,
            )
        }
    }

    fun onSaveRequested() {
        val current = _uiState.value
        _uiState.update { it.copy(isSaving = true, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val outcome = saveBook(
                SaveBookRequest(
                    id = current.id,
                    title = current.title,
                    author = current.author,
                    coverUrl = current.coverUrl,
                    rating = current.rating,
                    review = current.review,
                    dateFinished = current.dateFinished,
                ),
            )
            when (outcome) {
                is SaveBookOutcome.Saved -> {
                    _uiState.update { it.copy(id = outcome.id, isSaving = false) }
                    _effects.send(BookEditEffect.SaveCompleted)
                }
                is SaveBookOutcome.Rejected -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        fieldErrors = mapOf(fieldFor(outcome.reason) to messageFor(outcome.reason)),
                    )
                }
            }
        }
    }

    private fun fieldFor(reason: SaveBookRejection): BookEditField = when (reason) {
        SaveBookRejection.BLANK_TITLE -> BookEditField.TITLE
        SaveBookRejection.MISSING_RATING, SaveBookRejection.INVALID_RATING -> BookEditField.RATING
        SaveBookRejection.FUTURE_DATE_FINISHED -> BookEditField.DATE_FINISHED
    }

    private fun messageFor(reason: SaveBookRejection): String = when (reason) {
        SaveBookRejection.BLANK_TITLE -> "Title is required."
        SaveBookRejection.MISSING_RATING -> "Rating is required."
        SaveBookRejection.INVALID_RATING -> "Rating must be a half-star value between 0.5 and 5.0."
        SaveBookRejection.FUTURE_DATE_FINISHED -> "Date finished cannot be in the future."
    }
}
