package com.codeboxlk.tranzlate.core.access

import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.Tier
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import com.codeboxlk.subscription.Entitlement as ProviderEntitlement

/**
 * THE one provider→domain mapping (A7): gating and purchase share it, so these
 * are the only tier semantics in the app. Single paid tier (D-2 rev.2) — every
 * provider `Paid` grants PRO regardless of the dashboard's entitlement id, so
 * no unknown-string branch exists to fail open.
 */
class EntitlementMappingTest {
    @Test
    fun `loading and free pass through unchanged`() {
        assertThat(ProviderEntitlement.Loading.toDomain()).isEqualTo(Entitlement.Loading)
        assertThat(ProviderEntitlement.Free.toDomain()).isEqualTo(Entitlement.Free)
    }

    @Test
    fun `every provider paid id grants PRO - including legacy dashboard ids`() {
        for (id in listOf("pro", "plus", "premium", "PREMIUM", "some-future-id", "")) {
            assertThat(ProviderEntitlement.Paid(id).toDomain())
                .isEqualTo(Entitlement.Paid(Tier.PRO))
        }
    }
}
