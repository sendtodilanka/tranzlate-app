package com.codeboxlk.tranzlate.core.data.di

import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The application scope's backstop (issue #238).
 *
 * `SupervisorJob` stops one child's failure cancelling its siblings; it does NOT
 * swallow. Without a `CoroutineExceptionHandler` the failure is delivered to
 * `Thread.defaultUncaughtExceptionHandler` — process death, no dialog, nothing
 * the user can act on. Three files carried KDoc saying "appScope has no handler,
 * so guard your write", and issue #236 is what happened when writes got added by
 * someone who had not read them.
 *
 * These tests are written against the REAL provider, not a hand-rolled scope, so
 * that deleting the handler from `DataModule` is what turns them red. A test that
 * built its own `CoroutineScope(SupervisorJob() + handler)` would prove only that
 * coroutines work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationScopeHandlerTest {
    /**
     * The escape route the whole S0 rides. `UnsatisfiedLinkError` is the class
     * #236 is about — an `Error`, so past any `catch (Exception)` — and this is
     * the last thing between it and the process handler.
     */
    @Test
    fun `an unguarded failure on the application scope does not reach the thread handler`() =
        runTest {
            val scope = DataModule.applicationScope(TestDispatchers(StandardTestDispatcher(testScheduler)))
            val escaped = mutableListOf<Throwable>()
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, thrown -> escaped += thrown }
            try {
                scope.launch { throw UnsatisfiedLinkError("nativeExecute") }
                advanceUntilIdle()
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(previous)
            }

            assertThat(escaped).isEmpty()
        }

    /**
     * The `SupervisorJob` half, still true: one failed write must not retire the
     * scope for every later one. Asserted alongside the handler because a handler
     * bolted onto a plain `Job` would pass the test above and silently break this.
     */
    @Test
    fun `a failed child leaves the scope usable for the next write`() =
        runTest {
            val scope = DataModule.applicationScope(TestDispatchers(StandardTestDispatcher(testScheduler)))
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            var laterWriteRan = false
            try {
                scope.launch { throw UnsatisfiedLinkError("nativeExecute") }
                advanceUntilIdle()
                scope.launch { laterWriteRan = true }
                advanceUntilIdle()
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(previous)
            }

            assertThat(laterWriteRan).isTrue()
        }
}

/** Every role on the test scheduler — the scope is built from [DispatcherProvider.io]. */
private class TestDispatchers(
    private val dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val main: CoroutineDispatcher get() = dispatcher
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
}
