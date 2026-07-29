package com.codeboxlk.tranzlate.domain.access

import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.ModeId
import kotlinx.coroutines.flow.Flow

/**
 * ACCESS BRAIN ask-surface (TEST_A11Y_CONTRACT §1.3; C-9 [ModeId] naming applied).
 *
 * Loading-gate rule (DATA_MODEL :48): [entitlement] starts at
 * [Entitlement.Loading]; anything that *decides* (metering, paywall, ad
 * suppression) calls [awaitResolved] — nothing defaults to FREE while loading.
 */
interface FeatureAccess {
    /** Hot entitlement state — `Loading | Free | Paid(PRO)`. */
    val entitlement: Flow<Entitlement>

    /** First non-[Entitlement.Loading] value — the only legal input to a gate. */
    suspend fun awaitResolved(): Entitlement

    /**
     * Engine *visibility* (contract §1.3 matrix): every tier sees every engine —
     * AI quality is metered by the Usage brain, not hidden (C-10 rev.2).
     */
    fun isEngineAllowed(mode: ModeId): Boolean
}
