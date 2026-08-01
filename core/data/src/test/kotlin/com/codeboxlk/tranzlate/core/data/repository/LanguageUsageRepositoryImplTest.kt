package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.database.LanguageUsageDao
import com.codeboxlk.tranzlate.core.database.LanguageUsageEntity
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The translation-use store's own contract (issue #122): canonical ids in,
 * per-role maps out. The Room DAO is faked with upsert semantics — SQL
 * execution is the instrumented gap recorded on the migration (#40/#111).
 */
class LanguageUsageRepositoryImplTest {
    private val dao = FakeLanguageUsageDao()
    private val repository = LanguageUsageRepositoryImpl(dao)

    /**
     * Same normalisation rule as the recents write: a detect result can arrive
     * as an alternate spelling, and an un-normalised row would be kept forever
     * yet matched by nothing in the catalog.
     */
    @Test
    fun `a legacy id is canonicalised before the write`() =
        runTest {
            repository.stampUse("iw", LanguageRole.SOURCE, atMillis = 42L)

            assertThat(
                dao.rows.value
                    .single()
                    .langId,
            ).isEqualTo("he")
        }

    @Test
    fun `an unresolvable id is written through unchanged`() =
        runTest {
            repository.stampUse("zzz", LanguageRole.TARGET, atMillis = 7L)

            assertThat(
                dao.rows.value
                    .single()
                    .langId,
            ).isEqualTo("zzz")
        }

    @Test
    fun `a repeat use moves the stamp - one row per id and role`() =
        runTest {
            repository.stampUse("fr", LanguageRole.TARGET, atMillis = 10L)
            repository.stampUse("fr", LanguageRole.TARGET, atMillis = 30L)

            assertThat(repository.lastUsed(LanguageRole.TARGET).first()).containsExactly("fr", 30L)
        }

    /** The same language on both sides is TWO facts — deleting neither may shadow the other. */
    @Test
    fun `roles are independent - the same id can carry two stamps`() =
        runTest {
            repository.stampUse("en", LanguageRole.SOURCE, atMillis = 10L)
            repository.stampUse("en", LanguageRole.TARGET, atMillis = 20L)

            assertThat(repository.lastUsed(LanguageRole.SOURCE).first()).containsExactly("en", 10L)
            assertThat(repository.lastUsed(LanguageRole.TARGET).first()).containsExactly("en", 20L)
        }

    @Test
    fun `lastUsed reads live - a new stamp surfaces without recollecting`() =
        runTest {
            repository.stampUse("de", LanguageRole.SOURCE, atMillis = 1L)

            assertThat(repository.lastUsed(LanguageRole.SOURCE).first()).containsExactly("de", 1L)
            repository.stampUse("es", LanguageRole.SOURCE, atMillis = 2L)
            assertThat(repository.lastUsed(LanguageRole.SOURCE).first())
                .containsExactly("de", 1L, "es", 2L)
        }
}

/** In-memory DAO with the composite-key upsert semantics the real one declares. */
private class FakeLanguageUsageDao : LanguageUsageDao {
    val rows = MutableStateFlow<List<LanguageUsageEntity>>(emptyList())

    override suspend fun upsert(usage: LanguageUsageEntity) {
        rows.value =
            rows.value.filterNot { it.langId == usage.langId && it.role == usage.role } + usage
    }

    override fun usageFor(role: String): Flow<List<LanguageUsageEntity>> =
        rows.map { all -> all.filter { it.role == role } }
}
