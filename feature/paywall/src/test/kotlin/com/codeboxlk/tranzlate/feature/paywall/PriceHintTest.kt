package com.codeboxlk.tranzlate.feature.paywall

import com.codeboxlk.tranzlate.core.model.PlanPrice
import com.codeboxlk.tranzlate.core.model.PlanPrices
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The three-way hint, tested where it lives — the FULL table, because the bug
 * this function replaces was a collapsed row: `Known(empty)` and plan-missing
 * rendered as "Couldn't reach Google Play", which is false (Play answered) and
 * dangles a retry that can never succeed. Swapping the PLAN_UNAVAILABLE and
 * STORE_UNREACHABLE arms (mutation M12) must turn these red.
 */
class PriceHintTest {
    @Test
    fun `Loading hints LOADING for every plan`() {
        for (plan in PaywallPlan.entries) {
            assertThat(priceHintFor(PlanPrices.Loading, plan)).isEqualTo(PriceHint.LOADING)
        }
    }

    @Test
    fun `Unavailable hints STORE_UNREACHABLE - the state retry exists for`() {
        for (plan in PaywallPlan.entries) {
            assertThat(priceHintFor(PlanPrices.Unavailable, plan))
                .isEqualTo(PriceHint.STORE_UNREACHABLE)
        }
    }

    /** The R4-B3 row: the store ANSWERED with nothing — that is not "unreachable". */
    @Test
    fun `Known-empty hints PLAN_UNAVAILABLE - never STORE_UNREACHABLE`() {
        for (plan in PaywallPlan.entries) {
            assertThat(priceHintFor(PlanPrices.Known(emptyMap()), plan))
                .isEqualTo(PriceHint.PLAN_UNAVAILABLE)
        }
    }

    @Test
    fun `a partial answer hints per SELECTED plan - missing is PLAN_UNAVAILABLE, priced is NONE`() {
        val onlyYearly =
            PlanPrices.Known(mapOf(PaywallPlan.YEARLY.offeringId to PlanPrice("Rs 10,500.00")))

        assertThat(priceHintFor(onlyYearly, PaywallPlan.YEARLY)).isEqualTo(PriceHint.NONE)
        assertThat(priceHintFor(onlyYearly, PaywallPlan.WEEKLY)).isEqualTo(PriceHint.PLAN_UNAVAILABLE)
        assertThat(priceHintFor(onlyYearly, PaywallPlan.MONTHLY)).isEqualTo(PriceHint.PLAN_UNAVAILABLE)
    }

    @Test
    fun `a fully priced answer hints NONE for every plan`() {
        val all =
            PlanPrices.Known(
                PaywallPlan.entries.associate { it.offeringId to PlanPrice("Rs 690.00") },
            )

        for (plan in PaywallPlan.entries) {
            assertThat(priceHintFor(all, plan)).isEqualTo(PriceHint.NONE)
        }
    }
}
