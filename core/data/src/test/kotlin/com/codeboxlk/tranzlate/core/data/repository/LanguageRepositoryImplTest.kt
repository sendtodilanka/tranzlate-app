package com.codeboxlk.tranzlate.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.codeboxlk.tranzlate.core.database.LanguageDao
import com.codeboxlk.tranzlate.core.database.LanguageEntity
import com.codeboxlk.tranzlate.core.datastore.TranzlatePreferencesDataSource
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

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

            repository.setLastUsed("iw", atMillis = 42L)

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

            repository.setLastUsed("zzz", atMillis = 7L)

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

            repository.setLastUsed("fr", atMillis = 1_234L)
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

            repository.setLastUsed("es", atMillis = 99L)

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

            repository.setLastUsed("iw", atMillis = 5L)

            assertThat(
                repository
                    .languages()
                    .drop(1)
                    .first()
                    .single { it.id == "he" }
                    .lastUsedAt,
            ).isEqualTo(5L)
        }

    /** Unbounded growth would put every language the user ever touched in one preference. */
    @Test
    fun `the recents store keeps only the newest entries`() =
        runTest {
            val store = FakeLanguagePreferencesStore()
            val repository = repository(store = store)
            val ids = BundledLanguageCatalog.all.take(TranzlatePreferencesDataSource.RECENT_STORE_LIMIT + 3)

            ids.forEachIndexed { index, language -> repository.setLastUsed(language.id, atMillis = index.toLong()) }
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

    private fun repository(
        dao: LanguageDao = FakeLanguageDao(),
        models: OfflineModelManager = FakeOfflineModelManager(),
        store: FakeLanguagePreferencesStore = FakeLanguagePreferencesStore(),
    ) = LanguageRepositoryImpl(dao, models, TranzlatePreferencesDataSource(store))
}

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

    override suspend fun download(languageTag: String) = Unit

    override suspend fun delete(languageTag: String) = Unit
}

/** Stands in for ML Kit never answering — the flow that emits nothing, ever. */
private class SilentOfflineModelManager : OfflineModelManager {
    override fun modelStates(): Flow<Map<String, OfflineModelState>> = flow { awaitCancellation() }

    override suspend fun download(languageTag: String) = Unit

    override suspend fun delete(languageTag: String) = Unit
}
