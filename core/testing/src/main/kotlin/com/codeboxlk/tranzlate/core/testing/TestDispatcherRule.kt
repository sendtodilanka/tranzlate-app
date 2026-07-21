package com.codeboxlk.tranzlate.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sets `Dispatchers.Main` to an [UnconfinedTestDispatcher] for the test's duration
 * (TEST_A11Y_CONTRACT §1.8 — `@get:Rule val dispatcher = TestDispatcherRule()`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
