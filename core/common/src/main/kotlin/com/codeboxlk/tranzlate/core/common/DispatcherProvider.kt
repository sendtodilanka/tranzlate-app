package com.codeboxlk.tranzlate.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injectable dispatcher seam so coroutine context is swappable in tests
 * (TEST_A11Y_CONTRACT §0 determinism rule).
 *
 * NOTE: the Hilt binding for this interface intentionally does NOT live here —
 * default bindings are prod-side wiring in `:app/src/prod` (plan §6.2); the fake
 * variant binds its own in `FakeTranslateModule`.
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
}

/** Standard mapping onto [Dispatchers]. Bound prod-side (plan §6.2), never bound here. */
class DefaultDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
}
