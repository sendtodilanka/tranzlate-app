package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.domain.access.FeatureAccess

/**
 * Deterministic Access fake (TEST_A11Y_CONTRACT §1.3 matrix): all tiers see all
 * engines (NLP35 is usage-limited for FREE via UsagePolicy, not access-blocked);
 * isPaid = tier != FREE.
 */
class FakeFeatureAccess(
    override var tier: Tier = Tier.FREE,
) : FeatureAccess {
    override fun isEngineAllowed(mode: ModeId): Boolean = true

    override fun isPaid(): Boolean = tier != Tier.FREE
}
