package com.codeboxlk.tranzlate.domain.access

import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Tier

/**
 * ACCESS BRAIN ask-surface (TEST_A11Y_CONTRACT §1.3; C-9 [ModeId] naming applied).
 * Loading-gate rule (DATA_MODEL :48): callers gate on a *resolved* entitlement —
 * they never decide on stale/loading data.
 */
interface FeatureAccess {
    val tier: Tier

    fun isEngineAllowed(mode: ModeId): Boolean

    fun isPaid(): Boolean
}
