package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.FakeStorageProbe
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.PackEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * The download-start race the #304 cross-model co-verify quantified (BLOCK).
 *
 * The "one in-flight per tag" guard used to be a **non-atomic check-then-act**
 * (`containsKey` then a plain `activeDownloads[tag] = job`). Two callers racing the
 * same tag both passed the check, both registered, both started the transfer, and
 * both emitted `DownloadStarted` — two "Download started" snackbars and two real
 * ML Kit transfers of one pack. The lens measured it: 2 threads, 500 trials, both
 * got `Started` 47.2% of the time.
 *
 * **This test MUST use real threads.** `runTest` runs on ONE thread with a virtual
 * scheduler, so its coroutines never actually interleave inside the check-then-act
 * window — every `runTest`-based test in this module passed against the buggy code.
 * Here N real threads on `Dispatchers.Default` are released together by a
 * [CountDownLatch] onto one tag.
 *
 * **The store PARKS** each accepted download (`gate.await()`), so the winner stays
 * genuinely in-flight for the whole flood — otherwise an instant store would let the
 * winner finish and drop the tag before a straggler arrives, and that straggler's
 * `Started` is a correct FRESH download, not the concurrent duplicate under test.
 *
 * Mutation decided first (rule 11): revert the fix — `activeDownloads.putIfAbsent(...)
 * != null` back to a plain `activeDownloads[tag] = job` with the start/emit/return
 * unguarded. The invariant below (exactly one `Started`, one `store.download`, one
 * `DownloadStarted` per tag) then fails on some tag → RED. With the atomic claim →
 * GREEN.
 */
class RealOfflineManagerConcurrencyTest {
    private class ParkingStore(
        private val tags: Set<String>,
    ) : ModelStore {
        val downloadCalls = ConcurrentHashMap<String, AtomicInteger>()
        val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

        override suspend fun downloadedTags(): Set<String> = emptySet()

        override suspend fun download(tag: String) {
            downloadCalls.getOrPut(tag) { AtomicInteger() }.incrementAndGet()
            gates[tag]?.await() // stay in-flight (holding the tag) until the trial releases it
        }

        override suspend fun delete(tag: String) = Unit

        override fun isCapable(tag: String): Boolean = tag in tags

        override fun capableTags(): Set<String> = tags
    }

    @Test
    fun `concurrent duplicate requests for one tag start it once and announce it once`() {
        val trials = 30
        val threadsPerTag = 6
        val tags = (0 until trials).map { "t$it" }
        val store = ParkingStore(tags.toSet())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val manager =
                RealOfflineModelManager(
                    store,
                    FakeStorageProbe(free = Long.MAX_VALUE),
                    FakeConnectivityMonitor(),
                    scope,
                )

            // Count DownloadStarted per tag on a real subscriber. Subscribe FIRST
            // (replay = 0 drops anything emitted before the collector registers), and
            // collect on IO, NOT the download scope's Default, so a busy Default pool
            // cannot starve the count into a false miss.
            val startedEvents = ConcurrentHashMap<String, AtomicInteger>()
            val subscribed = CompletableDeferred<Unit>()
            val collector =
                scope.launch(Dispatchers.IO) {
                    manager.packEvents
                        .onSubscription { subscribed.complete(Unit) }
                        .collect { event ->
                            if (event is PackEvent.DownloadStarted) {
                                startedEvents.getOrPut(event.languageTag) { AtomicInteger() }.incrementAndGet()
                            }
                        }
                }
            runBlocking { subscribed.await() }

            val startedReturns = tags.associateWith { floodOneTag(manager, store, it, threadsPerTag, startedEvents) }

            val startedReturnViolations = tags.filter { startedReturns[it] != 1 }
            val storeCallViolations = tags.filter { (store.downloadCalls[it]?.get() ?: 0) != 1 }
            val startedEventViolations = tags.filter { (startedEvents[it]?.get() ?: 0) != 1 }

            // Exactly ONE caller wins the tag (the lens's 236/500 measurement),
            // ONE real transfer runs, and ONE DownloadStarted reaches the collector.
            assertThat(startedReturnViolations).isEmpty()
            assertThat(storeCallViolations).isEmpty()
            assertThat(startedEventViolations).isEmpty()

            collector.cancel()
        } finally {
            scope.cancel()
        }
    }

    /**
     * Floods [tag] with [threadsPerTag] real threads released together, and returns
     * how many callers got [DownloadAttempt.Started]. The store parks the winner
     * (via a fresh gate) so the tag stays held for the whole flood; the wait lets the
     * winner reach the store and its Started event deliver before the caller asserts.
     */
    private fun floodOneTag(
        manager: RealOfflineModelManager,
        store: ParkingStore,
        tag: String,
        threadsPerTag: Int,
        startedEvents: ConcurrentHashMap<String, AtomicInteger>,
    ): Int {
        val gate = CompletableDeferred<Unit>()
        store.gates[tag] = gate
        val started = AtomicInteger(0)
        val ready = CountDownLatch(threadsPerTag)
        val go = CountDownLatch(1)
        val workers =
            (1..threadsPerTag).map {
                thread {
                    ready.countDown()
                    go.await()
                    if (runBlocking { manager.download(tag) } == DownloadAttempt.Started) started.incrementAndGet()
                }
            }
        ready.await(5, TimeUnit.SECONDS)
        go.countDown() // release the flood
        workers.forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }

        // Winner is parked in store.download holding the tag. Wait for it to reach the
        // store AND its Started event to deliver, then a window to catch any erroneous
        // extra the bug adds, then release the winner so it finishes and drops the tag.
        val deadline = System.currentTimeMillis() + 5_000
        while (((store.downloadCalls[tag]?.get() ?: 0) < 1 || (startedEvents[tag]?.get() ?: 0) < 1) &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
        Thread.sleep(80)
        gate.complete(Unit)
        return started.get()
    }
}
