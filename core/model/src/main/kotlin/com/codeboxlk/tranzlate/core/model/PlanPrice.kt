package com.codeboxlk.tranzlate.core.model

/**
 * What a subscription plan costs, as the STORE reports it.
 *
 * The paywall used to print its prices from string resources — `US$1.99` and
 * friends — which told every buyer outside that one currency a false number, at
 * the exact moment money changed hands. This carries the store's own figure
 * instead, and it is deliberately a `String`: Play has already formatted it for
 * the user's locale and currency, and re-formatting it here would reintroduce
 * precisely the bug being removed.
 *
 * @property formattedPrice display verbatim ("Rs 1,200.00", "€4,99").
 * @property trialDays exact trial length, when the store's unit converts without
 *   rounding. Null for a month- or year-long trial, where a day count would be
 *   an invention — [hasTrial] still reports that one exists.
 * @property hasTrial whether THIS account is still eligible for a trial at all.
 *   Eligibility is per account: someone who already spent the intro offer is not
 *   getting another, and promising them one is the same class of lie as the
 *   wrong currency.
 */
data class PlanPrice(
    val formattedPrice: String,
    val trialDays: Int? = null,
    val hasTrial: Boolean = false,
)

/**
 * What the store has told us so far — three states, not two.
 *
 * "Not asked yet" and "asked and could not reach Play" are different facts, and
 * the paywall must not print one while the other is true. Collapsing them into
 * an empty map made the screen open onto "Couldn't reach Google Play" before a
 * single call had been made — the same class of falsehood as the hardcoded
 * prices this replaced.
 */
sealed interface PlanPrices {
    /** A request is in flight, or none has been made yet. */
    data object Loading : PlanPrices

    /** The store answered. May be empty if it published nothing we asked for. */
    data class Known(
        val plans: Map<String, PlanPrice>,
    ) : PlanPrices

    /** Unreachable, or this build has no billing at all. */
    data object Unavailable : PlanPrices

    /** The price of [offeringId], or null unless the store actually named one. */
    operator fun get(offeringId: String): PlanPrice? = (this as? Known)?.plans?.get(offeringId)
}
