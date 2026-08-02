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

    /**
     * Per-write fault injection for History's three write paths (issue #190).
     *
     * [failWrites] is a blanket switch over the two INSERT paths, which cannot
     * express what History needs: one path failing while the other two still
     * work. Without that, a single test proving `delete` surfaces its failure
     * would say nothing about `undoDelete` and `toggleFavourite` — which is how
     * the crash survived in all three at once.
     *
     * Each hook runs BEFORE its write. A hook that throws makes that write fail;
     * a hook that suspends holds the write open, which is how a scope cancelled
     * mid-write (the user navigating away) is reproduced.
     */
    var beforeDelete: (suspend () -> Unit)? = null

    /** @see beforeDelete */
    var beforeRestore: (suspend () -> Unit)? = null

    /** @see beforeDelete */
    var beforeSetFavourite: (suspend () -> Unit)? = null

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
        if (store.value.any { sameTuple(it, normalized) }) return -1L
        val id = nextId++
        store.value = store.value + normalized.copy(id = id)
        return id
    }

    /**
     * Mirrors [TranslationRepositoryImpl.restore][com.codeboxlk.tranzlate.core.data.repository]:
     * merge onto the occupant of the C-8 tuple, insert only when it is free. Same
     * rule as the DAO's `mergeIntoTuple` — the star is OR'd (never cleared in
     * either direction) and the earlier `created_at` wins.
     */
    override suspend fun restore(translation: Translation) {
        beforeRestore?.invoke()
        check(!failWrites) { "FakeTranslationRepository.failWrites is set" }
        val normalized = translation.copy(sourceText = normalize(translation.sourceText))
        val occupant = store.value.firstOrNull { sameTuple(it, normalized) }
        if (occupant == null) {
            save(normalized)
            return
        }
        store.value =
            store.value.map { row ->
                if (row.id != occupant.id) {
                    row
                } else {
                    row.copy(
                        favourite = row.favourite || normalized.favourite,
                        createdAt = minOf(row.createdAt, normalized.createdAt),
                    )
                }
            }
    }

    override suspend fun delete(id: Long) {
        beforeDelete?.invoke()
        store.value = store.value.filterNot { it.id == id }
    }

    override suspend fun setFavourite(
        id: Long,
        favourite: Boolean,
    ) {
        beforeSetFavourite?.invoke()
        store.value = store.value.map { if (it.id == id) it.copy(favourite = favourite) else it }
    }

    private fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /** The C-8 key the unique index enforces — both source texts already normalized. */
    private fun sameTuple(
        row: Translation,
        other: Translation,
    ): Boolean =
        row.sourceText == other.sourceText &&
            row.sourceLang == other.sourceLang &&
            row.targetLang == other.targetLang &&
            row.engine == other.engine
}
