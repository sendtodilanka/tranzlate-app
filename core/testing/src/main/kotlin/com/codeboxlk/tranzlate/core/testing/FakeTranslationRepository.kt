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

    override suspend fun save(translation: Translation): Long {
        check(!failWrites) { "FakeTranslationRepository.failWrites is set" }
        val id = nextId++
        store.value = store.value + translation.copy(id = id, sourceText = normalize(translation.sourceText))
        return id
    }

    override suspend fun setFavourite(
        id: Long,
        favourite: Boolean,
    ) {
        store.value = store.value.map { if (it.id == id) it.copy(favourite = favourite) else it }
    }

    private fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")
}
