package com.codeboxlk.tranzlate.core.access

import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ACCESS BRAIN (plan §2 — the one home for entitlement gating).
 *
 * Phase-2 scope: adapt `:lib:subscription` `SubscriptionGateway.entitlement`
 * into [Tier] with the Loading-gate rule (DATA_MODEL :48 — gating always waits
 * for a resolved, non-Loading value).
 */
@Singleton
class RealFeatureAccess @Inject constructor() : FeatureAccess {

    // TODO(#4-brains): real implementation — placeholder returns Error(ENGINE) / safe defaults.
    // Safe defaults: FREE tier, every mode visible (contract §1.3 matrix), not paid.
    override val tier: Tier = Tier.FREE

    override fun isEngineAllowed(mode: ModeId): Boolean = true

    override fun isPaid(): Boolean = false
}
