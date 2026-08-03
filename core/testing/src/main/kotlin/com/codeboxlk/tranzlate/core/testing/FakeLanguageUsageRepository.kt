package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.domain.repository.LanguageUsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory translation-use stamps (issue #122). Recording spy first: the R6
 * tests assert the exact (id, role, millis) tuples the translate flow wrote —
 * and, just as often, that NOTHING was written. [failStamps] stands in for a
 * dying disk, proving a failed stamp is the stamp's problem alone.
 */
class FakeLanguageUsageRepository(
    var failStamps: Boolean = false,
) : LanguageUsageRepository {
    /**
     * The failure to throw from [stampUse], or null. A `Throwable`, not an
     * `Exception`, and that is the whole reason it exists (issue #236).
     *
     * [failStamps] can only ever produce the `IllegalStateException` that `check`
     * throws, so every test written through it passes under a `catch (Exception)`
     * as happily as under a `catch (Throwable)` — **the fixture could not express
     * the failure class the guard was widened for.** Room's statements end in
     * `native` methods and a JNI link failure raises `UnsatisfiedLinkError`, a
     * `LinkageError`, so an `Error`: reachable only through a hook typed this wide.
     */
    var failWith: Throwable? = null

    data class Stamp(
        val languageId: String,
        val role: LanguageRole,
        val atMillis: Long,
    )

    private val state = MutableStateFlow<List<Stamp>>(emptyList())

    /** Every accepted stamp, in write order. */
    val stamps: List<Stamp> get() = state.value

    override suspend fun stampUse(
        languageId: String,
        role: LanguageRole,
        atMillis: Long,
    ) {
        failWith?.let { throw it }
        check(!failStamps) { "stamp write failed (test-forced)" }
        state.value = state.value + Stamp(languageId, role, atMillis)
    }

    override fun lastUsed(role: LanguageRole): Flow<Map<String, Long>> =
        state.map { all ->
            all
                .filter { it.role == role }
                .associate { it.languageId to it.atMillis }
        }
}
