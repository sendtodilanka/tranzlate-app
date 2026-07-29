package com.codeboxlk.tranzlate.core.model

/** Subscription tier — two-tier model (D-2 rev.2 · BUSINESS_MODEL §2). */
enum class Tier {
    FREE,
    PRO,
}

/**
 * Entitlement from the Access brain (DATA_MODEL :48):
 * `Loading | Free | Paid(tier: PRO)`.
 *
 * Gating always waits for a resolved (non-[Loading]) value — the FeatureAccess
 * Loading-gate rule (EDGE_CASES §1: never decide on stale data).
 */
sealed interface Entitlement {
    data object Loading : Entitlement

    data object Free : Entitlement

    data class Paid(
        val tier: Tier = Tier.PRO,
    ) : Entitlement {
        init {
            require(tier == Tier.PRO) { "Paid entitlement is PRO — FREE is not a paid tier" }
        }
    }
}
