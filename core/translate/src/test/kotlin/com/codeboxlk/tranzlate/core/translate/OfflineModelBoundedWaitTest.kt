package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeStorageProbe
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * **No wait in this manager may be unbounded, and no state it lands on may be
 * one the user cannot leave** — issues #218 and #237.
 *
 * The two issues are one defect in two places. Every call into [ModelStore] is a
 * Play Services `Task.await()`, and an unresolved `Task` is not a slow path: it
 * is a coroutine parked forever, which makes the `catch` blocks written below it
 * unreachable. #218 measured the consequence on a genuinely offline device — the
 * row read `Downloading…` at every sample over 802 s, because ML Kit's transfer
 * is a system `DownloadManager` job gated on `CONNECTIVITY`, and that gate is
 * event-driven rather than timed.
 *
 * Every case here was written down with the source edit that must turn it red
 * BEFORE it was written (CLAUDE.md rule 11, third cause); the mutation is named
 * on each test rather than kept in a document that can drift away from it.
 *
 * Virtual time throughout, against the PRODUCTION constants rather than copies —
 * so raising a constant without raising the wait breaks these, which is the
 * point of `internal` on both of them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModelBoundedWaitTest {
    /** A store whose every call parks until the test says otherwise. */
    private class ParkedStore(
        private val onDisk: MutableSet<String> = mutableSetOf(),
    ) : ModelStore {
        val downloadEntered = CompletableDeferred<Unit>()
        val deleteEntered = CompletableDeferred<Unit>()
        var downloadCalls = 0
        var parkTheDiskRead = false

        private val forever = CompletableDeferred<Unit>()

        override suspend fun downloadedTags(): Set<String> {
            if (parkTheDiskRead) forever.await()
            return onDisk.toSet()
        }

        override suspend fun download(tag: String) {
            downloadCalls++
            downloadEntered.complete(Unit)
            forever.await() // the CONNECTIVITY-gated job that never runs
        }

        override suspend fun delete(tag: String) {
            deleteEntered.complete(Unit)
            forever.await()
        }

        override fun isCapable(tag: String): Boolean = true

        override fun capableTags(): Set<String> = setOf("fr")
    }

    private fun manager(
        store: ModelStore,
        connectivity: FakeConnectivityMonitor,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = RealOfflineModelManager(store, FakeStorageProbe(free = Long.MAX_VALUE), connectivity, scope)

    // ---- #218: the pre-flight, which is the half that never waits at all ----

    /**
     * The harm itself. ML Kit hands the transfer to the system `DownloadManager`,
     * which parks it on an unsatisfied `CONNECTIVITY` constraint forever — so the
     * only way to not hang is to never enqueue.
     *
     * Mutation: move the `!connectivity.isOnline()` guard below
     * `store.download(languageTag)` inside the job.
     */
    @Test
    fun `offline, the store is never asked to download at all`() =
        runTest {
            val store = ParkedStore()
            val offline = FakeConnectivityMonitor(initiallyOnline = false)
            val manager = manager(store, offline, backgroundScope)

            manager.download("fr")
            advanceTimeBy(MODEL_TRANSFER_TIMEOUT_MILLIS * 2)
            runCurrent()

            assertThat(store.downloadCalls).isEqualTo(0)
            assertThat(store.downloadEntered.isCompleted).isFalse()
        }

    /**
     * What makes sheet 19d reachable. The picker raises it by watching the shared
     * map for a CHANGE (`LanguagePickerViewModel.reportFailure`), so a refusal
     * that writes no state is invisible to it — the row would simply stay
     * `NotDownloaded` and the tap would look ignored.
     *
     * Mutation: make the guard `return` without calling `takeTransient`.
     */
    @Test
    fun `offline lands the row on Failed(NETWORK) - the cause that raises 19d`() =
        runTest {
            val store = ParkedStore()
            val offline = FakeConnectivityMonitor(initiallyOnline = false)
            val manager = manager(store, offline, backgroundScope)

            manager.download("fr")
            runCurrent()

            assertThat(manager.stateOf("fr"))
                .isEqualTo(OfflineModelState.Failed(OfflineModelFailure.NETWORK))
        }

    /**
     * The gate must be the radio and nothing else: a connected device still
     * downloads, and the pre-flight is invisible to it.
     *
     * Mutation: invert the guard to `if (connectivity.isOnline())`.
     */
    @Test
    fun `online is untouched by the pre-flight - the store is asked`() =
        runTest {
            val store = ParkedStore()
            val manager = manager(store, FakeConnectivityMonitor(initiallyOnline = true), backgroundScope)

            manager.download("fr")
            runCurrent()

            assertThat(store.downloadCalls).isEqualTo(1)
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)
        }

    /**
     * The radio coming back must not leave the row stuck on its refusal — the
     * no-dead-end rule's "Retry" arm, exercised rather than assumed.
     *
     * Mutation: make the refusal `setTransient` a terminal state the retry path
     * cannot overwrite (e.g. return early when the row is already `Failed`).
     */
    @Test
    fun `the radio returning lets the SAME row retry after Failed(NETWORK)`() =
        runTest {
            val store = ParkedStore()
            val connectivity = FakeConnectivityMonitor(initiallyOnline = false)
            val manager = manager(store, connectivity, backgroundScope)

            manager.download("fr")
            runCurrent()
            assertThat(manager.stateOf("fr"))
                .isEqualTo(OfflineModelState.Failed(OfflineModelFailure.NETWORK))

            connectivity.state.value = true // user left airplane mode
            manager.download("fr")
            runCurrent()

            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)
            assertThat(store.downloadCalls).isEqualTo(1)
        }

    // ---- #218: the backstop, for a connection that exists and then stalls ----

    /**
     * The case the `CONNECTIVITY` constraint cannot catch, and the reason a
     * timeout is still needed once the pre-flight exists: the device IS online,
     * the transfer starts, and the `Task` never settles.
     *
     * This is also the test that pins **the trap this change could most easily
     * have walked into**. `TimeoutCancellationException` is a
     * `CancellationException`, and `download()`'s first catch rethrows those
     * because that catch means "the user pressed Stop". A bare `withTimeout`
     * would therefore be read as a Stop and the row would stay `Downloading`
     * forever — the bound would look right in review and change nothing.
     *
     * Mutations, either of which must turn this red: remove the `bounded(...)`
     * wrapper around `store.download`; or let `TimeoutCancellationException`
     * escape `bounded` instead of becoming an `IOException`.
     */
    @Test
    fun `a download Task that never settles ends as Failed(NETWORK), not a stuck spinner`() =
        runTest {
            val store = ParkedStore()
            val manager = manager(store, FakeConnectivityMonitor(initiallyOnline = true), backgroundScope)

            manager.download("fr")
            runCurrent()
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)

            advanceTimeBy(MODEL_TRANSFER_TIMEOUT_MILLIS + 1)
            runCurrent()

            assertThat(manager.stateOf("fr"))
                .isEqualTo(OfflineModelState.Failed(OfflineModelFailure.NETWORK))
        }

    /**
     * The budget is a real budget: a transfer still running one millisecond
     * before it expires is left alone. Without this, a timeout of zero would pass
     * the test above.
     *
     * Mutation: lower `MODEL_TRANSFER_TIMEOUT_MILLIS`.
     */
    @Test
    fun `a download still running inside its budget is not cut off`() =
        runTest {
            val store = ParkedStore()
            val manager = manager(store, FakeConnectivityMonitor(initiallyOnline = true), backgroundScope)

            manager.download("fr")
            runCurrent()
            advanceTimeBy(MODEL_TRANSFER_TIMEOUT_MILLIS - 1)
            runCurrent()

            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Downloading)
        }

    // ---- #237: Deleting must end somewhere the user can tap ----

    /**
     * #237's exact shape. `delete()` sets `Deleting`, launches, and `join()`s;
     * the transient clears only in the job's `finally`, which cannot run while an
     * unresolved `Task` is being awaited. `transient` lives in a `@Singleton`, so
     * leaving Settings and coming back shows the same spinner — and the screen
     * draws `Deleting` as a bare `CircularProgressIndicator` with nothing
     * tappable on it.
     *
     * Mutation: remove the `bounded(...)` wrapper around `store.delete`.
     */
    @Test
    fun `a delete Task that never settles still leaves Deleting`() =
        runTest {
            val store = ParkedStore(onDisk = mutableSetOf("fr"))
            val manager = manager(store, FakeConnectivityMonitor(initiallyOnline = true), backgroundScope)

            val caller = launch { manager.delete("fr") }
            runCurrent()
            assertThat(manager.stateOf("fr")).isEqualTo(OfflineModelState.Deleting)

            advanceTimeBy(MODEL_LOCAL_TIMEOUT_MILLIS + 1)
            runCurrent()

            assertThat(manager.stateOf("fr")).isNotEqualTo(OfflineModelState.Deleting)
            caller.join()
        }

    /**
     * Where it lands, not merely that it moves. `Failed` was rejected on purpose:
     * a failed row's only control is a Retry wired to `onDownload`, so a failed
     * DELETE would offer the user a DOWNLOAD. The truthful refresh gives the row
     * back its own control — 🗑 on `Downloaded`, ⬇ on `NotDownloaded`.
     *
     * Mutation: make the `finally`'s `clearTransient` conditional on the delete
     * having succeeded.
     */
    @Test
    fun `after a timed-out delete the row is a state with a control on it`() =
        runTest {
            val store = ParkedStore(onDisk = mutableSetOf("fr"))
            val manager = manager(store, FakeConnectivityMonitor(initiallyOnline = true), backgroundScope)

            val caller = launch { manager.delete("fr") }
            runCurrent()
            advanceTimeBy(MODEL_LOCAL_TIMEOUT_MILLIS + 1)
            runCurrent()
            caller.join()

            assertThat(manager.stateOf("fr")).isAnyOf(
                OfflineModelState.Downloaded,
                OfflineModelState.NotDownloaded,
            )
        }

    /**
     * The half of #237 that outlives the screen. `confirmRemove` launches
     * `delete()` on the application scope, and `delete()` ends in `job.join()`,
     * so a delete that never returns pins that coroutine for the PROCESS
     * lifetime — only a force-stop clears it.
     *
     * Mutation: remove the `bounded(...)` wrapper around `store.delete`.
     */
    @Test
    fun `delete returns to its caller even when the Task never settles`() =
        runTest {
            val store = ParkedStore(onDisk = mutableSetOf("fr"))
            val manager = manager(store, FakeConnectivityMonitor(initiallyOnline = true), backgroundScope)

            val caller = launch { manager.delete("fr") }
            runCurrent()
            assertThat(caller.isActive).isTrue()

            advanceTimeBy(MODEL_LOCAL_TIMEOUT_MILLIS + 1)
            runCurrent()

            assertThat(caller.isCompleted).isTrue()
        }

    /**
     * The SECOND unbounded wait #237 names, behind the first: the delete job's
     * `finally` calls `refreshDownloaded()`, which is another `Task.await()`. A
     * delete that resolved perfectly would still not return if the disk read
     * behind it hung.
     *
     * Mutation: remove the `bounded(...)` wrapper inside `refreshDownloaded`.
     */
    @Test
    fun `a hung disk read in the finally cannot stop delete from returning`() =
        runTest {
            val store = ParkedStore(onDisk = mutableSetOf("fr"))
            store.parkTheDiskRead = true
            val manager = manager(store, FakeConnectivityMonitor(initiallyOnline = true), backgroundScope)

            val caller = launch { manager.delete("fr") }
            runCurrent()
            // Both waits expire in sequence: the delete itself, then the refresh.
            advanceTimeBy(MODEL_LOCAL_TIMEOUT_MILLIS * 2 + 2)
            runCurrent()

            assertThat(caller.isCompleted).isTrue()
        }
}

/**
 * Same read the sibling suites use: the manager's states are HOT (#130 rev.3
 * U-13), so a bare `first()` reads the `emptyMap()` seed rather than the state
 * under test.
 */
private suspend fun OfflineModelManager.stateOf(tag: String): OfflineModelState? =
    modelStates().first { it.isNotEmpty() }[tag]
