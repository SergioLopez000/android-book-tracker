package com.librosleidos.ui

import androidx.compose.ui.Modifier
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Release builds must not expose test tags as resource ids (spec: "Release
 * behavior unchanged"): the release `e2eRoot` is the identity modifier, so it
 * adds nothing to the semantics tree. Plain JUnit; no Robolectric needed.
 */
class E2eRootReleaseTest {

    @Test
    fun e2eRoot_isTheIdentityModifier() {
        assertSame(Modifier, Modifier.e2eRoot())
    }

    @Test
    fun e2eRoot_leavesAChainedModifierUnchanged() {
        val chained = Modifier.then(Modifier)

        assertSame(chained, chained.e2eRoot())
    }
}
