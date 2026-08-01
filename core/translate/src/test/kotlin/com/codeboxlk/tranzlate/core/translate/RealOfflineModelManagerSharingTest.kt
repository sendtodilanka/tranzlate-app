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
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope)

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
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope)

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
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope)

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
                assertThat(reopened.awaitItem()["es"]).isEqualTo(OfflineModelState.NotDownloaded)
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
            val manager = RealOfflineModelManager(store, ampleStorage, backgroundScope)

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
    ) : ModelStore {
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
            return onDisk.toSet()
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
}

private val CAPABLE_TAGS = setOf("en", "es", "fr")
private val ampleStorage = FakeStorageProbe(free = Long.MAX_VALUE)
private const val SUBSCRIBER_BURST = 5
