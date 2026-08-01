package com.codeboxlk.tranzlate.domain.repository

import com.codeboxlk.tranzlate.core.model.LanguageRole
import kotlinx.coroutines.flow.Flow

/**
 * When each language was last PROVEN in use — one stamp per (id, role), written
 * on translation success ONLY (issue #122, rev.3 ruling R6). This is what
 * Manage packs' deletion honesty reads: "last used 3 months ago" must mean the
 * user last TRANSLATED with it then, not that they last scrolled past it.
 *
 * Distinct from the picker's recents (`LanguageRepository.setLastUsed`) on
 * purpose: recents answer "what did I recently CHOOSE" and stamp on selection;
 * this store answers "what do I actually USE" and never stamps on selection —
 * conflating the two is exactly the nudge-to-delete-an-active-pack risk the
 * ruling records.
 */
interface LanguageUsageRepository {
    /**
     * Records a use. [languageId] must be a RESOLVED id — for an auto-detect ask
     * that is the detected source, never the "auto" sentinel; the impl
     * canonicalises alternate spellings (`iw`, `fil`, `zh-CN`) before writing.
     */
    suspend fun stampUse(
        languageId: String,
        role: LanguageRole,
        atMillis: Long,
    )

    /** Live canonical-id → last-used-millis map for one [role]. */
    fun lastUsed(role: LanguageRole): Flow<Map<String, Long>>
}
