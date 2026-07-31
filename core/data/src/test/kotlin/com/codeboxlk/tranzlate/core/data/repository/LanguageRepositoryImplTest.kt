package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.database.LanguageDao
import com.codeboxlk.tranzlate.core.database.LanguageEntity
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

    private fun repository(
        dao: LanguageDao = FakeLanguageDao(),
        models: OfflineModelManager = FakeOfflineModelManager(),
    ) = LanguageRepositoryImpl(dao, models)
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
