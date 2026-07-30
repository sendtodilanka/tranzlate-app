package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory deterministic [TranslationRepository] double for unit tests
 * (history write-on-success + drawer Recents, issue #11). Applies the same C-8
 * normalization as the Room impl so cache/dedupe behaviour matches production.
 *
 * @property failWrites when true, [save] throws — proves best-effort history
 *   never fails a translation (TranslateTextUseCase rule).
 */
class FakeTranslationRepository(
    var failWrites: Boolean = false,
) : TranslationRepository {
    private val store = MutableStateFlow<List<Translation>>(emptyList())
    private var nextId = 1L

    val saved: List<Translation> get() = store.value

    override fun history(): Flow<List<Translation>> = store.map { it.sortedByDescending(Translation::createdAt) }

    override fun recent(limit: Int): Flow<List<Translation>> =
        store.map { it.sortedByDescending(Translation::createdAt).take(limit) }

    override fun favourites(): Flow<List<Translation>> =
        store.map { list -> list.filter(Translation::favourite).sortedByDescending(Translation::createdAt) }

    override suspend fun cached(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
        engine: Engine,
    ): Translation? {
        val normalized = normalize(sourceText)
        return store.value.lastOrNull {
            it.sourceText == normalized &&
                it.sourceLang == sourceLang &&
                it.targetLang == targetLang &&
                it.engine == engine
        }
    }

    override suspend fun cachedAny(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
    ): Translation? {
        val normalized = normalize(sourceText)
        return store.value
            .filter { it.sourceText == normalized && it.sourceLang == sourceLang && it.targetLang == targetLang }
            .maxByOrNull(Translation::createdAt)
    }

    override suspend fun save(translation: Translation): Long {
        check(!failWrites) { "FakeTranslationRepository.failWrites is set" }
        val normalized = translation.copy(sourceText = normalize(translation.sourceText))
        // Mirror Room's IGNORE + unique C-8 index (issue #53 A9): a duplicate
        // tuple loses the race and reports -1, exactly like the real DAO.
        val duplicate =
            store.value.any {
                it.sourceText == normalized.sourceText &&
                    it.sourceLang == normalized.sourceLang &&
                    it.targetLang == normalized.targetLang &&
                    it.engine == normalized.engine
            }
        if (duplicate) return -1L
        val id = nextId++
        store.value = store.value + normalized.copy(id = id)
        return id
    }

    override suspend fun delete(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }

    override suspend fun setFavourite(
        id: Long,
        favourite: Boolean,
    ) {
        store.value = store.value.map { if (it.id == id) it.copy(favourite = favourite) else it }
    }

    private fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")
}
