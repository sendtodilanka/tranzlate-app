package com.codeboxlk.tranzlate.core.access

import com.codeboxlk.subscription.SubscriptionGateway
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ACCESS BRAIN (plan §2 — the one home for entitlement gating).
 *
 * Adapts `:lib:subscription` [SubscriptionGateway.entitlement] into the domain
 * [Entitlement] via [toDomain] — the same mapping the purchase flow uses (A7:
 * one tier source). The Loading-gate rule lives in [awaitResolved]; this class
 * holds no state of its own.
 */
@Singleton
class RealFeatureAccess
    @Inject
    constructor(
        gateway: SubscriptionGateway,
    ) : FeatureAccess {
        override val entitlement: Flow<Entitlement> = gateway.entitlement.map { it.toDomain() }

        override suspend fun awaitResolved(): Entitlement = entitlement.first { it !is Entitlement.Loading }

        // Contract §1.3 matrix: every tier sees every engine (C-10 rev.2 — AI
        // quality is metered by the Usage brain, never hidden).
        override fun isEngineAllowed(mode: ModeId): Boolean = true
    }
