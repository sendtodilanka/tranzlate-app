package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeStorageProbe
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
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
    fun `low disk refuses BEFORE enqueue - Failed(STORAGE), store never touched`() =
        runTest {
            val store = FakeStore()
            val probe = FakeStorageProbe(free = 10L * 1024 * 1024) // 10MB < the 150MB budget
            val manager = RealOfflineModelManager(store, probe, online, backgroundScope)

            manager.download("fr")
            runCurrent()

            assertThat(manager.stateOf("fr"))
                .isEqualTo(OfflineModelState.Failed(OfflineModelFailure.STORAGE))
            assertThat(store.committed).isEmpty() // no partial download ever started
        }

    @Test
    fun `freed disk lets the SAME row retry after Failed(STORAGE)`() =
        runTest {
            val store = FakeStore()
            val probe = FakeStorageProbe(free = 0L)
            val manager = RealOfflineModelManager(store, probe, online, backgroundScope)

            manager.download("fr")
            runCurrent()
            assertThat(manager.stateOf("fr"))
                .isEqualTo(OfflineModelState.Failed(OfflineModelFailure.STORAGE))

            probe.free = Long.MAX_VALUE // user freed space; retry must not be a dead end
            manager.download("fr")
            runCurrent()
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)
        }

    // ---- issue #238: the pre-flight probe had no error handling -------------

    /**
     * The prod probe is `StatFs(context.noBackupFilesDir.absolutePath)`, and
     * `StatFs` throws `IllegalArgumentException` when the underlying `statvfs`
     * fails — `android/os/StatFs.java:53`. This call runs on the CALLER's
     * coroutine, outside the try/catch that wraps the launched transfer, so the
     * throw went straight to `Thread.defaultUncaughtExceptionHandler` on a
     * download tap.
     *
     * A probe that cannot ANSWER is not a refusal. Refusing on an unknown would
     * strand a user whose disk is perfectly fine, and `StorageProbe`'s own
     * contract already says a missing answer means "unknown", never "zero" —
     * ML Kit reports a genuine out-of-space through the normal failure path.
     */
    @Test
    fun `an unanswerable free-space probe lets the download proceed`() =
        runTest {
            val store = FakeStore()
            val probe = FakeStorageProbe(free = Long.MAX_VALUE)
            probe.freeFailure = IllegalArgumentException("Invalid path: /data/user/0/…/no_backup")
            val manager = RealOfflineModelManager(store, probe, online, backgroundScope)

            manager.download("fr")
            runCurrent()

            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)
        }

    /** The same, for the class a narrow catch would have let past (issue #236's shape). */
    @Test
    fun `a free-space probe that fails as an Error lets the download proceed`() =
        runTest {
            val store = FakeStore()
            val probe = FakeStorageProbe(free = Long.MAX_VALUE)
            probe.freeFailure = UnsatisfiedLinkError("nativeStatvfs")
            val manager = RealOfflineModelManager(store, probe, online, backgroundScope)

            manager.download("fr")
            runCurrent()

            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)
        }

    /**
     * A REAL low-disk answer must still refuse. Without this, "proceed when the
     * probe throws" could be implemented by never consulting the probe at all and
     * every test above would still pass.
     */
    @Test
    fun `the proceed-on-unknown rule does not disarm a real low-disk refusal`() =
        runTest {
            val store = FakeStore()
            val probe = FakeStorageProbe(free = 10L * 1024 * 1024) // 10MB < the 150MB budget
            val manager = RealOfflineModelManager(store, probe, online, backgroundScope)

            manager.download("fr")
            runCurrent()

            assertThat(manager.stateOf("fr"))
                .isEqualTo(OfflineModelState.Failed(OfflineModelFailure.STORAGE))
            assertThat(store.committed).isEmpty()
        }

    @Test
    fun `stop mid-download cancels the manager's job and the row never ghosts back`() =
        runTest {
            val store = FakeStore()
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)

            manager.download("fr") // launches internally, returns at once
            runCurrent()
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)

            manager.delete("fr") // the user's Stop
            runCurrent()

            assertThat(store.downloadCancelled).isTrue() // the internal job died at the gate
            assertThat(manager.stateOf("fr"))
                .isEqualTo(OfflineModelState.NotDownloaded) // never Downloaded, never Failed
        }

    @Test
    fun `a caller's death never touches the download - the manager owns it`() =
        runTest {
            val store = FakeStore()
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)

            // The "screen" launches and immediately dies (nav pop).
            val screenScope = launch { manager.download("fr") }
            runCurrent()
            screenScope.cancel()
            runCurrent()

            // Still truthfully Downloading — the owner's leave-and-return scenario.
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)

            store.downloadGate.complete(Unit)
            runCurrent()
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloaded)
        }

    @Test
    fun `a caller's death mid-delete never strands the Deleting spinner`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val store =
                object : ModelStore by FakeStore() {
                    override suspend fun downloadedTags(): Set<String> = setOf("fr")

                    override suspend fun delete(tag: String) {
                        gate.await() // park the delete so the caller can die mid-flight
                    }

                    override fun capableTags(): Set<String> = setOf("fr")
                }
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)

            val screen = launch { manager.delete("fr") }
            runCurrent()
            screen.cancel() // nav-away mid-delete (PR-83 lens OPEN-1)
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            // Never a dead-end spinner: the manager-scoped finally cleared it.
            assertThat(manager.stateOf("fr"))
                .isNotEqualTo(OfflineModelState.Deleting)
        }

    /**
     * Issue #123 item 3 (risk R1): the picker shows a ⬇ on a Deleting row, so a
     * user CAN start a new download while the old delete is still in flight.
     * The delete's finally used to clear the transient UNCONDITIONALLY, wiping
     * the new download's Downloading and letting a second tap queue a second
     * download. Ownership rule: only the job that set a transient may clear it.
     */
    @Test
    fun `delete in flight, then redownload - the row STAYS Downloading when the delete lands`() =
        runTest {
            val base = FakeStore()
            base.committed = mutableSetOf("fr") // model already on disk
            val deleteGate = CompletableDeferred<Unit>()
            val store =
                object : ModelStore by base {
                    override suspend fun delete(tag: String) {
                        deleteGate.await() // park the delete so a download can race it
                        base.delete(tag)
                    }
                }
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)

            // The user deletes the model; the platform delete is slow.
            val screen = launch { manager.delete("fr") }
            runCurrent()
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Deleting)

            // Mid-delete, the user re-downloads from the picker row.
            manager.download("fr")
            runCurrent()
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)

            // The stale delete lands — it no longer owns the row's transient,
            // so it must NOT clear the new download's Downloading.
            deleteGate.complete(Unit)
            runCurrent()
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)

            // And the download it raced still completes truthfully.
            base.downloadGate.complete(Unit)
            runCurrent()
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloaded)
            screen.join()
        }

    @Test
    fun `a second download tap while one is in flight is a no-op`() =
        runTest {
            val store = FakeStore()
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)

            manager.download("fr")
            runCurrent()
            manager.download("fr") // double tap — the guard's no-suspension window
            runCurrent()
            store.downloadGate.complete(Unit)
            runCurrent()

            assertThat(store.committed).containsExactly("fr") // one store call chain
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloaded)
        }

    @Test
    fun `a completed download publishes Downloaded through its own ownership`() =
        runTest {
            val store = FakeStore()
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)

            manager.download("fr")
            runCurrent()
            store.downloadGate.complete(Unit)
            runCurrent()

            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloaded)
        }

    @Test
    fun `a failed download keeps ownership and shows retry`() =
        runTest {
            val store =
                object : ModelStore by FakeStore() {
                    override suspend fun download(tag: String): Unit = throw java.io.IOException("dns")

                    override fun capableTags(): Set<String> = setOf("fr")
                }
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)

            manager.download("fr")
            runCurrent()

            val state = manager.stateOf("fr")
            assertThat(state).isInstanceOf(OfflineModelState.Failed::class.java)
        }
}

private val plentyFree = FakeStorageProbe(free = Long.MAX_VALUE)

/** The default is online: every pre-existing case here predates the #218 gate. */
private val online = FakeConnectivityMonitor()

/**
 * The manager's states are HOT since issue #130 rev.3 (U-13): a subscriber is
 * handed the shared flow's current value, and that is the `emptyMap()` seed
 * until the first merge lands — so a bare `first()` now reads the seed rather
 * than the state under test.
 *
 * Waiting for a populated map waits for the merge without pinning HOW MANY
 * emissions precede it, which is the only part of the sequence these ownership
 * tests were ever about. Every assertion below is the one it made before the
 * flow changed temperature; only the read is new.
 */
private suspend fun OfflineModelManager.stateOf(tag: String): OfflineModelState? =
    modelStates().first { it.isNotEmpty() }[tag]
