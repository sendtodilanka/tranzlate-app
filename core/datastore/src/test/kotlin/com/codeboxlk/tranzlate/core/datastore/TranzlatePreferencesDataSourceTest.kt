package com.codeboxlk.tranzlate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class TranzlatePreferencesDataSourceTest {
    @Test
    fun `empty store yields the DATA_MODEL defaults`() =
        runTest {
            val source = TranzlatePreferencesDataSource(FakePreferencesDataStore())

            assertThat(source.sourceLang.first()).isEqualTo("en")
            assertThat(source.targetLang.first()).isEqualTo("fr")
            assertThat(source.textMode.first()).isEqualTo("AUTO")
            assertThat(source.theme.first()).isEqualTo(0)
            assertThat(source.dynamicColor.first()).isFalse()
        }

    @Test
    fun `stored values are read back`() =
        runTest {
            val store = FakePreferencesDataStore()
            val source = TranzlatePreferencesDataSource(store)

            source.setTheme(2)
            source.setDynamicColor(true)

            assertThat(source.theme.first()).isEqualTo(2)
            assertThat(source.dynamicColor.first()).isTrue()
        }

    @Test
    fun `setLanguagePair writes both ids in one edit so no observer sees a torn pair`() =
        runTest {
            val store = FakePreferencesDataStore()
            val source = TranzlatePreferencesDataSource(store)

            source.setLanguagePair(sourceValue = "de", targetValue = "es")

            assertThat(store.writes).isEqualTo(1)
            assertThat(source.sourceLang.first()).isEqualTo("de")
            assertThat(source.targetLang.first()).isEqualTo("es")
        }

    /**
     * The launch-crash guard: a read failure must degrade to defaults, not escape
     * into the collector's scope. Without the `catch` in the data source this test
     * fails by throwing rather than by asserting.
     */
    @Test
    fun `an IO failure degrades to defaults instead of propagating`() =
        runTest {
            val source = TranzlatePreferencesDataSource(FailingDataStore(IOException("disk gone")))

            assertThat(source.theme.first()).isEqualTo(0)
            assertThat(source.dynamicColor.first()).isFalse()
            assertThat(source.sourceLang.first()).isEqualTo("en")
        }

    /**
     * The launch-crash guard, widened past I/O (issue #254). A read that fails
     * for ANY non-cancellation reason — a decode bug, a keystore/JNI linkage
     * error, an `IllegalStateException` from a DataStore internal — must degrade
     * to the defaults exactly as an I/O failure does. The eager
     * `stateIn(…, Eagerly)` readers on the Text screen (source / target / mode)
     * collect at construction with no handler, so before #254 any non-I/O read
     * failure crashed the screen on cold start instead of opening it.
     *
     * The mutation this locks down: narrowing the source catch back to
     * `IOException` only makes this test throw `IllegalStateException` from
     * `.first()` instead of returning a default — red. The write side stays
     * `IOException`-only on purpose; `a non-IO failure on write still propagates`
     * below is the twin that must stay green while this one flips.
     */
    @Test
    fun `a non-IO read failure degrades to defaults instead of propagating`() =
        runTest {
            val source = TranzlatePreferencesDataSource(FailingDataStore(IllegalStateException("bug")))

            assertThat(source.theme.first()).isEqualTo(0)
            assertThat(source.dynamicColor.first()).isFalse()
            assertThat(source.sourceLang.first()).isEqualTo("en")
        }

    /**
     * Cancellation is not a read failure. A `CancellationException` reaching the
     * source catch must propagate so a cancelled collector actually stops — the
     * same reason `DownloadGateTest` guards the metered gate. This is the boundary
     * of the widening above: "degrade any `Throwable`" must still exempt
     * cancellation, or structured concurrency breaks.
     *
     * Mutation: dropping the `is CancellationException` rethrow in the source
     * catch degrades it to `emptyPreferences()` instead, `.first()` returns 0, and
     * the `expected` throw never happens — red. (Verified: the `Flow.catch`
     * operator delivers an upstream `CancellationException` to its lambda, so this
     * rethrow is load-bearing, not defensive.)
     */
    @Test(expected = kotlin.coroutines.cancellation.CancellationException::class)
    fun `a CancellationException from the read is rethrown, not degraded`() =
        runTest {
            TranzlatePreferencesDataSource(
                FailingDataStore(kotlin.coroutines.cancellation.CancellationException("collector left")),
            ).theme.first()
        }

    // ---- issue #238: reads were guarded, writes were not --------------------

    /**
     * The WRITE half of the guard above.
     *
     * `DataStore.edit` is documented to throw `IOException`, and several of these
     * setters are launched fire-and-forget on the application scope — including
     * `setAllowMobileData`, the mobile-data consent checkbox. A checkbox that
     * cannot reach the disk should mis-save, not end the process: the user gets
     * asked once more than they wanted, which is the cheap side of that trade.
     *
     * Every setter is exercised, not only the one the issue named. The guard is
     * one home, and a test covering a single setter would not notice a new one
     * added beside it that went straight to `dataStore.edit`.
     */
    @Test
    fun `an IO failure on write is swallowed rather than propagating`() =
        runTest {
            val source = TranzlatePreferencesDataSource(WriteFailingDataStore(IOException("disk full")))

            source.setSourceLang("de")
            source.setTargetLang("es")
            source.setLanguagePair("de", "es")
            source.setTextMode("AUTO")
            source.setTheme(2)
            source.setDynamicColor(true)
            source.setAllowMobileData(true)
            source.recordSourceLanguageUse("de", atMillis = 1L)
            source.recordTargetLanguageUse("es", atMillis = 1L)
        }

    /**
     * Same narrow rule as the read guard: `IOException` only. Anything else is a
     * programming error and must stay loud rather than be hidden by a catch that
     * was widened for convenience.
     */
    @Test(expected = IllegalStateException::class)
    fun `a non-IO failure on write still propagates`() =
        runTest {
            TranzlatePreferencesDataSource(WriteFailingDataStore(IllegalStateException("bug")))
                .setTheme(1)
        }

    @Test
    fun `recorded language uses read back newest-first and deduplicated`() =
        runTest {
            val source = TranzlatePreferencesDataSource(FakePreferencesDataStore())

            source.recordSourceLanguageUse("fr", atMillis = 10L)
            source.recordSourceLanguageUse("es", atMillis = 20L)
            source.recordSourceLanguageUse("fr", atMillis = 30L)

            // One entry per language — a second use MOVES it, never duplicates it.
            assertThat(source.recentSourceLanguages.first()).containsExactly("fr", 30L, "es", 20L)
        }

    @Test
    fun `the recents store is capped per role map`() =
        runTest {
            val source = TranzlatePreferencesDataSource(FakePreferencesDataStore())

            repeat(TranzlatePreferencesDataSource.RECENT_STORE_LIMIT + 5) { index ->
                source.recordSourceLanguageUse("lang$index", atMillis = index.toLong())
            }
            // A full source map must never evict target entries — the caps are per key.
            source.recordTargetLanguageUse("tgt", atMillis = 1L)

            val recents = source.recentSourceLanguages.first()
            assertThat(recents).hasSize(TranzlatePreferencesDataSource.RECENT_STORE_LIMIT)
            assertThat(recents.keys).doesNotContain("lang0")
            assertThat(recents.keys).contains("lang${TranzlatePreferencesDataSource.RECENT_STORE_LIMIT + 4}")
            assertThat(source.recentTargetLanguages.first()).containsExactly("tgt", 1L)
        }

    // ---- issue #130 rev.3: recents split per role, legacy union continuity ---

    @Test
    fun `source and target recents are independent maps`() =
        runTest {
            val source = TranzlatePreferencesDataSource(FakePreferencesDataStore())

            source.recordSourceLanguageUse("en", atMillis = 10L)
            source.recordTargetLanguageUse("fr", atMillis = 20L)

            assertThat(source.recentSourceLanguages.first()).containsExactly("en", 10L)
            assertThat(source.recentTargetLanguages.first()).containsExactly("fr", 20L)
        }

    /**
     * Behaviour-preservation for the shipped 15a picker: the merged view keeps
     * the exact single-map shape it always served — union of the legacy
     * pre-split key and both role keys, newest stamp per id.
     */
    @Test
    fun `the merged view unions legacy and role maps keeping the newest stamp per id`() =
        runTest {
            val store = FakePreferencesDataStore()
            val source = TranzlatePreferencesDataSource(store)
            store.seedLegacyRecents("fr:5${SEP}de:40")

            source.recordSourceLanguageUse("fr", atMillis = 30L) // newer than legacy fr:5
            source.recordTargetLanguageUse("es", atMillis = 20L)

            assertThat(source.recentLanguages.first())
                .containsExactly("fr", 30L, "de", 40L, "es", 20L)
        }

    /** An upgrader whose picks all predate the split still has a Recent section. */
    @Test
    fun `legacy-only recents still surface in the merged view`() =
        runTest {
            val store = FakePreferencesDataStore()
            val source = TranzlatePreferencesDataSource(store)
            store.seedLegacyRecents("fr:10${SEP}es:20")

            assertThat(source.recentLanguages.first()).containsExactly("fr", 10L, "es", 20L)
            // No side was ever recorded for them, so no side claims them.
            assertThat(source.recentSourceLanguages.first()).isEmpty()
            assertThat(source.recentTargetLanguages.first()).isEmpty()
        }

    /** The legacy key is READ-ONLY: new picks must never rewrite pre-split data. */
    @Test
    fun `recording a pick leaves the legacy key byte-identical`() =
        runTest {
            val store = FakePreferencesDataStore()
            val source = TranzlatePreferencesDataSource(store)
            store.seedLegacyRecents("fr:10")

            source.recordSourceLanguageUse("es", atMillis = 99L)
            source.recordTargetLanguageUse("de", atMillis = 98L)

            assertThat(store.legacyRecentsRaw()).isEqualTo("fr:10")
        }

    /** Each key decodes independently — one corrupt map never takes the others down. */
    @Test
    fun `a corrupt role map drops alone - the merged view keeps the rest`() =
        runTest {
            val store = FakePreferencesDataStore()
            val source = TranzlatePreferencesDataSource(store)
            store.seedLegacyRecents("fr:10")
            source.recordTargetLanguageUse("es", atMillis = 20L)
            store.seedSourceRecents("garbage-no-stamp")

            assertThat(source.recentSourceLanguages.first()).isEmpty()
            assertThat(source.recentLanguages.first()).containsExactly("fr", 10L, "es", 20L)
        }

    /**
     * A corrupt preference must cost the user their recents list, never the
     * screen — the picker collects this flow, and a throw here would surface as
     * a crash on open.
     */
    @Test
    fun `a malformed recents entry is dropped, not thrown`() =
        runTest {
            val decoded =
                TranzlatePreferencesDataSource.decodeRecents(
                    listOf("fr:10", "notanumber", "es:notalong", ":5", "de:20").joinToString(""),
                )

            assertThat(decoded).containsExactly("fr", 10L, "de", 20L)
        }

    @Test
    fun `a blank language id is not recorded`() =
        runTest {
            val source = TranzlatePreferencesDataSource(FakePreferencesDataStore())

            source.recordSourceLanguageUse("  ", atMillis = 1L)
            source.recordTargetLanguageUse("  ", atMillis = 1L)

            assertThat(source.recentLanguages.first()).isEmpty()
        }

    private companion object {
        /** The codec's U+001F entry separator, spelled out so the seeds stay readable. */
        const val SEP = "\u001F"
    }
}

