package com.codeboxlk.subscription

import com.google.common.truth.Truth.assertThat
import com.qonversion.android.sdk.dto.eligibility.QIntroEligibilityStatus
import org.junit.Test

/**
 * The one SDK-facing mapping a JVM test CAN reach: [QIntroEligibilityStatus] is
 * a plain enum in the shipped AAR, so the 1:1 translation at the seam is pinned
 * here against the real constants — no store, no mocking.
 */
class ApiEligibilityMappingTest {
    @Test
    fun `each provider status maps to its own eligibility`() {
        assertThat(QIntroEligibilityStatus.Eligible.toApiEligibility())
            .isEqualTo(ApiEligibility.ELIGIBLE)
        assertThat(QIntroEligibilityStatus.Ineligible.toApiEligibility())
            .isEqualTo(ApiEligibility.INELIGIBLE)
        assertThat(QIntroEligibilityStatus.Unknown.toApiEligibility())
            .isEqualTo(ApiEligibility.UNKNOWN)
        assertThat(QIntroEligibilityStatus.NonIntroOrTrialProduct.toApiEligibility())
            .isEqualTo(ApiEligibility.NON_INTRO)
    }

    /**
     * The trial grant keys on ELIGIBLE alone, so the property that matters most
     * is stated on the WHOLE enum: exactly one provider constant may reach it.
     * A new SDK status added upstream lands here as a compile error (exhaustive
     * `when`) — and any remap of an existing one lands as a red bar.
     */
    @Test
    fun `only the provider's Eligible reaches ELIGIBLE`() {
        val granting =
            QIntroEligibilityStatus.entries.filter {
                it.toApiEligibility() == ApiEligibility.ELIGIBLE
            }

        assertThat(granting).containsExactly(QIntroEligibilityStatus.Eligible)
    }
}
