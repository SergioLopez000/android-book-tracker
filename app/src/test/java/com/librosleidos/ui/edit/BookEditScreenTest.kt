package com.librosleidos.ui.edit

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.librosleidos.domain.model.BookMetadata
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Task 6.3 (design: container/presentational split — stateless
 * `*Screen(state, onEvent)` directly testable). Spec: "Add Book Manually",
 * "Require Rating on Save", "Edit Existing Book", "Selection prefills form".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BookEditScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val noOpState = BookEditUiState()

    @Test
    fun manualEntry_fillingFieldsAndSaving_invokesOnSaveRequestedWithoutLookup() {
        var savedInvoked = false
        var searchRequested = false
        composeTestRule.setContent {
            BookEditScreen(
                state = noOpState,
                onTitleChanged = {},
                onAuthorChanged = {},
                onRatingChanged = {},
                onReviewChanged = {},
                onSearchQueryChanged = {},
                onSearchRequested = { searchRequested = true },
                onResultSelected = {},
                onSaveRequested = { savedInvoked = true },
                onCancelRequested = {},
            )
        }

        composeTestRule.onNodeWithText("Title").performTextInput("Dune")
        composeTestRule.onNodeWithText("4.5").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Save").performScrollTo().performClick()

        assert(savedInvoked) { "expected onSaveRequested to be invoked" }
        assert(!searchRequested) { "manual entry must not trigger a lookup" }
    }

    @Test
    fun blockedSave_showsFieldErrorMessage() {
        composeTestRule.setContent {
            BookEditScreen(
                state = noOpState.copy(fieldErrors = mapOf(BookEditField.RATING to "Rating is required.")),
                onTitleChanged = {},
                onAuthorChanged = {},
                onRatingChanged = {},
                onReviewChanged = {},
                onSearchQueryChanged = {},
                onSearchRequested = {},
                onResultSelected = {},
                onSaveRequested = {},
                onCancelRequested = {},
            )
        }

        composeTestRule.onNodeWithText("Rating is required.").assertExists()
    }

    @Test
    fun editPrefill_rendersExistingBookValues() {
        composeTestRule.setContent {
            BookEditScreen(
                state = noOpState.copy(
                    id = 7L,
                    title = "Neuromancer",
                    author = "William Gibson",
                    review = "Classic cyberpunk.",
                ),
                onTitleChanged = {},
                onAuthorChanged = {},
                onRatingChanged = {},
                onReviewChanged = {},
                onSearchQueryChanged = {},
                onSearchRequested = {},
                onResultSelected = {},
                onSaveRequested = {},
                onCancelRequested = {},
            )
        }

        composeTestRule.onNodeWithText("Neuromancer").assertExists()
        composeTestRule.onNodeWithText("William Gibson").assertExists()
        composeTestRule.onNodeWithText("Classic cyberpunk.").assertExists()
    }

    @Test
    fun searchResults_areRenderedAndSelectingInvokesOnResultSelected() {
        val dune = BookMetadata(title = "Dune", author = "Frank Herbert", coverUrl = null)
        var selected: BookMetadata? = null
        composeTestRule.setContent {
            BookEditScreen(
                state = noOpState.copy(search = SearchState.Results(listOf(dune))),
                onTitleChanged = {},
                onAuthorChanged = {},
                onRatingChanged = {},
                onReviewChanged = {},
                onSearchQueryChanged = {},
                onSearchRequested = {},
                onResultSelected = { selected = it },
                onSaveRequested = {},
                onCancelRequested = {},
            )
        }

        composeTestRule.onNodeWithText("Dune — Frank Herbert").performClick()

        assert(selected == dune) { "expected onResultSelected to be invoked with the picked result" }
    }

    @Test
    fun cancelButton_invokesOnCancelRequested() {
        var cancelled = false
        composeTestRule.setContent {
            BookEditScreen(
                state = noOpState,
                onTitleChanged = {},
                onAuthorChanged = {},
                onRatingChanged = {},
                onReviewChanged = {},
                onSearchQueryChanged = {},
                onSearchRequested = {},
                onResultSelected = {},
                onSaveRequested = {},
                onCancelRequested = { cancelled = true },
            )
        }

        composeTestRule.onNodeWithText("Cancel").performScrollTo().performClick()

        assert(cancelled) { "expected onCancelRequested to be invoked" }
    }
}
