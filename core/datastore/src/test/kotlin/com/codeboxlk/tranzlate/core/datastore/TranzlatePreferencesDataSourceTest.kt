package com.codeboxlk.tranzlate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
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

            source.recordLanguageUse("fr", atMillis = 10L)
            source.recordLanguageUse("es", atMillis = 20L)
            source.recordLanguageUse("fr", atMillis = 30L)

            // One entry per language — a second use MOVES it, never duplicates it.
            assertThat(source.recentLanguages.first()).containsExactly("fr", 30L, "es", 20L)
        }

    @Test
    fun `the recents store is capped`() =
        runTest {
            val source = TranzlatePreferencesDataSource(FakePreferencesDataStore())

            repeat(TranzlatePreferencesDataSource.RECENT_STORE_LIMIT + 5) { index ->
                source.recordLanguageUse("lang$index", atMillis = index.toLong())
            }

            val recents = source.recentLanguages.first()
            assertThat(recents).hasSize(TranzlatePreferencesDataSource.RECENT_STORE_LIMIT)
            assertThat(recents.keys).doesNotContain("lang0")
            assertThat(recents.keys).contains("lang${TranzlatePreferencesDataSource.RECENT_STORE_LIMIT + 4}")
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

            source.recordLanguageUse("  ", atMillis = 1L)

            assertThat(source.recentLanguages.first()).isEmpty()
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
}

private class FailingDataStore(
    private val cause: Throwable,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw cause }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        error("not used by these tests")
}
