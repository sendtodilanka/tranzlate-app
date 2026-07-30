package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The PR-73 lens's OPEN-1, pinned: Stop during a download must cancel the
 * in-flight coroutine and the row must NEVER ghost back to Downloaded from
 * the stale writer.
 */
class RealOfflineModelManagerTest {
    private class FakeStore : ModelStore {
        val downloadGate = CompletableDeferred<Unit>()
        var committed = mutableSetOf<String>()
        var downloadCancelled = false

        override suspend fun downloadedTags(): Set<String> = committed.toSet()

        override suspend fun download(tag: String) {
            try {
                downloadGate.await() // park until the test decides
                committed += tag
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                downloadCancelled = true
                throw rethrown
            }
        }

        override suspend fun delete(tag: String) {
            committed -= tag
        }

        override fun isCapable(tag: String): Boolean = true

        override fun capableTags(): Set<String> = setOf("en", "fr")
    }

    @Test
    fun `stop mid-download cancels the manager's job and the row never ghosts back`() =
        runTest {
            val store = FakeStore()
            val manager = RealOfflineModelManager(store, backgroundScope)

            manager.download("fr") // launches internally, returns at once
            runCurrent()
            assertThat(manager.modelStates().first()["fr"]).isEqualTo(OfflineModelState.Downloading)

            manager.delete("fr") // the user's Stop
            runCurrent()

            assertThat(store.downloadCancelled).isTrue() // the internal job died at the gate
            assertThat(manager.modelStates().first()["fr"])
                .isEqualTo(OfflineModelState.NotDownloaded) // never Downloaded, never Failed
        }

    @Test
    fun `a caller's death never touches the download - the manager owns it`() =
        runTest {
            val store = FakeStore()
            val manager = RealOfflineModelManager(store, backgroundScope)

            // The "screen" launches and immediately dies (nav pop).
            val screenScope = launch { manager.download("fr") }
            runCurrent()
            screenScope.cancel()
            runCurrent()

            // Still truthfully Downloading — the owner's leave-and-return scenario.
            assertThat(manager.modelStates().first()["fr"]).isEqualTo(OfflineModelState.Downloading)

            store.downloadGate.complete(Unit)
            runCurrent()
            assertThat(manager.modelStates().first()["fr"]).isEqualTo(OfflineModelState.Downloaded)
        }

    @Test
    fun `a completed download publishes Downloaded through its own ownership`() =
        runTest {
            val store = FakeStore()
            val manager = RealOfflineModelManager(store, backgroundScope)

            manager.download("fr")
            runCurrent()
            store.downloadGate.complete(Unit)
            runCurrent()

            assertThat(manager.modelStates().first()["fr"]).isEqualTo(OfflineModelState.Downloaded)
        }

    @Test
    fun `a failed download keeps ownership and shows retry`() =
        runTest {
            val store =
                object : ModelStore by FakeStore() {
                    override suspend fun download(tag: String): Unit = throw java.io.IOException("dns")

                    override fun capableTags(): Set<String> = setOf("fr")
                }
            val manager = RealOfflineModelManager(store, backgroundScope)

            manager.download("fr")
            runCurrent()

            val state = manager.modelStates().first()["fr"]
            assertThat(state).isInstanceOf(OfflineModelState.Failed::class.java)
        }
}
