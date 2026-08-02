package com.codeboxlk.tranzlate.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translation ORDER BY created_at DESC")
    fun history(): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translation ORDER BY created_at DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translation WHERE favourite = 1 ORDER BY created_at DESC")
    fun favourites(): Flow<List<TranslationEntity>>

    /** C-8 cache lookup — [sourceText] must already be normalized by the caller. */
    @Query(
        "SELECT * FROM translation " +
            "WHERE source_text = :sourceText AND source_lang = :sourceLang " +
            "AND target_lang = :targetLang AND engine = :engine " +
            "ORDER BY created_at DESC LIMIT 1",
    )
    suspend fun cached(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
        engine: String,
    ): TranslationEntity?

    /** Newest row for the tuple regardless of engine (read-side relaxation, issue #53 A2). */
    @Query(
        "SELECT * FROM translation " +
            "WHERE source_text = :sourceText AND source_lang = :sourceLang " +
            "AND target_lang = :targetLang " +
            "ORDER BY created_at DESC LIMIT 1",
    )
    suspend fun cachedAny(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
    ): TranslationEntity?

    /**
     * IGNORE + the unique C-8 index (issue #53 A9): two concurrent identical
     * translations race to one row - the loser gets -1, never a duplicate.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TranslationEntity): Long

    /**
     * Undo's whole write, in ONE transaction (issue #189 co-verify).
     *
     * [mergeIntoTuple] then [insert] were two separate statements in the
     * repository. A writer landing between them — an in-flight
     * `saveToHistory` finishing for the same tuple, or a second undo — takes
     * the tuple after the merge has already reported 0, so the insert that
     * follows hits the UNIQUE C-8 index, `IGNORE`s, returns -1, and the star
     * and the original stamp are dropped in silence. That is issue #179's own
     * harm, narrowed to a race window instead of firing on every
     * occupied-tuple undo. The co-verify lens reproduced it rather than
     * arguing it.
     *
     * Room runs a `@Transaction` method's queries in one transaction, so the
     * tuple cannot change between the two.
     */
    @Transaction
    suspend fun restoreTuple(entity: TranslationEntity) {
        val merged =
            mergeIntoTuple(
                sourceText = entity.sourceText,
                sourceLang = entity.sourceLang,
                targetLang = entity.targetLang,
                engine = entity.engine,
                favourite = entity.favourite,
                createdAt = entity.createdAt,
            )
        if (merged == 0) insert(entity)
    }

    /**
     * Undo-merge (issue #179) — folds a deleted row's star and original stamp onto
     * whatever now occupies its C-8 tuple. Returns the rows changed: the tuple is
     * UNIQUE, so that is 1 (merged) or 0 (the tuple is free — insert instead).
     *
     * `MAX` on `favourite` is the OR that [MigrationOneToTwo] already applies when
     * it collapses a duplicate group, and for the same reason: a merge must never
     * clear a star the user chose to set, in EITHER direction. `MIN` on `created_at`
     * restores the earlier stamp — `created_at` means "first recorded", a repeat
     * translation never bumps it (`TranslateTextUseCase.saveToHistory` writes
     * nothing on a C-8 hit), so the occupant's newer stamp exists only because the
     * delete removed the row that would have answered the cache. Taking the older
     * one restores the state that would have existed had the delete not happened.
     * `target_text` is deliberately NOT touched: the user expressed intent with the
     * star, not with the engine's wording, and the occupant's is the current answer.
     */
    @Query(
        "UPDATE translation " +
            "SET favourite = MAX(favourite, :favourite), created_at = MIN(created_at, :createdAt) " +
            "WHERE source_text = :sourceText AND source_lang = :sourceLang " +
            "AND target_lang = :targetLang AND engine = :engine",
    )
    suspend fun mergeIntoTuple(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
        engine: String,
        favourite: Boolean,
        createdAt: Long,
    ): Int

    /**
     * How many SAVED translations use [languageId] on either side — the count in
     * the remove-pack sheet's line *"3 saved phrases use Spanish"* (#130 PR-19,
     * ruling U-10).
     *
     * ## Why this is not `WHERE favourite = 1 AND (source_lang = ? OR target_lang = ?)`
     *
     * That is the obvious spelling and it does **not** reach the two indices
     * [TranslationEntity] declares for it, which would make the pair of indices
     * dead weight in every install. Measured with `EXPLAIN QUERY PLAN` on the
     * exported v4 schema (sqlite 3.51.0), same table, same rows, three states:
     *
     * | query | table state | plan |
     * |---|---|---|
     * | `OR` form | empty | `SEARCH … USING INDEX index_translation_favourite (favourite=?)` |
     * | `OR` form | 4000 rows, no `ANALYZE` | `SEARCH … USING INDEX index_translation_favourite (favourite=?)` |
     * | `OR` form | 4000 rows, after `ANALYZE` | `MULTI-INDEX OR` over both pairs |
     * | this form | any of the three | `SEARCH … USING COVERING INDEX …_favourite_source_lang` + `…_favourite_target_lang` |
     *
     * SQLite's OR optimization needs `sqlite_stat1` to prefer two index lookups
     * over one, and **Room never runs `ANALYZE`** — so on a real install the
     * `OR` form walks every saved row and looks each one up in the table. The
     * union of two single-column lookups is index-backed with no statistics at
     * all, which is the property that has to hold on a user's phone rather than
     * on a bench.
     *
     * `UNION` (not `UNION ALL`) is load-bearing: a row whose source and target
     * are BOTH [languageId] appears in both branches and is one saved phrase,
     * not two. Deduplicating on `id` is what makes the two-branch form return
     * exactly what the single-statement `OR` form returns.
     */
    @Query(
        "SELECT COUNT(*) FROM (" +
            "SELECT id FROM translation WHERE favourite = 1 AND source_lang = :languageId " +
            "UNION " +
            "SELECT id FROM translation WHERE favourite = 1 AND target_lang = :languageId" +
            ")",
    )
    suspend fun savedCountUsing(languageId: String): Int

    @Query("DELETE FROM translation WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE translation SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(
        id: Long,
        favourite: Boolean,
    )
}
