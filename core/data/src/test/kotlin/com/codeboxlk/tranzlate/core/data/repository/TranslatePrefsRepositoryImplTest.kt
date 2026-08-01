package com.codeboxlk.tranzlate.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codeboxlk.tranzlate.core.datastore.TranzlatePreferencesDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The issue #119 / #123.2 door: every language id entering the prefs store is
 * canonical, so the picker's raw-string radio compare can never be lied to by
 * a detector spelling that arrived via swap or history-reopen.
 */
class TranslatePrefsRepositoryImplTest {
    private fun repository(): Pair<TranslatePrefsRepositoryImpl, CountingPreferencesDataStore> {
        val store = CountingPreferencesDataStore()
        return TranslatePrefsRepositoryImpl(TranzlatePreferencesDataSource(store)) to store
    }

    // ---- the #123.2 trigger, killed at the write ----------------------------

    /**
     * Source = Detect, translate Hebrew, tap swap: the detector's own tag
     * (`iw`) used to land in `prefs.target_lang` raw, and the Hebrew row's
     * `"iw" != "he"` compare unticked the whole radio group. Canonical at the
     * door means the store simply never holds `iw` again.
     */
    @Test
    fun `a legacy detector tag is stored canonical - source`() =
        runTest {
            val (repository, _) = repository()

            repository.setSourceLang("iw")

            assertThat(repository.sourceLang.first()).isEqualTo("he")
        }

    @Test
    fun `a legacy detector tag is stored canonical - target`() =
        runTest {
            val (repository, _) = repository()

            repository.setTargetLang("zh-CN")

            assertThat(repository.targetLang.first()).isEqualTo("zh")
        }

    /** What the store now holds MATCHES a catalog row id — the picker-side compare ticks. */
    @Test
    fun `the stored id equals the catalog row id the picker compares against`() =
        runTest {
            val (repository, _) = repository()

            repository.setTargetLang("iw")

            val stored = repository.targetLang.first()
            assertThat(BundledLanguageCatalog.all.map { it.id }).contains(stored)
            assertThat(stored).isEqualTo("he")
        }

    // ---- behaviour-preserving for everything that was already right ---------

    @Test
    fun `an already-canonical id is stored unchanged`() =
        runTest {
            val (repository, _) = repository()

            repository.setSourceLang("de")
            repository.setTargetLang("pt-BR")

            assertThat(repository.sourceLang.first()).isEqualTo("de")
            assertThat(repository.targetLang.first()).isEqualTo("pt-BR")
        }

    /**
     * The `"auto"` detect sentinel is a picker affordance, not a language: the
     * resolver has no row for it, and the `?: id` fallback must pass it through
     * — a swallowed sentinel would silently turn Detect into a concrete source.
     */
    @Test
    fun `the auto sentinel passes through the canonicaliser untouched`() =
        runTest {
            val (repository, _) = repository()

            repository.setSourceLang("auto")

            assertThat(repository.sourceLang.first()).isEqualTo("auto")
        }

    /**
     * An id the resolver cannot serve at all is stored as given rather than
     * dropped: the write path must never lose data the read path would only
     * render as an unticked list — same no-data-loss stance as `setLastUsed`.
     */
    @Test
    fun `an unresolvable id falls back to itself`() =
        runTest {
            val (repository, _) = repository()

            repository.setTargetLang("zzz")

            assertThat(repository.targetLang.first()).isEqualTo("zzz")
        }

    // ---- the pair write stays atomic ----------------------------------------

    /**
     * Canonicalising both sides must not split the swap's one edit in two: a
     * torn `en→en` frame between two writes is exactly what `setLanguagePair`
     * exists to prevent. One repository call → ONE DataStore edit, and no
     * observed pair is ever half-old.
     */
    @Test
    fun `pair write canonicalises both ids inside a single atomic edit`() =
        runTest {
            val (repository, store) = repository()
            val seen = mutableListOf<Pair<String?, String?>>()
            val job =
                launch(UnconfinedTestDispatcher(testScheduler)) {
                    store.data
                        .map { prefs ->
                            prefs[stringPreferencesKey("prefs.source_lang")] to
                                prefs[stringPreferencesKey("prefs.target_lang")]
                        }.toList(seen)
                }
            val editsBefore = store.edits

            repository.setLanguagePair(sourceId = "iw", targetId = "zh-CN")

            job.cancel()
            assertThat(store.edits - editsBefore).isEqualTo(1)
            // Unset → both-set in one hop; no frame carries only half the pair.
            assertThat(seen).containsExactly(null to null, "he" to "zh").inOrder()
        }

    @Test
    fun `pair write with canonical ids behaves exactly as before`() =
        runTest {
            val (repository, store) = repository()

            repository.setLanguagePair(sourceId = "fr", targetId = "en")

            assertThat(repository.sourceLang.first()).isEqualTo("fr")
            assertThat(repository.targetLang.first()).isEqualTo("en")
            assertThat(store.edits).isEqualTo(1)
        }
}

/** In-memory [DataStore] counting its edits, so atomicity is a measured claim. */
private class CountingPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())

    var edits: Int = 0
        private set

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        edits += 1
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
