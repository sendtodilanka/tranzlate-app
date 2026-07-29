package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.Tier
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Deterministic Access fake (TEST_A11Y_CONTRACT §1.3 matrix): all tiers see all
 * engines (AI quality is usage-limited for FREE via UsagePolicy, not
 * access-blocked). Starts resolved — set [state] to [Entitlement.Loading] to
 * exercise the Loading-gate.
 */
class FakeFeatureAccess(
    tier: Tier = Tier.FREE,
) : FeatureAccess {
    /** Mutable so tests can drive Loading → resolved transitions. */
    val state: MutableStateFlow<Entitlement> =
        MutableStateFlow(if (tier == Tier.FREE) Entitlement.Free else Entitlement.Paid(Tier.PRO))

    override val entitlement: MutableStateFlow<Entitlement> get() = state

    override suspend fun awaitResolved(): Entitlement = state.first { it !is Entitlement.Loading }

    /** Matrix default: every tier sees every engine. Hook for the NotEntitled contract test. */
    var engineAllowed: Boolean = true

    override fun isEngineAllowed(mode: ModeId): Boolean = engineAllowed
}
