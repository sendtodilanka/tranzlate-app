package com.codeboxlk.tranzlate.core.translate

import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeStorageProbe
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The state-flow contract of issue #130 rev.3 (U-13): one shared upstream, one
 * disk read per subscription burst, and a re-read when a screen returns to a
 * flow nobody was watching.
 *
 * Every count here is a count of ML Kit round-trips. `getDownloadedModels` is a
 * Play-Services IPC, and before this the picker alone paid it twice on open —
 * its own transient watch and the catalog repository's overlay are two separate
 * collectors of what used to be a cold flow.
 *
 * `runCurrent()` rather than `advanceUntilIdle()` throughout: since coroutines
 * 1.11 the latter drains the test's own work and stops, so a scenario whose
 * collectors all live in `backgroundScope` never starts at all (verified — it
 * reported zero reads for a burst that had not yet run).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealOfflineModelManagerSharingTest {
    /**
     * Five screens opening together. The real number is two on the picker
     * today and four once the packs screen and the events observer land, so the
     * assertion is "one, whatever N is" rather than "one for two".
     */
    @Test
    fun `a burst of subscribers costs ONE ML Kit read`() =
        runTest {
            val store = CountingStore()
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope) { testScheduler.currentTime }

            repeat(SUBSCRIBER_BURST) {
                backgroundScope.launch { manager.modelStates().collect { } }
            }
            runCurrent()

            assertThat(store.downloadedTagsCalls).isEqualTo(1)
        }

    /**
     * Coherence: two screens watching one download see the SAME sequence, and
     * neither loses the terminal state to the other's subscription. The third
     * collector at the end is the same rule from the other side — joining a
     * watched flow hands you the live value, it does not start a private read
     * that would push a surprise emission at everyone already watching.
     */
    @Test
    fun `two collectors read one flow - same sequence, terminal state included`() =
        runTest {
            val store = CountingStore()
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope) { testScheduler.currentTime }

            turbineScope {
                val picker = manager.modelStates().testIn(backgroundScope)
                val packs = manager.modelStates().testIn(backgroundScope)

                assertThat(picker.awaitItem()).isEmpty() // the shared seed
                assertThat(packs.awaitItem()).isEmpty()
                assertThat(picker.awaitItem()["fr"]).isEqualTo(OfflineModelState.NotDownloaded)
                assertThat(packs.awaitItem()["fr"]).isEqualTo(OfflineModelState.NotDownloaded)
                assertThat(store.downloadedTagsCalls).isEqualTo(1)

                manager.download("fr")
                runCurrent()
                assertThat(picker.awaitItem()["fr"]).isEqualTo(OfflineModelState.Downloading)
                assertThat(packs.awaitItem()["fr"]).isEqualTo(OfflineModelState.Downloading)

                store.releaseDownload()
                runCurrent()
                assertThat(picker.awaitItem()["fr"]).isEqualTo(OfflineModelState.Downloaded)
                assertThat(packs.awaitItem()["fr"]).isEqualTo(OfflineModelState.Downloaded)

                // A third screen joins while the other two are still watching.
                // It is handed what they hold — not a fresh read of a disk that
                // changed behind the app's back — and their sequences do not
                // gain an entry because it arrived.
                store.onDisk += "es"
                val text = manager.modelStates().testIn(backgroundScope)
                assertThat(text.awaitItem()["es"]).isEqualTo(OfflineModelState.NotDownloaded)
                picker.expectNoEvents()
                packs.expectNoEvents()

                picker.cancelAndIgnoreRemainingEvents()
                packs.cancelAndIgnoreRemainingEvents()
                text.cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Risk R3, the disconfirming run. A `StateFlow` replays its last value
     * forever, long after `WhileSubscribed` stopped the upstream that produced
     * it — and packs can be removed from Android's app-storage settings while
     * this app is backgrounded. Without a read on the way back in, the reopened
     * screen paints that replay and calls a stale map the truth.
     */
    @Test
    fun `a resubscribe after the idle window re-reads the disk`() =
        runTest {
            val store = CountingStore()
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope) { testScheduler.currentTime }

            turbineScope {
                val screen = manager.modelStates().testIn(backgroundScope)
                assertThat(screen.awaitItem()).isEmpty()
                assertThat(screen.awaitItem()["es"]).isEqualTo(OfflineModelState.NotDownloaded)
                assertThat(store.downloadedTagsCalls).isEqualTo(1)
                screen.cancelAndIgnoreRemainingEvents()

                // Virtual time only: a real five-second sleep would be five
                // seconds on every CI run, for a window nothing but the
                // scheduler observes.
                advanceTimeBy(SHARED_STATE_IDLE_MILLIS + 1)
                runCurrent()
                store.onDisk += "es" // the disk moved while nobody was watching

                val reopened = manager.modelStates().testIn(backgroundScope)
                // The seed, not the map this flow held before. Expiring the
                // replay cache is what puts it here, and it is the honest
                // first frame: nothing has been read since the disk moved.
                assertThat(reopened.awaitItem()).isEmpty()
                // Straight from the seed to the truth: the re-read lands before
                // the merge emits, so the stale NotDownloaded frame this test
                // used to see never reaches a screen at all.
                assertThat(reopened.awaitItem()["es"]).isEqualTo(OfflineModelState.Downloaded)
                assertThat(store.downloadedTagsCalls).isEqualTo(2)
                reopened.cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * `TranslateLanguage.getAllLanguages()` answers from a static array, so the
     * merge asking it per emission bought nothing and allocated a fresh set for
     * every row transition on every collector. Four emissions here, one read.
     */
    @Test
    fun `the capable-tag list is read once, not once per emission`() =
        runTest {
            val store = CountingStore()
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope) { testScheduler.currentTime }

            turbineScope {
                val screen = manager.modelStates().testIn(backgroundScope)
                screen.awaitItem() // the seed
                screen.awaitItem() // the first merge
                manager.download("fr")
                runCurrent()
                screen.awaitItem() // Downloading
                store.releaseDownload()
                runCurrent()
                screen.awaitItem() // Downloaded

                assertThat(store.capableTagsCalls).isEqualTo(1)
                screen.cancelAndIgnoreRemainingEvents()
            }
        }

    /** Counts what the seam costs: every call here is an ML Kit call in production. */
    private class CountingStore(
        val onDisk: MutableSet<String> = mutableSetOf(),
        initial: Set<String> = emptySet(),
    ) : ModelStore {
        /** What the disk says right now — a test can change it behind the app's back. */
        var downloadedTags: Set<String> = initial

        /** Raised by the NEXT read only, then cleared: a one-off transfer failure. */
        var failNextWith: Throwable? = null

        var downloadedTagsCalls = 0
            private set

        var capableTagsCalls = 0
            private set

        private val downloadGate = CompletableDeferred<Unit>()

        fun releaseDownload() {
            downloadGate.complete(Unit)
        }

        override suspend fun downloadedTags(): Set<String> {
            downloadedTagsCalls++
            failNextWith?.let {
                failNextWith = null
                throw it
            }
            return onDisk.toSet() + downloadedTags
        }

        override suspend fun download(tag: String) {
            downloadGate.await() // parked until the test decides the transfer ended
            onDisk += tag
        }

        override suspend fun delete(tag: String) {
            onDisk -= tag
        }

        override fun isCapable(tag: String): Boolean = tag in CAPABLE_TAGS

        override fun capableTags(): Set<String> {
            capableTagsCalls++
            return CAPABLE_TAGS
        }
    }

    /**
     * The regression a co-verify lens caught, and the reason the subscriber
     * COUNT had to go. Home holds a subscription all session; ten minutes
     * later the user deletes a pack from Android's app-storage settings and
     * opens Manage packs. With a count gate the flow was never unwatched, so
     * the new screen asked nothing and rendered a pack that is not on the disk.
     *
     * A screen may not state something the app has not checked. Every
     * subscription asks; the freshness window is what keeps a burst at one.
     */
    @Test
    fun `a screen opening later gets a real reading, even while another watches`() =
        runTest {
            val store = CountingStore(initial = setOf("fr"))
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope) { testScheduler.currentTime }
            backgroundScope.launch { manager.modelStates().collect { } } // Home, and it stays
            runCurrent()
            assertThat(store.downloadedTagsCalls).isEqualTo(1)

            advanceTimeBy(TEN_MINUTES_MILLIS)
            store.downloadedTags = emptySet() // deleted from Android settings

            val later = backgroundScope.launch { manager.modelStates().collect { } }
            runCurrent()

            assertThat(store.downloadedTagsCalls).isEqualTo(2)
            later.cancel()
        }

    /**
     * The worker is the only reader of the refresh channel, so anything that
     * terminates it retires refreshing for the whole process — every later
     * request lands in a conflated buffer nobody reads. `Task.await()` raises
     * CancellationException when an ML Kit task is CANCELLED rather than
     * failed, and refreshDownloaded rethrows it by design, so one cancelled
     * task was enough to make every screen for the rest of the session show
     * whatever the last good read said.
     */
    @Test
    fun `a cancelled ML Kit task does not retire refreshing for the process`() =
        runTest {
            val store = CountingStore()
            store.failNextWith = kotlinx.coroutines.CancellationException("task cancelled")
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope) { testScheduler.currentTime }

            val first = backgroundScope.launch { manager.modelStates().collect { } }
            runCurrent()
            first.cancel()
            advanceTimeBy(TEN_MINUTES_MILLIS)

            val second = backgroundScope.launch { manager.modelStates().collect { } }
            runCurrent()

            assertThat(store.downloadedTagsCalls).isEqualTo(2) // the worker lived
            second.cancel()
        }

    /**
     * A StateFlow replays its last value forever unless the replay cache is
     * told to expire. Without that, a screen returning after the upstream
     * stopped renders the OLD map — "Downloaded" for a pack since deleted —
     * and holds it until the disk read returns. The empty seed is the honest
     * first frame: nothing known yet, which every consumer already handles.
     */
    @Test
    fun `a resubscribe after the idle window never replays the old map`() =
        runTest {
            val store = CountingStore(initial = setOf("fr"))
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope) { testScheduler.currentTime }
            val first = backgroundScope.launch { manager.modelStates().collect { } }
            runCurrent()
            first.cancel()
            advanceTimeBy(TEN_MINUTES_MILLIS)
            store.downloadedTags = emptySet() // deleted while nobody watched

            turbineScope {
                val states = manager.modelStates().testIn(backgroundScope)
                assertThat(states.awaitItem()).isEmpty() // the seed, not the stale map
                runCurrent()
                assertThat(states.awaitItem()["fr"]).isEqualTo(OfflineModelState.NotDownloaded)
                states.cancel()
            }
        }
}

private val CAPABLE_TAGS = setOf("en", "es", "fr")
private val ampleStorage = FakeStorageProbe(free = Long.MAX_VALUE)
private const val TEN_MINUTES_MILLIS = 10 * 60 * 1_000L
private const val SUBSCRIBER_BURST = 5
