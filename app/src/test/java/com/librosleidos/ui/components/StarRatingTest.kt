package com.librosleidos.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.librosleidos.domain.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Task 2.1/2.3 (design D10). Spec: "star-rating" capability — "Whole-star
 * rating", "Half-star rating" (display), "User taps to set half-star
 * value", "Input stays within valid bounds" (input).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StarRatingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- Display: pure fill-fraction logic (design D10 fraction-clip) ---

    @Test
    fun starFillFraction_wholeStarRating_fourFilledOneEmpty() {
        assertEquals(1f, starFillFraction(4.0f, 0))
        assertEquals(1f, starFillFraction(4.0f, 1))
        assertEquals(1f, starFillFraction(4.0f, 2))
        assertEquals(1f, starFillFraction(4.0f, 3))
        assertEquals(0f, starFillFraction(4.0f, 4))
    }

    @Test
    fun starFillFraction_halfStarRating_threeFilledOneHalfOneEmpty() {
        assertEquals(1f, starFillFraction(3.5f, 0))
        assertEquals(1f, starFillFraction(3.5f, 1))
        assertEquals(1f, starFillFraction(3.5f, 2))
        assertEquals(0.5f, starFillFraction(3.5f, 3))
        assertEquals(0f, starFillFraction(3.5f, 4))
    }

    // --- Display: no numeric text, formatted content description ---

    @Test
    fun display_wholeStarRating_showsNoNumericTextAndFormattedDescription() {
        composeTestRule.setContent {
            StarRating(rating = 4.0f)
        }

        composeTestRule.onNodeWithText("4.0").assertDoesNotExist()
        composeTestRule.onNodeWithText("4").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Rating 4.0 out of 5").assertExists()
    }

    @Test
    fun display_halfStarRating_showsNoNumericTextAndFormattedDescription() {
        composeTestRule.setContent {
            StarRating(rating = 3.5f)
        }

        composeTestRule.onNodeWithText("3.5").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Rating 3.5 out of 5").assertExists()
    }

    // --- Input: tap sets 0.5-increment value ---

    @Test
    fun input_tapHalfStarPosition_setsHalfIncrementValue() {
        var captured: Float? = null
        composeTestRule.setContent {
            StarRating(rating = null, onRatingChange = { captured = it })
        }

        composeTestRule.onNodeWithContentDescription("Set rating 3.5").performClick()

        assertEquals(3.5f, captured)
    }

    @Test
    fun input_tapWholeStarPosition_setsWholeIncrementValue() {
        var captured: Float? = null
        composeTestRule.setContent {
            StarRating(rating = null, onRatingChange = { captured = it })
        }

        composeTestRule.onNodeWithContentDescription("Set rating 2.0").performClick()

        assertEquals(2.0f, captured)
    }

    // --- Input: result always 0.5-5.0 ---

    @Test
    fun input_everyTapTarget_resultAlwaysWithinValidBounds() {
        val captured = mutableListOf<Float>()
        composeTestRule.setContent {
            StarRating(rating = null, onRatingChange = { captured.add(it) })
        }

        listOf("0.5", "1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0", "4.5", "5.0").forEach { description ->
            composeTestRule.onNodeWithContentDescription("Set rating $description").performClick()
        }

        assertEquals(10, captured.size)
        assertTrue(captured.all { it in Rating.MIN..Rating.MAX })
    }
}
