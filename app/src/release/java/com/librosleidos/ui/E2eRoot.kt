package com.librosleidos.ui

import androidx.compose.ui.Modifier

/**
 * Release builds: identity. Test tags are never exposed as resource ids, and
 * the release bytecode never links the Compose property that does it.
 * The debug source set has the real implementation.
 */
fun Modifier.e2eRoot(): Modifier = this
