package com.librosleidos.ui

import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Task 3.3 (design D13). Installs a [FakeImageLoaderEngine] as Coil's
 * singleton [ImageLoader] for the duration of a test, so any
 * [coil3.compose.AsyncImage] under test resolves without touching the
 * network. [engine] defaults to returning a [ColorImage] for every request;
 * call [FakeImageLoaderEngine.newBuilder] on it (or build a fresh engine
 * and reassign) from a test to customize per-URL results. Reset in
 * [finished] so later tests are unaffected. Scaffold-only, no domain logic
 * — TDD-exempt (mirrors [MainDispatcherRule]).
 */
class CoilTestRule : TestWatcher() {

    val engine: FakeImageLoaderEngine = FakeImageLoaderEngine.Builder()
        .default(ColorImage())
        .build()

    @OptIn(DelicateCoilApi::class)
    override fun starting(description: Description) {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(engine) }
                .build()
        }
    }

    @OptIn(DelicateCoilApi::class)
    override fun finished(description: Description) {
        SingletonImageLoader.reset()
    }
}
