package com.codeboxlk.tranzlate.core.model

/** Subscription tier (TEST_A11Y_CONTRACT §1.3). */
enum class Tier {
    FREE,
    PLUS,
    PREMIUM,
}

/**
 * Entitlement from the Access brain (DATA_MODEL :48):
 * `Loading | Free | Paid(tier: PLUS | PREMIUM)`.
 *
 * Gating always waits for a resolved (non-[Loading]) value — the FeatureAccess
 * Loading-gate rule (EDGE_CASES §1: never decide on stale data).
 */
sealed interface Entitlement {
    data object Loading : Entitlement

    data object Free : Entitlement

    data class Paid(
        val tier: Tier,
    ) : Entitlement {
        init {
            require(tier != Tier.FREE) { "Paid entitlement requires PLUS or PREMIUM" }
        }
    }
}
