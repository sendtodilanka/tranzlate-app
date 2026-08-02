package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.database.TranslationDao
import com.codeboxlk.tranzlate.core.database.TranslationEntity
import com.codeboxlk.tranzlate.core.model.Engine
import com.codeboxlk.tranzlate.core.model.Translation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * `restore` — Undo's write path (issue #179).
 *
 * The Room DAO is faked with the semantics its annotations declare: `@Insert(IGNORE)`
 * plus the UNIQUE C-8 index gives -1 on a taken tuple, and `mergeIntoTuple` is the
 * single UPDATE with `MAX(favourite, …)` / `MIN(created_at, …)`. SQL execution is the
 * instrumented gap this project has recorded since the migrations (#40/#111) — the
 * decision *around* the statements is what these tests pin, and the statements
 * themselves are compile-verified by Room against the exported schema.
 */
class TranslationRepositoryImplTest {
    private val dao = FakeTranslationDao()
    private val repository = TranslationRepositoryImpl(dao)

    private fun row(
        id: Long = 0L,
        text: String = "good morning",
        favourite: Boolean = false,
        at: Long,
    ) = Translation(
        id = id,
        sourceLang = "en",
        sourceText = text,
        targetLang = "fr",
        targetText = "bonjour",
        engine = Engine.ONLINE_GOOGLE,
        favourite = favourite,
        createdAt = at,
    )

    @Test
    fun `a free tuple takes the row back whole - star, stamp and a fresh id`() =
        runTest {
            repository.restore(row(id = 7, favourite = true, at = 42))

            val restored = dao.rows.value.single()
            assertThat(restored.sourceText).isEqualTo("good morning")
            assertThat(restored.favourite).isTrue()
            assertThat(restored.createdAt).isEqualTo(42)
            assertThat(restored.id).isNotEqualTo(7) // the old id is gone with the row
        }

    /** The #179 harm: the star used to vanish because the insert reported -1. */
    @Test
    fun `an occupied tuple keeps the deleted row's star`() =
        runTest {
            dao.insert(entity(id = 1, favourite = false, at = 99))

            repository.restore(row(favourite = true, at = 42))

            assertThat(
                dao.rows.value
                    .single()
                    .favourite,
            ).isTrue()
        }

    @Test
    fun `an occupied tuple takes back the earlier stamp, not the occupant's`() =
        runTest {
            dao.insert(entity(id = 1, at = 99))

            repository.restore(row(at = 42))

            assertThat(
                dao.rows.value
                    .single()
                    .createdAt,
            ).isEqualTo(42)
        }

    /**
     * The other direction, which is the one [MigrationOneToTwo]'s comment warns
     * about: restoring an UNSTARRED row must not clear the occupant's own star.
     * A merge that only carries the star one way is still data loss.
     */
    @Test
    fun `an occupied tuple never loses the occupant's own star`() =
        runTest {
            dao.insert(entity(id = 1, favourite = true, at = 99))

            repository.restore(row(favourite = false, at = 42))

            assertThat(
                dao.rows.value
                    .single()
                    .favourite,
            ).isTrue()
        }

    @Test
    fun `an occupied tuple is merged, never duplicated - the row keeps its id`() =
        runTest {
            dao.insert(entity(id = 1, at = 99))

            repository.restore(row(at = 42))

            assertThat(dao.rows.value).hasSize(1)
            assertThat(
                dao.rows.value
                    .single()
                    .id,
            ).isEqualTo(1)
        }

    /**
     * Merge FIRST, insert only on a free tuple. Insert-first would need the -1 to
     * route it to the merge, and an occupant deleted in between would leave the
     * merge matching nothing — #179 all over again. Pinning the order pins that.
     */
    @Test
    fun `the occupied path costs one statement - no insert is attempted`() =
        runTest {
            dao.insert(entity(id = 1, at = 99))
            dao.calls.clear()

            repository.restore(row(at = 42))

            assertThat(dao.calls).containsExactly("merge")
        }

    @Test
    fun `the free path merges nothing and then inserts`() =
        runTest {
            repository.restore(row(at = 42))

            assertThat(dao.calls).containsExactly("merge", "insert").inOrder()
        }

    /** C-8 stores the source text normalized, so the merge must match on it too. */
    @Test
    fun `an un-normalized source text still finds its occupant`() =
        runTest {
            dao.insert(entity(id = 1, text = "good morning", at = 99))

            repository.restore(row(text = "  good   morning ", favourite = true, at = 42))

            assertThat(dao.rows.value).hasSize(1)
            assertThat(
                dao.rows.value
                    .single()
                    .favourite,
            ).isTrue()
        }

    private fun entity(
        id: Long,
        text: String = "good morning",
        favourite: Boolean = false,
        at: Long,
    ) = TranslationEntity(
        id = id,
        sourceLang = "en",
        sourceText = text,
        targetLang = "fr",
        targetText = "bonjour",
        engine = Engine.ONLINE_GOOGLE.name,
        favourite = favourite,
        createdAt = at,
    )
}

/**
 * In-memory DAO with the conflict semantics the real one declares: IGNORE over the
 * UNIQUE C-8 index, and the undo-merge as one statement returning rows-changed.
 */
private class FakeTranslationDao : TranslationDao {
    val rows = MutableStateFlow<List<TranslationEntity>>(emptyList())
    val calls = mutableListOf<String>()
    private var nextId = 100L

    override suspend fun insert(entity: TranslationEntity): Long {
        calls += "insert"
        if (rows.value.any { it.sameTupleAs(entity) }) return -1L
        val id = if (entity.id != 0L) entity.id else nextId++
        rows.value = rows.value + entity.copy(id = id)
        return id
    }

    override suspend fun mergeIntoTuple(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
        engine: String,
        favourite: Boolean,
        createdAt: Long,
    ): Int {
        calls += "merge"
        var changed = 0
        rows.value =
            rows.value.map { row ->
                val hit =
                    row.sourceText == sourceText && row.sourceLang == sourceLang &&
                        row.targetLang == targetLang && row.engine == engine
                if (!hit) {
                    row
                } else {
                    changed++
                    row.copy(
                        favourite = maxOf(row.favourite.toInt(), favourite.toInt()) == 1,
                        createdAt = minOf(row.createdAt, createdAt),
                    )
                }
            }
        return changed
    }

    override suspend fun savedCountUsing(languageId: String): Int {
        calls += "savedCountUsing"
        return rows.value.count {
            it.favourite && (it.sourceLang == languageId || it.targetLang == languageId)
        }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun setFavourite(
        id: Long,
        favourite: Boolean,
    ) {
        rows.value = rows.value.map { if (it.id == id) it.copy(favourite = favourite) else it }
    }

    override suspend fun cached(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
        engine: String,
    ): TranslationEntity? =
        rows.value.lastOrNull {
            it.sourceText == sourceText && it.sourceLang == sourceLang &&
                it.targetLang == targetLang && it.engine == engine
        }

    override suspend fun cachedAny(
        sourceText: String,
        sourceLang: String,
        targetLang: String,
    ): TranslationEntity? =
        rows.value
            .filter {
                it.sourceText == sourceText && it.sourceLang == sourceLang && it.targetLang == targetLang
            }.maxByOrNull(TranslationEntity::createdAt)

    override fun history(): Flow<List<TranslationEntity>> =
        rows.map { all -> all.sortedByDescending(TranslationEntity::createdAt) }

    override fun recent(limit: Int): Flow<List<TranslationEntity>> =
        rows.map { all -> all.sortedByDescending(TranslationEntity::createdAt).take(limit) }

    override fun favourites(): Flow<List<TranslationEntity>> =
        rows.map { all -> all.filter(TranslationEntity::favourite) }

    private fun TranslationEntity.sameTupleAs(other: TranslationEntity) =
        sourceText == other.sourceText && sourceLang == other.sourceLang &&
            targetLang == other.targetLang && engine == other.engine

    private fun Boolean.toInt() = if (this) 1 else 0
}
