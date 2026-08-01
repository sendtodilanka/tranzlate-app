package com.codeboxlk.tranzlate.domain.repository

import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import kotlinx.coroutines.flow.Flow

/**
 * Language catalog contract — bundled static list (180+) intersected with MLKit
 * runtime capability (spec 02 §4.1/§4.2); Picker and Offline-manager are separate
 * UX surfaces over the same catalog (D-E2).
 */
interface LanguageRepository {
    fun languages(): Flow<List<Language>>

    /**
     * When each language was last CHOSEN for [role] — what a picker's recents
     * section is built from, keyed by catalog id.
     *
     * Separate from [Language.lastUsedAt], which the catalog above overlays with
     * the MERGED view (source ∪ target ∪ the pre-split legacy key) the shipped
     * 15a picker renders. 16a's section header reads "Recently used as target",
     * so it may not be served from that union: a language the user only ever
     * picked as a source would appear under a header claiming it was a target —
     * the screen contradicting itself, which is the defect class this epic
     * exists to remove.
     *
     * No default implementation on purpose. A fake that inherited one would
     * serve an empty map while looking wired, which is exactly the silent-fake
     * defect issue #123.4 closed.
     */
    fun recentSelections(role: LanguageRole): Flow<Map<String, Long>>

    /**
     * Stamps a picker CHOICE for the Recent section, per [role] since issue
     * #130 rev.3 (16a shows source-recents and target-recents separately).
     * Selection only — actual translate USE is [LanguageUsageRepository]'s
     * job, and this method must never write there (ruling R6).
     */
    suspend fun setLastUsed(
        languageId: String,
        role: LanguageRole,
        atMillis: Long,
    )
}
