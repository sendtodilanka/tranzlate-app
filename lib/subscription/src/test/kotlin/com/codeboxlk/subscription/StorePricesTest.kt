package com.codeboxlk.subscription

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The blank-price rule, tested where it lives.
 *
 * This module had no test source set at all until a review round ran the
 * experiment: it deleted the filter these tests defend, and the entire project
 * suite stayed green. The fix was not a better assertion somewhere else — it was
 * that the rule now has a name, a home, and a test that fails when it is
 * removed. Deleting the `filter` in [storePricesFrom] must turn
 * `a product the store priced at nothing is not published` red.
 */
class StorePricesTest {
    @Test
    fun `a product the store priced at nothing is not published`() {
        val prices =
            storePricesFrom(
                listOf(
                    facts("yearly", price = "Rs 10,500.00"),
                    facts("monthly", price = ""),
                    facts("weekly", price = null),
                ),
            )

        // Not "the blank ones are empty strings" — they must be ABSENT, because
        // the host asks this map whether it may charge, and an entry is a yes.
        assertThat(prices.products.keys).containsExactly("yearly")
        assertThat(prices.products["monthly"]).isNull()
        assertThat(prices.products["weekly"]).isNull()
    }

    @Test
    fun `whitespace is not a price either`() {
        val prices = storePricesFrom(listOf(facts("yearly", price = "   ")))

        assertThat(prices.products).isEmpty()
    }

    @Test
    fun `a real price survives with its trial intact`() {
        val prices =
            storePricesFrom(
                listOf(facts("yearly", price = "€49,99", trialDays = 7, hasTrial = true)),
            )

        val yearly = prices.products.getValue("yearly")
        assertThat(yearly.price).isEqualTo("€49,99")
        assertThat(yearly.trialDays).isEqualTo(7)
        assertThat(yearly.hasTrial).isTrue()
    }

    /**
     * A trial the store expresses in months has no day count we may state, but it
     * is still a trial — the two facts travel separately on purpose.
     */
    @Test
    fun `a trial with no exact day count still reports that it exists`() {
        val prices =
            storePricesFrom(
                listOf(facts("yearly", price = "€49,99", trialDays = null, hasTrial = true)),
            )

        val yearly = prices.products.getValue("yearly")
        assertThat(yearly.hasTrial).isTrue()
        assertThat(yearly.trialDays).isNull()
    }

    /** An answer with nothing purchasable in it is still an ANSWER, not a failure. */
    @Test
    fun `a store that priced nothing is Known and empty, never Unavailable`() {
        val prices = storePricesFrom(listOf(facts("yearly", price = null)))

        assertThat(prices).isInstanceOf(StorePrices.Known::class.java)
        assertThat(prices.products).isEmpty()
    }

    private fun facts(
        offeringId: String,
        price: String?,
        trialDays: Int? = null,
        hasTrial: Boolean = false,
    ) = StoreProductFacts(offeringId, price, trialDays, hasTrial)
}
