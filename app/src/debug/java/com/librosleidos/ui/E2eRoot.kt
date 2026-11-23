package com.librosleidos.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Debug builds only: exposes every `testTag` below this node as an Android
 * `resource-id`, which is how Appium (UiAutomator2) finds Compose elements.
 * Apply once per window root (the activity content and each dialog).
 * The release source set has the same function as a no-op.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.e2eRoot(): Modifier = semantics { testTagsAsResourceId = true }
