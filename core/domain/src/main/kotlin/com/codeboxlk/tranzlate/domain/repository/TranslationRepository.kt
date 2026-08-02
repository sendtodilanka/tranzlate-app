package com.codeboxlk.tranzlate.domain.repository

import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation
import kotlinx.coroutines.flow.Flow

/**
 * History/saved/cache repository contract (DATA_MODEL `translation`).
 * Cache rule C-8: lookup by `(sourceText, sourceLang, targetLang, engine)` on the
 * NORMALIZED source text (trim + collapse internal whitespace, case-preserved) —
 * no sha, no separate cache-key column.
 */
interface TranslationRepository {
    fun history(): Flow<List<Translation>>

    /** Newest [limit] rows — the drawer's Recents (issue #76: DB-limited, not in-memory). */
    fun recent(limit: Int): Flow<List<Translation>>

    fun favourites(): Flow<List<Translation>>

    /** C-8 cache-first lookup; [sourceText] may be un-normalized — impl normalizes. */
    suspend fun cached(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
        engine: Engine,
    ): Translation?

    /**
     * Engine-AGNOSTIC cache read (owner's pipeline, 2026-07-29 / issue #53 A2):
     * any engine's prior answer for the same normalized (text, source, target)
     * is acceptable - newest wins. The engine-keyed [cached] stays the WRITE-side
     * dedupe key (C-8); this is the read-side relaxation that makes a repeat
     * translation cost zero API calls.
     */
    suspend fun cachedAny(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
    ): Translation?

    suspend fun save(translation: Translation): Long

    /**
     * Undo of [delete] (issue #179) — puts [translation] back, `id` ignored.
     *
     * [save] alone could not do this. Its insert is `IGNORE`-on-conflict, so when
     * the row's C-8 tuple had been retaken (delete, then the same text translated
     * again — reachable for the nine `LanguageTagResolver` legacy aliases since
     * #177) the write returned -1 and the undo was a silent no-op: the star and the
     * original stamp were gone and the snackbar had already said "deleted".
     *
     * Restore MERGES rather than fighting for the tuple. A row with the same
     * (text, source, target, engine) is the SAME entry by C-8 — the DB's unique
     * index says so — so the star is carried across (never cleared in either
     * direction) and the earlier `created_at` wins. Nothing is deleted: the
     * alternative, evicting the occupant so the old row can be re-inserted, would
     * make Undo destroy a row the user never asked to delete.
     */
    suspend fun restore(translation: Translation)

    /**
     * How many SAVED translations use [languageId] as their source or their
     * target — the number in the remove-pack sheet's line *"3 saved phrases use
     * Spanish"* (issue #130 PR-19, ruling U-10).
     *
     * A one-shot read rather than a `Flow`: it is asked once, while a
     * confirmation sheet is opening, about a language the user has just pointed
     * at. Nothing on that surface changes the saved set while it is up, so a
     * subscription would be a live query serving a number that cannot move.
     *
     * Saved means starred (D-3 `favourite`) — the History screen's "Saved" tab.
     * A row using the language on BOTH sides counts once: it is one phrase.
     */
    suspend fun savedCountUsing(languageId: String): Int

    /** Removes one history row (issue #80 swipe-to-delete; Undo restores the content). */
    suspend fun delete(id: Long)

    /** D-3: star toggles favourite. */
    suspend fun setFavourite(
        id: Long,
        favourite: Boolean,
    )
}
