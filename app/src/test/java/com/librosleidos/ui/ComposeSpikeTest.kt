package com.librosleidos.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Task 0 spike: proves Compose UI tests run under Robolectric on this exact
 * compose-bom / Robolectric / Gradle / AGP version combination (design R1).
 *
 * Not a feature test — this is deliberately the smallest possible
 * `createComposeRule` assertion. It exists only to catch a version mismatch
 * before Phase 5/6 UI work depends on the same harness.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComposeSpikeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun textNode_rendersWithExpectedContent() {
        composeTestRule.setContent {
            Text("Rincón de Lectura")
        }

        composeTestRule.onNodeWithText("Rincón de Lectura").assertExists()
    }
}
