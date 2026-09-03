package com.librosleidos.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.librosleidos.ui.CoilTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Task 3.4 (design D11, D13). Spec: "Missing or failed cover image" (list),
 * "Cover shown while editing", "Missing cover in edit form" (edit form) —
 * plus the MVP's carried-forward https-only threat-matrix rule
 * ([com.librosleidos.data.remote.GoogleBooksMetadataSource]'s
 * `toSecureCoverUrl`).
 *
 * [CoilTestRule] installs a fake engine that resolves every request
 * successfully, so a distinguishing content description proves which
 * branch [BookCoverImage] actually took: the placeholder short-circuits
 * before Coil is ever invoked (D11), so its fixed description appears and
 * the real [contentDescription] parameter never reaches the semantics
 * tree; a valid https URL reaches [coil3.compose.AsyncImage], which is the
 * only path that can produce the real description.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BookCoverImageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val coilTestRule = CoilTestRule()

    private val realCoverDescription = "Dune cover"
    private val placeholderDescription = "No cover available"

    @Test
    fun nullCoverUrl_showsPlaceholderNotRealImage() {
        composeTestRule.setContent {
            BookCoverImage(coverUrl = null, contentDescription = realCoverDescription)
        }

        composeTestRule.onNodeWithContentDescription(placeholderDescription).assertExists()
        composeTestRule.onNodeWithContentDescription(realCoverDescription).assertDoesNotExist()
    }

    @Test
    fun nonHttpsCoverUrl_showsPlaceholderNotRealImage() {
        composeTestRule.setContent {
            BookCoverImage(
                coverUrl = "http://example.com/cover.jpg",
                contentDescription = realCoverDescription,
            )
        }

        composeTestRule.onNodeWithContentDescription(placeholderDescription).assertExists()
        composeTestRule.onNodeWithContentDescription(realCoverDescription).assertDoesNotExist()
    }

    @Test
    fun validHttpsCoverUrl_loadsRealImageNotPlaceholder() {
        composeTestRule.setContent {
            BookCoverImage(
                coverUrl = "https://example.com/cover.jpg",
                contentDescription = realCoverDescription,
            )
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(realCoverDescription).assertExists()
        composeTestRule.onNodeWithContentDescription(placeholderDescription).assertDoesNotExist()
    }
}
