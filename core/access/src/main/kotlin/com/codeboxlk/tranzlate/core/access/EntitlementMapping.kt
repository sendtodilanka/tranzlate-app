package com.codeboxlk.tranzlate.core.access

import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.Tier

/**
 * THE one provider→domain entitlement mapping — [RealFeatureAccess] and
 * [SubscriptionPurchaseFlow] both go through here, so gating and purchase can
 * never disagree on what a provider state means (plan §PR-3, A7).
 *
 * Single-paid-tier model (D-2 rev.2): any provider `Paid` grants [Tier.PRO] —
 * the provider-side entitlement id ("pro", or a legacy "plus"/"premium" from
 * an old dashboard) names the purchase, it no longer selects a tier. There is
 * deliberately no string→tier branch left to fail open on unknown ids.
 */
internal fun com.codeboxlk.subscription.Entitlement.toDomain(): Entitlement =
    when (this) {
        com.codeboxlk.subscription.Entitlement.Loading -> Entitlement.Loading
        com.codeboxlk.subscription.Entitlement.Free -> Entitlement.Free
        is com.codeboxlk.subscription.Entitlement.Paid -> Entitlement.Paid(Tier.PRO)
    }
