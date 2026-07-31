package com.codeboxlk.subscription

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The trial rules, tested where they live (register M7/M8/M11).
 *
 * Before [productFacts] existed these decisions sat inline in a coroutine, and
 * a review round proved what that was worth: inverting the eligibility check,
 * or dropping the week→days conversion, left the whole suite green. Each test
 * here is the red bar for one of those edits.
 */
class ProductFactsTest {
    // ------------------------------------------------------------ the grant rule

    /** M7 in reverse: only ELIGIBLE grants; this is the one row where it does. */
    @Test
    fun `an eligible account with a one-week trial gets exactly seven days`() {
        val facts = productFacts(product(trial = weeks(1)), ApiEligibility.ELIGIBLE)

        assertThat(facts.hasTrial).isTrue()
        assertThat(facts.trialDays).isEqualTo(7)
    }

    /** M7: treating INELIGIBLE as eligible must turn this red. */
    @Test
    fun `an ineligible account gets no trial even when the product carries one`() {
        val facts = productFacts(product(trial = weeks(1)), ApiEligibility.INELIGIBLE)

        assertThat(facts.hasTrial).isFalse()
        assertThat(facts.trialDays).isNull()
    }

    @Test
    fun `unknown eligibility grants nothing`() {
        val facts = productFacts(product(trial = weeks(1)), ApiEligibility.UNKNOWN)

        assertThat(facts.hasTrial).isFalse()
        assertThat(facts.trialDays).isNull()
    }

    @Test
    fun `a non-intro product grants nothing`() {
        val facts = productFacts(product(trial = weeks(1)), ApiEligibility.NON_INTRO)

        assertThat(facts.hasTrial).isFalse()
        assertThat(facts.trialDays).isNull()
    }

    @Test
    fun `no eligibility answer at all grants nothing`() {
        val facts = productFacts(product(trial = weeks(1)), eligibility = null)

        assertThat(facts.hasTrial).isFalse()
        assertThat(facts.trialDays).isNull()
    }

    /** M11: hasTrial is the grant itself, never independently true. */
    @Test
    fun `an eligible account without a trial period has no trial`() {
        val facts = productFacts(product(trial = null), ApiEligibility.ELIGIBLE)

        assertThat(facts.hasTrial).isFalse()
        assertThat(facts.trialDays).isNull()
    }

    // ------------------------------------------------------------ exact day counts

    /** M8: dropping the ×7 must turn this red. */
    @Test
    fun `two weeks convert to fourteen days`() {
        val facts = productFacts(product(trial = weeks(2)), ApiEligibility.ELIGIBLE)

        assertThat(facts.trialDays).isEqualTo(14)
    }

    @Test
    fun `day periods pass through as their own count`() {
        val facts = productFacts(product(trial = ApiPeriod(3, ApiPeriod.Unit.DAY)), ApiEligibility.ELIGIBLE)

        assertThat(facts.trialDays).isEqualTo(3)
    }

    /** A month has no exact day count we may print — but the trial still EXISTS. */
    @Test
    fun `a month-long trial has no day count but still reports that it exists`() {
        val facts = productFacts(product(trial = ApiPeriod(1, ApiPeriod.Unit.MONTH)), ApiEligibility.ELIGIBLE)

        assertThat(facts.hasTrial).isTrue()
        assertThat(facts.trialDays).isNull()
    }

    @Test
    fun `exactDays converts only units that convert without rounding`() {
        assertThat(ApiPeriod(1, ApiPeriod.Unit.DAY).exactDays()).isEqualTo(1)
        assertThat(ApiPeriod(1, ApiPeriod.Unit.WEEK).exactDays()).isEqualTo(7)
        assertThat(ApiPeriod(1, ApiPeriod.Unit.MONTH).exactDays()).isNull()
        assertThat(ApiPeriod(1, ApiPeriod.Unit.YEAR).exactDays()).isNull()
        assertThat(ApiPeriod(1, ApiPeriod.Unit.UNKNOWN).exactDays()).isNull()
    }

    // ------------------------------------------------------------ pass-throughs

    @Test
    fun `offering id and price pass through untouched`() {
        val priced = productFacts(ApiProduct("yearly", "Rs 10,500.00", null), null)
        assertThat(priced.offeringId).isEqualTo("yearly")
        assertThat(priced.price).isEqualTo("Rs 10,500.00")

        val unpriced = productFacts(ApiProduct("monthly", null, null), null)
        assertThat(unpriced.price).isNull()
    }

    private fun product(trial: ApiPeriod?) =
        ApiProduct(offeringId = "yearly", prettyPrice = "€49,99", trialPeriod = trial)

    private fun weeks(count: Int) = ApiPeriod(count, ApiPeriod.Unit.WEEK)
}
