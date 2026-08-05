package com.codeboxlk.tranzlate.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.feature.language.PackSnackbar
import com.codeboxlk.tranzlate.feature.language.PackSnackbarKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The consuming half of the U-1 STARTED-only contract (#130 PR-22): the shell handles
 * a 20a notice ONLY while its lifecycle is at least STARTED, and a notice emitted while
 * stopped is neither handled then nor replayed on return.
 *
 * Drives a real [TestLifecycleOwner] through CREATED → STARTED → CREATED → STARTED and
 * feeds a `replay = 0` flow shaped exactly like the manager's channel, so the two halves
 * of the contract meet: `repeatOnLifecycle` tears the collection down on STOP, and
 * `replay = 0` gives the re-subscription nothing.
 *
 * Mutation decided first (rule 11): make `collectPackSnackbars` a plain
 * `snackbars.collect { handle(it) }` with no `repeatOnLifecycle`. The collector then
 * subscribes at CREATED and never stops, so BOTH the "not handled below STARTED" and the
 * "dropped while stopped" assertions redden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadSnackbarCollectionTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val dispatcherRule = TestDispatcherRule(dispatcher)

    @Test
    fun `notices are handled only while STARTED and never replayed on return`() =
        runTest(dispatcher) {
            val owner = TestLifecycleOwner(Lifecycle.State.CREATED, dispatcher)
            val channel =
                MutableSharedFlow<PackSnackbar>(
                    replay = 0,
                    extraBufferCapacity = 16,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            val handled = mutableListOf<PackSnackbar>()
            backgroundScope.launch { collectPackSnackbars(owner.lifecycle, channel) { handled += it } }

            // CREATED (below STARTED): a notice is not handled.
            channel.emit(PackSnackbar(PackSnackbarKind.STARTED, "a"))
            assertThat(handled).isEmpty()

            // STARTED: a notice is handled.
            owner.setCurrentState(Lifecycle.State.STARTED)
            channel.emit(PackSnackbar(PackSnackbarKind.READY, "b"))
            assertThat(handled).containsExactly(PackSnackbar(PackSnackbarKind.READY, "b"))

            // STOPPED (back to CREATED): a notice emitted now is dropped...
            owner.setCurrentState(Lifecycle.State.CREATED)
            channel.emit(PackSnackbar(PackSnackbarKind.REMOVED, "c"))
            // ...and returning to STARTED replays nothing (replay = 0) — "b" only.
            owner.setCurrentState(Lifecycle.State.STARTED)
            assertThat(handled).containsExactly(PackSnackbar(PackSnackbarKind.READY, "b"))
        }
}
