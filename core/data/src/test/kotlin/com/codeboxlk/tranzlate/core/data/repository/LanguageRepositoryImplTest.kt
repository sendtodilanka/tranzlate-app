package com.codeboxlk.tranzlate.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import app.cash.turbine.test
import com.codeboxlk.tranzlate.core.database.LanguageDao
import com.codeboxlk.tranzlate.core.database.LanguageEntity
import com.codeboxlk.tranzlate.core.datastore.TranzlatePreferencesDataSource
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.core.testing.FakeOfflineVoiceCatalog
import com.codeboxlk.tranzlate.domain.speech.OfflineVoiceCatalog
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageRepositoryImplTest {
    @Test
    fun `an empty table serves the whole bundled catalog`() =
        runTest {
            val repository = repository()

            val languages = repository.languages().first()

            assertThat(languages).hasSize(BundledLanguageCatalog.all.size)
            assertThat(languages.map { it.id })
                .containsExactlyElementsIn(BundledLanguageCatalog.all.map { it.id })
        }

    /**
     * The whole point of the overlay: the catalog constant says `false` for every
     * row, so anything reading `true` can only have come from the device.
     *
     * `drop(1)` is the contract, not a workaround — the first emission is
     * deliberately the pre-ML-Kit paint asserted in
     * `the catalog is served even when model state never arrives`, and device
     * truth lands on the one after it.
     */
    @Test
    fun `a downloaded model is overlaid onto the catalog row`() =
        runTest {
            val models =
                FakeOfflineModelManager(
                    mapOf(
                        "fr" to OfflineModelState.Downloaded,
                        "de" to OfflineModelState.NotDownloaded,
                        "es" to OfflineModelState.Downloading,
                    ),
                )
            val repository = repository(models = models)

            val languages =
                repository
                    .languages()
                    .drop(1)
                    .first()
                    .associateBy { it.id }

            assertThat(languages.getValue("fr").offlineDownloaded).isTrue()
            assertThat(languages.getValue("de").offlineDownloaded).isFalse()
            // Mid-download is not downloaded — a half-transferred model cannot translate.
            assertThat(languages.getValue("es").offlineDownloaded).isFalse()
            // Online-only languages have no model to be downloaded at all.
            assertThat(languages.getValue("ceb").offlineDownloaded).isFalse()
        }

    /**
     * ML Kit reaches Play Services to answer `getDownloadedModels`. On a device
     * without it, or with it wedged, that answer may never come — and a picker
     * that shows nothing until it does is a dead end (EDGE_CASES). The list must
     * paint first and gain the download ticks afterwards.
     */
    @Test
    fun `the catalog is served even when model state never arrives`() =
        runTest {
            val repository = repository(models = SilentOfflineModelManager())

            val languages = repository.languages().first()

            assertThat(languages).hasSize(BundledLanguageCatalog.all.size)
            assertThat(languages.none { it.offlineDownloaded }).isTrue()
        }

    @Test
    fun `a seeded table wins over the bundled catalog`() =
        runTest {
            val dao =
                FakeLanguageDao(
                    listOf(
                        LanguageEntity(
                            id = "en",
                            name = "English",
                            offlineAvailable = true,
                            offlineDownloaded = false,
                        ),
                    ),
                )
            val repository = repository(dao = dao)

            val languages = repository.languages().first()

            assertThat(languages).hasSize(1)
            assertThat(languages.single().id).isEqualTo("en")
        }

    /**
     * An id can arrive from language detection or a restored preference in an
     * alternate spelling. Writing it through unchanged would update no row —
     * a silent no-op, not an error.
     */
    @Test
    fun `a legacy id is normalised before the last-used write`() =
        runTest {
            val dao = FakeLanguageDao()
            val repository = repository(dao = dao)

            repository.setLastUsed("iw", LanguageRole.SOURCE, atMillis = 42L)

            assertThat(dao.lastUsedWrites).containsExactly("he" to 42L)
        }

    /**
     * A tag the catalog cannot serve is still written through rather than
     * dropped: swallowing it here would hide the caller's bug, and the DAO
     * update is a harmless no-op when no row matches.
     */
    @Test
    fun `an unresolvable id is written through unchanged`() =
        runTest {
            val dao = FakeLanguageDao()
            val repository = repository(dao = dao)

            repository.setLastUsed("zzz", LanguageRole.TARGET, atMillis = 7L)

            assertThat(dao.lastUsedWrites).containsExactly("zzz" to 7L)
        }

    /**
     * The picker's whole Recent section hangs off this. It used to hang off the
     * DAO's `UPDATE … WHERE id = ?`, against a table nothing ever seeds — so the
     * write matched zero rows, `lastUsedAt` stayed null on every row, and Recent
     * rendered empty for every user while looking implemented.
     *
     * This asserts the overlay reaches the row, which is the part that was
     * missing; `a last-used write survives without the table` proves it does not
     * depend on the table at all.
     */
    @Test
    fun `a recorded use is overlaid onto the catalog row`() =
        runTest {
            val repository = repository()

            repository.setLastUsed("fr", LanguageRole.TARGET, atMillis = 1_234L)
            // `drop(1)` is the contract, not a workaround — identical to the
            // model-state overlay above. The first emission is the deliberate
            // paint that does not wait on DataStore; recents land on the next.
            val languages = repository.languages().drop(1).first()

            assertThat(languages.single { it.id == "fr" }.lastUsedAt).isEqualTo(1_234L)
            assertThat(languages.filter { it.lastUsedAt != null }.map { it.id }).containsExactly("fr")
        }

    @Test
    fun `a last-used write survives without the table`() =
        runTest {
            val dao = FakeLanguageDao()
            val repository = repository(dao = dao)

            repository.setLastUsed("es", LanguageRole.TARGET, atMillis = 99L)

            // The table is empty, so the DAO update matched nothing — and the
            // recent is still readable. That is the regression this guards.
            assertThat(dao.languages().first()).isEmpty()
            assertThat(
                repository
                    .languages()
                    .drop(1)
                    .first()
                    .single { it.id == "es" }
                    .lastUsedAt,
            ).isEqualTo(99L)
        }

    /** The legacy spelling must be canonical BEFORE it is recorded, or it can never match a row. */
    @Test
    fun `a legacy id is canonical in the recents overlay too`() =
        runTest {
            val repository = repository()

            repository.setLastUsed("iw", LanguageRole.SOURCE, atMillis = 5L)

            assertThat(
                repository
                    .languages()
                    .drop(1)
                    .first()
                    .single { it.id == "he" }
                    .lastUsedAt,
            ).isEqualTo(5L)
        }

    /**
     * Behaviour-preservation for the shipped 15a picker (issue #130 rev.3): the
     * role split happens in the STORE; the overlay this screen reads stays the
     * union of both sides, newest per id, exactly as before the split.
     */
    @Test
    fun `source and target picks meet in the one merged overlay`() =
        runTest {
            val repository = repository()

            repository.setLastUsed("en", LanguageRole.SOURCE, atMillis = 10L)
            repository.setLastUsed("fr", LanguageRole.TARGET, atMillis = 20L)
            repository.setLastUsed("en", LanguageRole.TARGET, atMillis = 30L) // newest en wins

            val overlaid =
                repository
                    .languages()
                    .drop(1)
                    .first()
                    .filter { it.lastUsedAt != null }
                    .associate { it.id to it.lastUsedAt }
            assertThat(overlaid).containsExactly("en", 30L, "fr", 20L)
        }

    /**
     * 16a's section header names the role, so its rows have to be true of that
     * role. `recentSelections(TARGET)` therefore reads the target key ALONE —
     * a source-only pick must not surface under "Recently used as target", even
     * though the merged overlay one test above deliberately still shows both.
     */
    @Test
    fun `target recents exclude a pick made on the source side`() =
        runTest {
            val repository = repository()

            repository.setLastUsed("en", LanguageRole.SOURCE, atMillis = 10L)
            repository.setLastUsed("fr", LanguageRole.TARGET, atMillis = 20L)

            assertThat(repository.recentSelections(LanguageRole.TARGET).first())
                .containsExactly("fr", 20L)
        }

    /**
     * The source side keeps the merged view the shipped 15a picker renders. Its
     * header is role-neutral ("Recent"), and the pre-split legacy key carries no
     * side at all — a source-only read would silently drop an upgrader's whole
     * recents list to make a header that never claimed a role more precise.
     */
    @Test
    fun `source recents stay the merged view 15a already renders`() =
        runTest {
            val repository = repository()

            repository.setLastUsed("en", LanguageRole.SOURCE, atMillis = 10L)
            repository.setLastUsed("fr", LanguageRole.TARGET, atMillis = 20L)

            assertThat(repository.recentSelections(LanguageRole.SOURCE).first())
                .containsExactly("en", 10L, "fr", 20L)
        }

    /** Nothing picked for a side yet is an empty map, never the other side's. */
    @Test
    fun `an untouched side serves an empty recents map`() =
        runTest {
            val repository = repository()

            repository.setLastUsed("en", LanguageRole.SOURCE, atMillis = 10L)

            assertThat(repository.recentSelections(LanguageRole.TARGET).first()).isEmpty()
        }

    /**
     * Ruling R6's disconfirming gate at the selection end: a pick writes
     * RECENTS, never translation-usage. The proof is structural — this class
     * cannot reach the usage store it does not depend on — and this pins that
     * structure so wiring `LanguageUsageRepository`/`LanguageUsageDao` into the
     * catalog repository fails a named test instead of slipping through review.
     */
    @Test
    fun `R6 - the selection path has no route to the translation-usage store`() {
        val dependencyTypes =
            LanguageRepositoryImpl::class.java.constructors
                .flatMap { it.parameterTypes.toList() }
                .map { it.name }

        assertThat(dependencyTypes).isNotEmpty()
        dependencyTypes.forEach { assertThat(it).doesNotContain("LanguageUsage") }
    }

    /**
     * Risk R10, first half — the operator may not move UP or out. `dataStore
     * .data` re-emits the WHOLE preferences object on every write, so a theme
     * toggle, a mode switch or a consent answer each arrive here as a recents
     * map identical to the last one. Without `distinctUntilChanged` on this
     * source, every one of them rebuilds 194 catalog rows and re-emits a list
     * nothing about the languages has changed.
     */
    @Test
    fun `R10 - an unrelated preference write never rebuilds the language list`() =
        runTest {
            val store = FakeLanguagePreferencesStore()
            val repository = repository(store = store)

            repository.languages().test {
                runCurrent()
                expectMostRecentItem() // drain the opening paint, whatever its shape

                store.updateData { prefs ->
                    prefs.toMutablePreferences().apply { this[UNRELATED_PREFERENCE] = 1 }
                }
                runCurrent()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Risk R10, second half — and it may not move DOWN either. Below the
     * `onStart` prefix it would compare the first REAL recents value against
     * that prefix, and on a fresh install both are empty: the real one is
     * swallowed, this source collapses to a single emission, and every reader
     * that waits for the value after the paint — `drop(1)` in the tests above,
     * a screen waiting for device truth in production — waits forever.
     *
     * The silent model manager keeps the arithmetic honest: it contributes
     * exactly one emission (the repository's own prefix), so the second
     * emission this asserts can only have come from recents.
     *
     * Collected through a DISPATCHED collector, which is how a ViewModel's
     * `stateIn` collects. `combine` folds every update already queued when its
     * loop next runs into a single emission, so an undispatched collector —
     * turbine's default start — legitimately sees the paint and the recents
     * answer as one item and would make this count say nothing. Verified both
     * ways before this test was written.
     */
    @Test
    fun `R10 - a fresh install still delivers the recents emission after the paint`() =
        runTest {
            val repository = repository(models = SilentOfflineModelManager())
            val emissions = mutableListOf<Int>()

            backgroundScope.launch { repository.languages().collect { emissions += it.size } }
            runCurrent()

            assertThat(emissions.size).isAtLeast(2)
        }

    /** Unbounded growth would put every language the user ever touched in one preference. */
    @Test
    fun `the recents store keeps only the newest entries`() =
        runTest {
            val store = FakeLanguagePreferencesStore()
            val repository = repository(store = store)
            val ids = BundledLanguageCatalog.all.take(TranzlatePreferencesDataSource.RECENT_STORE_LIMIT + 3)

            ids.forEachIndexed { index, language ->
                repository.setLastUsed(language.id, LanguageRole.SOURCE, atMillis = index.toLong())
            }
            val remembered =
                repository
                    .languages()
                    .drop(1)
                    .first()
                    .filter { it.lastUsedAt != null }

            assertThat(remembered).hasSize(TranzlatePreferencesDataSource.RECENT_STORE_LIMIT)
            // Newest survive: the first three writes are the ones dropped.
            assertThat(remembered.map { it.id }).containsNoneIn(ids.take(3).map { it.id })
        }

    /**
     * Issue #130 rev.3 U-3, and the rule the whole voice overlay hangs off: the
     * voice set DECORATES rows, it never selects them. A device with no TTS
     * engine at all — the empty answer — must still get every catalog row, with
     * no mark on any of them.
     *
     * The failure this makes impossible is the tempting one: filtering or
     * flat-mapping the catalog by the voice set instead of copying a flag onto
     * it. That reads fine, passes a "marks appear" test, and silently deletes
     * every language on a device with no voices.
     */
    @Test
    fun `a device with no offline voices still serves every language`() =
        runTest {
            val repository = repository(voices = FakeOfflineVoiceCatalog(ids = emptySet()))

            val languages = repository.languages().first()

            assertThat(languages).hasSize(BundledLanguageCatalog.all.size)
            assertThat(languages.none { it.hasOfflineVoice }).isTrue()
        }

    /**
     * The same rule against the harder case: the ask never answers at all.
     * Enumerating TTS voices binds a service in another process and, on a device
     * with no engine, ends in a five-second timeout — so "not yet" is a state
     * this list spends real time in. It paints the full catalog anyway, exactly
     * as it does for ML Kit's model state.
     */
    @Test
    fun `the catalog is served even when the voice answer never arrives`() =
        runTest {
            val repository = repository(voices = SilentOfflineVoiceCatalog())

            val languages = repository.languages().first()

            assertThat(languages).hasSize(BundledLanguageCatalog.all.size)
            assertThat(languages.none { it.hasOfflineVoice }).isTrue()
        }

    /**
     * The other half: when the device DOES answer, the flag lands on exactly the
     * rows it named and on no others — and the row count is untouched, which is
     * the "decoration, not selection" rule stated from the marked side.
     *
     * `drop(1)` is the same contract as the model-state and recents overlays
     * above: the first emission is the paint that waits for nothing.
     */
    @Test
    fun `an offline voice is overlaid onto the catalog row`() =
        runTest {
            val repository = repository(voices = FakeOfflineVoiceCatalog(ids = setOf("es", "pt-BR")))

            val languages = repository.languages().drop(1).first()

            assertThat(languages).hasSize(BundledLanguageCatalog.all.size)
            assertThat(languages.filter { it.hasOfflineVoice }.map { it.id })
                .containsExactly("es", "pt-BR")
        }

    /**
     * The seam is a per-DEVICE ask, not a per-row one. Asking inside the row
     * mapping would bind and release a TTS engine 194 times per emission; the
     * spy counts what actually happened.
     */
    @Test
    fun `the voice catalog is asked once for the whole list`() =
        runTest {
            val voices = FakeOfflineVoiceCatalog(ids = setOf("es"))
            val repository = repository(voices = voices)

            repository.languages().drop(1).first()

            assertThat(voices.calls).isEqualTo(1)
        }

    private fun repository(
        dao: LanguageDao = FakeLanguageDao(),
        models: OfflineModelManager = FakeOfflineModelManager(),
        voices: OfflineVoiceCatalog = FakeOfflineVoiceCatalog(),
        store: FakeLanguagePreferencesStore = FakeLanguagePreferencesStore(),
    ) = LanguageRepositoryImpl(dao, models, voices, TranzlatePreferencesDataSource(store))
}

/**
 * A key this repository never reads — the stand-in for theme, mode and consent,
 * every one of which writes to the same preferences object the recents live in.
 */
private val UNRELATED_PREFERENCE = intPreferencesKey("prefs.theme")

/** In-memory `DataStore`, so the real preference codec is what these tests exercise. */
private class FakeLanguagePreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

private class FakeLanguageDao(
    rows: List<LanguageEntity> = emptyList(),
) : LanguageDao {
    private val state = MutableStateFlow(rows)
    val lastUsedWrites = mutableListOf<Pair<String, Long>>()

    override fun languages(): Flow<List<LanguageEntity>> = state

    override suspend fun upsertAll(languages: List<LanguageEntity>) {
        state.value = languages
    }

    override suspend fun setLastUsed(
        languageId: String,
        atMillis: Long,
    ) {
        lastUsedWrites += languageId to atMillis
    }
}

private class FakeOfflineModelManager(
    private val states: Map<String, OfflineModelState> = emptyMap(),
) : OfflineModelManager {
    override fun modelStates(): Flow<Map<String, OfflineModelState>> = MutableStateFlow(states)

    override suspend fun download(languageTag: String) = DownloadAttempt.Started

    override suspend fun delete(languageTag: String) = Unit
}

/** Stands in for ML Kit never answering — the flow that emits nothing, ever. */
private class SilentOfflineModelManager : OfflineModelManager {
    override fun modelStates(): Flow<Map<String, OfflineModelState>> = flow { awaitCancellation() }

    override suspend fun download(languageTag: String) = DownloadAttempt.Started

    override suspend fun delete(languageTag: String) = Unit
}

/**
 * Stands in for a TTS engine that binds and then never calls back — the case
 * the production catalog spends its whole timeout on, seen from the list's side.
 */
private class SilentOfflineVoiceCatalog : OfflineVoiceCatalog {
    override suspend fun offlineVoiceLanguageIds(): Set<String> = awaitCancellation()
}
