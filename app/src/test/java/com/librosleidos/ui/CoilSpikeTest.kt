package com.librosleidos.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.compose.AsyncImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Task 3.3 spike (design D13): proves [CoilTestRule]'s [coil3.test.FakeImageLoaderEngine]
 * actually resolves a [coil3.compose.AsyncImage] under Robolectric on this
 * exact Coil3 / Compose-BOM / Kotlin toolchain combination — the last
 * unproven risk called out by the design.
 *
 * Not a feature test — deliberately the smallest possible assertion that
 * the fake engine's success result reaches `onSuccess`. [BookCoverImageTest]
 * (task 3.4) exercises the real production behavior; this only proves the
 * test harness itself works before that file is written.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CoilSpikeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val coilTestRule = CoilTestRule()

    @Test
    fun asyncImage_withFakeEngine_reachesOnSuccess() {
        var succeeded = false

        composeTestRule.setContent {
            AsyncImage(
                model = "https://example.com/fake-cover.jpg",
                contentDescription = null,
                onSuccess = { succeeded = true },
            )
        }

        composeTestRule.waitForIdle()

        assertTrue("expected AsyncImage to reach onSuccess via the fake engine", succeeded)
    }
}
