package com.librosleidos.ui

import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.librosleidos.MainActivity
import com.librosleidos.domain.model.Book
import com.librosleidos.domain.model.Rating
import com.librosleidos.ui.list.BookListScreen
import com.librosleidos.ui.list.BookListTags
import com.librosleidos.ui.list.BookListUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private val exposesTagsAsResourceId = SemanticsMatcher("testTagsAsResourceId == true") {
    it.config.getOrNull(SemanticsPropertiesAndroid.TestTagsAsResourceId) == true
}

/**
 * Debug builds expose test tags as Android resource ids (spec: "Debug
 * exposes tags as resource ids"). The activity root carries the flag once;
 * the delete dialog is a separate window and carries its own.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class E2eRootDebugTest {

    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_rootExposesTagsAsResourceId() {
        activityRule.waitUntil(timeoutMillis = 5_000) {
            activityRule
                .onAllNodesWithText("No books yet. Add one to get started.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val flagged = activityRule
            .onAllNodes(exposesTagsAsResourceId, useUnmergedTree = true)
            .fetchSemanticsNodes()

        assert(flagged.isNotEmpty()) { "expected a node with testTagsAsResourceId == true" }
    }

    @Test
    fun mainActivity_listTagsLiveUnderTheFlaggedRoot() {
        activityRule.waitUntil(timeoutMillis = 5_000) {
            activityRule
                .onAllNodesWithText("No books yet. Add one to get started.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val addButton = activityRule
            .onNodeWithTag(BookListTags.ADD_BUTTON, useUnmergedTree = true)
            .fetchSemanticsNode()

        val underFlaggedRoot = generateSequence(addButton) { it.parent }
            .any { exposesTagsAsResourceId.matches(it) }
        assert(underFlaggedRoot) { "expected the add button to sit under a flagged ancestor" }
    }
}

/**
 * The open delete dialog is its own window, so it needs the flag itself
 * (research: dialog windows do not inherit the activity root semantics).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class E2eRootDebugDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dune = Book(
        id = 1L,
        title = "Dune",
        author = "Frank Herbert",
        coverUrl = null,
        rating = Rating(4.5f),
        review = "",
        dateFinished = LocalDate.of(2026, 1, 15)
    )

    @Test
    fun deleteDialog_exposesTagsAsResourceId() {
        composeTestRule.setContent {
            BookListScreen(
                state = BookListUiState(
                    books = listOf(dune),
                    isLoading = false,
                    isEmpty = false,
                    pendingDeleteId = 1L
                ),
                onSortSelected = {},
                onMinRatingSelected = {},
                onDeleteRequested = {},
                onDeleteConfirmed = {},
                onDeleteCancelled = {}
            )
        }

        composeTestRule
            .onNodeWithTag(BookListTags.DELETE_DIALOG, useUnmergedTree = true)
            .assert(exposesTagsAsResourceId)
    }
}
