package com.librosleidos.ui.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.Rating
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Task 5.3 (design: container/presentational split — stateless
 * `*Screen(state, onEvent)` directly testable). Spec: "Books present",
 * "Empty library", "Delete confirmed", "Delete cancelled".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BookListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dune = Book(
        id = 1L,
        title = "Dune",
        author = "Frank Herbert",
        coverUrl = null,
        rating = Rating(4.5f),
        review = "Excellent.",
        dateFinished = LocalDate.of(2026, 1, 15),
    )

    @Test
    fun booksPresent_areRendered() {
        composeTestRule.setContent {
            BookListScreen(
                state = BookListUiState(books = listOf(dune), isLoading = false, isEmpty = false),
                onSortSelected = {},
                onMinRatingSelected = {},
                onDeleteRequested = {},
                onDeleteConfirmed = {},
                onDeleteCancelled = {},
            )
        }

        composeTestRule.onNodeWithText("Dune").assertExists()
        composeTestRule.onNodeWithText("Frank Herbert").assertExists()
        composeTestRule.onNodeWithText("Excellent.").assertExists()
    }

    @Test
    fun emptyLibrary_showsEmptyStateMessage() {
        composeTestRule.setContent {
            BookListScreen(
                state = BookListUiState(books = emptyList(), isLoading = false, isEmpty = true),
                onSortSelected = {},
                onMinRatingSelected = {},
                onDeleteRequested = {},
                onDeleteConfirmed = {},
                onDeleteCancelled = {},
            )
        }

        composeTestRule.onNodeWithText("No books yet. Add one to get started.").assertExists()
    }

    @Test
    fun pendingDelete_showsConfirmationDialogWithBookTitle() {
        composeTestRule.setContent {
            BookListScreen(
                state = BookListUiState(books = listOf(dune), isLoading = false, isEmpty = false, pendingDeleteId = 1L),
                onSortSelected = {},
                onMinRatingSelected = {},
                onDeleteRequested = {},
                onDeleteConfirmed = {},
                onDeleteCancelled = {},
            )
        }

        composeTestRule.onNodeWithText("Delete book?").assertExists()
    }

    @Test
    fun confirmingDeleteDialog_invokesOnDeleteConfirmed() {
        var confirmed = false
        composeTestRule.setContent {
            BookListScreen(
                state = BookListUiState(books = listOf(dune), isLoading = false, isEmpty = false, pendingDeleteId = 1L),
                onSortSelected = {},
                onMinRatingSelected = {},
                onDeleteRequested = {},
                onDeleteConfirmed = { confirmed = true },
                onDeleteCancelled = {},
            )
        }

        composeTestRule.onNodeWithText("Delete").performClick()

        assert(confirmed) { "expected onDeleteConfirmed to be invoked" }
    }

    @Test
    fun cancellingDeleteDialog_invokesOnDeleteCancelledAndLeavesBook() {
        var cancelled = false
        composeTestRule.setContent {
            BookListScreen(
                state = BookListUiState(books = listOf(dune), isLoading = false, isEmpty = false, pendingDeleteId = 1L),
                onSortSelected = {},
                onMinRatingSelected = {},
                onDeleteRequested = {},
                onDeleteConfirmed = {},
                onDeleteCancelled = { cancelled = true },
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()

        assert(cancelled) { "expected onDeleteCancelled to be invoked" }
    }

    @Test
    fun addBookButton_invokesOnAddRequested() {
        var addRequested = false
        composeTestRule.setContent {
            BookListScreen(
                state = BookListUiState(books = emptyList(), isLoading = false, isEmpty = true),
                onSortSelected = {},
                onMinRatingSelected = {},
                onDeleteRequested = {},
                onDeleteConfirmed = {},
                onDeleteCancelled = {},
                onAddRequested = { addRequested = true },
            )
        }

        composeTestRule.onNodeWithText("Add book").performClick()

        assert(addRequested) { "expected onAddRequested to be invoked" }
    }

    @Test
    fun clickingBookRow_invokesOnBookClickedWithId() {
        var clickedId: Long? = null
        composeTestRule.setContent {
            BookListScreen(
                state = BookListUiState(books = listOf(dune), isLoading = false, isEmpty = false),
                onSortSelected = {},
                onMinRatingSelected = {},
                onDeleteRequested = {},
                onDeleteConfirmed = {},
                onDeleteCancelled = {},
                onBookClicked = { clickedId = it },
            )
        }

        composeTestRule.onNodeWithText("Dune").performClick()

        assert(clickedId == 1L) { "expected onBookClicked to be invoked with id 1" }
    }

    @Test
    fun dateRangeFilters_areRendered_andClickingInvokesCallback() {
        var selectedRange: com.librosleidos.domain.model.DateRangeFilter? = null
        composeTestRule.setContent {
            BookListScreen(
                state = BookListUiState(
                    books = emptyList(),
                    isLoading = false,
                    isEmpty = true,
                    dateRange = com.librosleidos.domain.model.DateRangeFilter.ALL_TIME,
                ),
                onSortSelected = {},
                onMinRatingSelected = {},
                onDeleteRequested = {},
                onDeleteConfirmed = {},
                onDeleteCancelled = {},
                onDateRangeSelected = { selectedRange = it },
            )
        }

        composeTestRule.onNodeWithText("All Time").assertExists()
        composeTestRule.onNodeWithText("Last Month").assertExists()
        composeTestRule.onNodeWithText("Last Year").assertExists()

        composeTestRule.onNodeWithText("Last Month").performClick()
        assert(selectedRange == com.librosleidos.domain.model.DateRangeFilter.LAST_MONTH) {
            "expected onDateRangeSelected to be invoked with LAST_MONTH"
        }
    }
}
