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

    /** Anything that is not I/O is a programming error and must stay loud. */
    @Test(expected = IllegalStateException::class)
    fun `a non-IO failure still propagates`() =
        runTest {
            TranzlatePreferencesDataSource(FailingDataStore(IllegalStateException("bug")))
                .theme
                .first()
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
