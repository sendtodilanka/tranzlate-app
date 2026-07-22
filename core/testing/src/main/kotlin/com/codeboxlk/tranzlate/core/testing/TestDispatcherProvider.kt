package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher

/**
 * Every lane on ONE [TestDispatcher] so `runTest`/`advanceUntilIdle` control all
 * coroutines a ViewModel launches (TEST_A11Y_CONTRACT §0 determinism rule —
 * production code takes [DispatcherProvider]; tests pass this).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    private val dispatcher: TestDispatcher,
) : DispatcherProvider {
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val main: CoroutineDispatcher get() = dispatcher
    override val unconfined: CoroutineDispatcher get() = dispatcher
}
