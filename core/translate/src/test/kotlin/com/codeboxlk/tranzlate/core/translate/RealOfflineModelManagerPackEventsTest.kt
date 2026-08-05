package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeStorageProbe
import com.codeboxlk.tranzlate.domain.translate.PackEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * U-1 `PackEvents` (issue #130 PR-22) — the app-shell snackbar channel.
 *
 * Every test collects with an EXPLICIT `backgroundScope.launch { collect }` + a
 * `runCurrent()` to register the subscriber BEFORE anything is driven, because the
 * flow is `replay = 0`: a notice emitted before the collector subscribes is gone,
 * so the subscription order is part of what is under test, not incidental setup.
 */
class RealOfflineModelManagerPackEventsTest {
    private class FakeStore : ModelStore {
        val downloadGate = CompletableDeferred<Unit>()
        var committed = mutableSetOf<String>()

        override suspend fun downloadedTags(): Set<String> = committed.toSet()

        override suspend fun download(tag: String) {
            downloadGate.await()
            committed += tag
        }

        override suspend fun delete(tag: String) {
            committed -= tag
        }

        override fun isCapable(tag: String): Boolean = true

        override fun capableTags(): Set<String> = setOf("en", "fr")
    }

    /** The happy path: a transfer announces its start, then its completion. */
    @Test
    fun `a completed download announces DownloadStarted then DownloadSucceeded`() =
        runTest {
            val store = FakeStore()
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)
            val events = mutableListOf<PackEvent>()
            backgroundScope.launch { manager.packEvents.collect { events += it } }
            runCurrent()

            manager.download("fr")
            runCurrent()
            store.downloadGate.complete(Unit)
            runCurrent()

            assertThat(events)
                .containsExactly(
                    PackEvent.DownloadStarted("fr"),
                    PackEvent.DownloadSucceeded("fr"),
                ).inOrder()
        }

    /**
     * The ASYNC failure is the DownloadFailed notice; it carries the mapped cause.
     */
    @Test
    fun `a transfer that fails after starting announces DownloadFailed with the cause`() =
        runTest {
            val store =
                object : ModelStore by FakeStore() {
                    override suspend fun download(tag: String): Unit = throw java.io.IOException("dns")

                    override fun capableTags(): Set<String> = setOf("fr")
                }
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)
            val events = mutableListOf<PackEvent>()
            backgroundScope.launch { manager.packEvents.collect { events += it } }
            runCurrent()

            manager.download("fr")
            runCurrent()

            assertThat(events)
                .containsExactly(
                    PackEvent.DownloadStarted("fr"),
                    PackEvent.DownloadFailed("fr", OfflineModelFailure.NETWORK),
                ).inOrder()
        }

    /**
     * **Ownership-checked emission (mutate-first).** A delete is Stopped-and-superseded:
     * the user deletes "fr", and mid-delete re-downloads it, which revokes the delete's
     * ownership of the tag (`takeTransient`). When the stale delete finally lands it no
     * longer owns the row — it is now Downloading — so it must raise NO "removed".
     *
     * Mutation decided first (rule 11): move `emit(PackEvent.Deleted(languageTag))` OUT
     * of the `if (activeDeletes.remove(languageTag, self))` guard in `delete()`'s
     * finally. Then the stale delete emits `Deleted("fr")` between the Started and the
     * Succeeded, and `containsExactly(...).inOrder()` reddens. In the shipped code the
     * guard is false for the stale job, so only Started + Succeeded appear.
     */
    @Test
    fun `a stale delete that lost the tag mid-redownload raises no Deleted`() =
        runTest {
            val base = FakeStore()
            base.committed = mutableSetOf("fr")
            val deleteGate = CompletableDeferred<Unit>()
            val store =
                object : ModelStore by base {
                    override suspend fun delete(tag: String) {
                        deleteGate.await() // park the delete so a download can race it
                        base.delete(tag)
                    }
                }
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)
            val events = mutableListOf<PackEvent>()
            backgroundScope.launch { manager.packEvents.collect { events += it } }
            runCurrent()

            val deleting = launch { manager.delete("fr") }
            runCurrent()
            manager.download("fr") // re-download mid-delete → revokes the delete's claim
            runCurrent()
            deleteGate.complete(Unit) // the stale delete lands — it no longer owns "fr"
            runCurrent()
            base.downloadGate.complete(Unit) // the winning download completes
            runCurrent()
            deleting.join()

            // No Deleted from the stale delete: only the download's own two notices.
            assertThat(events)
                .containsExactly(
                    PackEvent.DownloadStarted("fr"),
                    PackEvent.DownloadSucceeded("fr"),
                ).inOrder()
        }

    /**
     * **Non-ownership-checked path stays silent (mutate-first).** A synchronous
     * pre-flight refusal — here low disk — is reported by `download()`'s return value
     * and the row's own `Failed` state, never doubled as a snackbar. This is the exact
     * "make a non-ownership-checked path emit" the brief names.
     *
     * Mutation decided first: add `emit(PackEvent.DownloadFailed(languageTag, cause))`
     * at the storage-refusal site (or the network one) in `download()`. The refusal
     * then announces a DownloadFailed and `events` is no longer empty — RED. Shipped,
     * the refusal returns before any emit, so nothing is announced.
     */
    @Test
    fun `a pre-flight refusal announces nothing`() =
        runTest {
            val store = FakeStore()
            val probe = FakeStorageProbe(free = 10L * 1024 * 1024) // 10MB < the 150MB budget
            val manager = RealOfflineModelManager(store, probe, online, backgroundScope)
            val events = mutableListOf<PackEvent>()
            backgroundScope.launch { manager.packEvents.collect { events += it } }
            runCurrent()

            manager.download("fr") // refused for space, before enqueue
            runCurrent()

            assertThat(events).isEmpty()
        }

    /**
     * **DROP_OLDEST bound (mutate-first).** With `extraBufferCapacity = 16` and a
     * subscriber that has subscribed but not yet drained (parked in `onSubscription`),
     * the 17th un-collected notice drops the OLDEST. Driven with 17 real Deleted
     * notices (one per delete), tags `t0`..`t16`; when the collector is released it
     * receives `t1`..`t16` — `t0`, the oldest, is gone.
     *
     * Mutation decided first: change `onBufferOverflow = BufferOverflow.DROP_OLDEST` to
     * `BufferOverflow.SUSPEND` (the default). `tryEmit` then FAILS on the full buffer
     * rather than dropping the oldest, so `t16` is the one lost and the collector
     * receives `t0`..`t15` — the `containsExactly(t1..t16)` reddens. `PACK_EVENTS_BUFFER`
     * is asserted to be 16 so the boundary the test drives is the production number.
     */
    @Test
    fun `packEvents drops the OLDEST notice when the buffer overflows`() =
        runTest {
            assertThat(PACK_EVENTS_BUFFER).isEqualTo(16)
            val tags = (0..16).map { "t$it" } // 17 tags — one past the buffer
            val store =
                object : ModelStore by FakeStore() {
                    override suspend fun downloadedTags(): Set<String> = tags.toSet()

                    override fun capableTags(): Set<String> = tags.toSet()
                }
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)

            val gate = CompletableDeferred<Unit>()
            val received = mutableListOf<PackEvent>()
            // Subscribed (so the buffer retains) but parked before draining, so all 17
            // notices land in the 16-slot buffer and the overflow policy decides.
            backgroundScope.launch {
                manager.packEvents.onSubscription { gate.await() }.collect { received += it }
            }
            runCurrent()

            tags.forEach { manager.delete(it) } // 17 Deleted notices, collector still parked
            runCurrent()
            gate.complete(Unit) // release — the buffer drains
            runCurrent()

            val receivedTags = received.filterIsInstance<PackEvent.Deleted>().map { it.languageTag }
            assertThat(receivedTags).containsExactlyElementsIn((1..16).map { "t$it" }).inOrder()
            assertThat(receivedTags).doesNotContain("t0") // the oldest was dropped
        }

    /**
     * **replay = 0, the consuming half of "backgrounded events are dropped"
     * (mutate-first).** A notice emitted while nothing is collecting is gone; a
     * collector that subscribes later is told nothing about the past. This is why the
     * shell can collect under `repeatOnLifecycle(STARTED)` and trust that a return from
     * background replays no stale snackbar.
     *
     * Mutation decided first: set `replay = 1` on the manager's `MutableSharedFlow`.
     * The Deleted emitted before anyone subscribed is then retained and delivered to
     * the late subscriber, so `received` is `[Deleted("fr")]` — RED. Shipped, `replay =
     * 0` keeps nothing.
     */
    @Test
    fun `packEvents replays nothing to a subscriber that arrives after the notice`() =
        runTest {
            val store = FakeStore()
            store.committed = mutableSetOf("fr")
            val manager = RealOfflineModelManager(store, plentyFree, online, backgroundScope)

            manager.delete("fr") // Deleted emitted with NO subscriber → dropped
            runCurrent()

            val received = mutableListOf<PackEvent>()
            backgroundScope.launch { manager.packEvents.collect { received += it } }
            runCurrent()

            assertThat(received).isEmpty()
        }
}

private val plentyFree = FakeStorageProbe(free = Long.MAX_VALUE)
private val online = FakeConnectivityMonitor()
