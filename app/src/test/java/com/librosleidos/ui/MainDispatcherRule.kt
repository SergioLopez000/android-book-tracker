package com.librosleidos.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Test infrastructure for Phase 5/6 ViewModel tests: `viewModelScope` uses
 * `Dispatchers.Main.immediate`, which does not exist on plain JVM tests
 * unless swapped via `Dispatchers.setMain` (design: ViewModel tests use
 * coroutines-test + Turbine + fakes, plain JVM). Scaffold-only, no domain
 * logic — TDD-exempt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
