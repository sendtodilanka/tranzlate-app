package com.codeboxlk.tranzlate.feature.paywall

import com.codeboxlk.tranzlate.core.model.PlanPrice
import com.codeboxlk.tranzlate.core.model.PlanPrices
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The purchase gate, tested where it lives.
 *
 * A review round deleted `&& selectedPrice != null` from the button and the
 * whole suite stayed green — the rule was defended by nothing but reading. These
 * tests are the replacement: removing the price check from [canPurchase] must
 * turn `an unpriced plan cannot be purchased` red.
 */
class CanPurchaseTest {
    @Test
    fun `a priced plan can be purchased`() {
        assertThat(canPurchase(known(PaywallPlan.YEARLY), PaywallPlan.YEARLY, purchasing = false)).isTrue()
    }

    @Test
    fun `an unpriced plan cannot be purchased`() {
        // The store answered, but not about this plan — the button must stay shut.
        assertThat(canPurchase(known(PaywallPlan.WEEKLY), PaywallPlan.YEARLY, purchasing = false)).isFalse()
    }

    @Test
    fun `nothing can be purchased while the store has not answered`() {
        assertThat(canPurchase(PlanPrices.Loading, PaywallPlan.YEARLY, purchasing = false)).isFalse()
    }

    @Test
    fun `nothing can be purchased when the store is unreachable`() {
        assertThat(canPurchase(PlanPrices.Unavailable, PaywallPlan.YEARLY, purchasing = false)).isFalse()
    }

    /** The double-tap guard is a SECOND reason to be shut, not the same one. */
    @Test
    fun `a purchase already in flight blocks even a priced plan`() {
        assertThat(canPurchase(known(PaywallPlan.YEARLY), PaywallPlan.YEARLY, purchasing = true)).isFalse()
    }

    private fun known(vararg priced: PaywallPlan) =
        PlanPrices.Known(priced.associate { it.offeringId to PlanPrice("Rs 10,500.00") })
}
