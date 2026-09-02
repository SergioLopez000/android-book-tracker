package com.librosleidos

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Task 7.3 (design: "Wiring & Integration") — runtime-harness smoke test
 * proving the full dependency graph assembles without crashing. Launching
 * [MainActivity] exercises [com.librosleidos.di.AppContainer] construction
 * end to end (real Room [com.librosleidos.data.local.BooksDatabase],
 * [com.librosleidos.data.remote.HttpUrlConnectionClient],
 * [com.librosleidos.data.remote.ChainedMetadataSource], all 5 use cases,
 * both `ViewModelProvider.Factory`s) — if `AppContainer` threw on
 * construction, `onCreate` would crash and this test would fail immediately
 * rather than time out. It then renders the list screen's empty-state
 * message on a fresh, unseeded database (task 7.3: "app launches to list
 * screen, empty-state shown on fresh DB").
 *
 * [androidx.compose.ui.test.junit4.createAndroidComposeRule] launches
 * [MainActivity] via a real Robolectric activity lifecycle (same harness
 * proven since Task 0's spike). [waitUntil] gives the Room query's
 * background-executor round trip time to complete before asserting, since
 * Compose's own idling only synchronizes recomposition/the main looper, not
 * arbitrary background threads.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MainActivitySmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_assemblesDependencyGraphAndShowsEmptyState() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("No books yet. Add one to get started.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText("No books yet. Add one to get started.").assertExists()
    }
}