private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())

    /** Counts `updateData` calls so atomic-write claims can be asserted, not assumed. */
    var writes = 0
        private set

    override val data: Flow<Preferences> = state

    // `edit {}` already hands us a mutable copy, so the current value goes straight in.
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        writes++
        val updated = transform(state.value)
        state.value = updated
        return updated
    }

    /**
     * Seeds the PRE-SPLIT single key exactly as a pre-#130 build left it on
     * disk — by key STRING, so a rename of the production constant breaks
     * these tests instead of silently orphaning every upgrader's recents.
     */
    suspend fun seedLegacyRecents(raw: String) = seedRaw("prefs.recent_languages", raw)

    suspend fun seedSourceRecents(raw: String) = seedRaw("prefs.recent_languages.source", raw)

    fun legacyRecentsRaw(): String? = state.value[stringPreferencesKey("prefs.recent_languages")]

    private suspend fun seedRaw(
        key: String,
        raw: String,
    ) {
        updateData { prefs ->
            prefs.toMutablePreferences().apply { this[stringPreferencesKey(key)] = raw }
        }
    }
}

private class FailingDataStore(
    private val cause: Throwable,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw cause }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        error("not used by these tests")
}

/**
 * The mirror of [FailingDataStore]: reads fine, every WRITE fails with [cause]
 * (issue #238). A store that fails both halves could not tell a swallowed write
 * apart from a read that never happened.
 */
private class WriteFailingDataStore(
    private val cause: Throwable,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw cause
}
