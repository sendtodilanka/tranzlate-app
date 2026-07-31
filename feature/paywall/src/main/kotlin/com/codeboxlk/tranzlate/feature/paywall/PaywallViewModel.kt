package com.codeboxlk.tranzlate.feature.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.PlanPrice
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
import com.codeboxlk.tranzlate.domain.access.PurchaseCancelledException
import com.codeboxlk.tranzlate.domain.access.PurchaseFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ENTITLEMENT_SUBSCRIBE_TIMEOUT_MS = 5_000L

/**
 * BUSINESS_MODEL §3 billing options — one tier, three periods.
 *
 * These offering ids ARE the store contract: `:app/src/prod` feeds them straight
 * to the billing gateway, which looks each one up as a Qonversion **product
 * identifier**. A plan whose id has no matching dashboard product fails with
 * `ProductUnavailable` — visibly, never silently.
 */
enum class PaywallPlan(
    val offeringId: String,
) {
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly"),
}

/** One-shot UI events — the screen shows these as snackbars. */
enum class PaywallEvent {
    PURCHASE_FAILED,
    RESTORE_FAILED,
    RESTORED_FREE,

    /** Terms/Privacy could not be opened — no URL yet, or no browser on the device. */
    LINK_UNAVAILABLE,
}

/**
 * Play-policy required legal links, remote-served (`TermsAndCondition` /
 * `PrivacyPolicy`). Blank = not fetched yet — the screen must say so rather than
 * open `about:blank` (EDGE_CASES no-dead-end).
 */
data class LegalLinks(
    val termsUrl: String = "",
    val privacyUrl: String = "",
)

/** Screens ASK (PurchaseFlow · FeatureAccess); the brains do the work. */
@HiltViewModel
class PaywallViewModel
    @Inject
    constructor(
        private val purchaseFlow: PurchaseFlow,
        featureAccess: FeatureAccess,
        private val remoteConfig: RemoteConfigSource,
    ) : ViewModel() {
        private val _selected = MutableStateFlow(PaywallPlan.YEARLY) // §4: Yearly pre-selected
        val selected: StateFlow<PaywallPlan> = _selected.asStateFlow()

        /**
         * Seeded SYNCHRONOUSLY from whatever config already holds (a returning
         * user's activated fetch answers instantly), then refreshed once the first
         * fetch settles so a cold install gets its links without a restart.
         */
        private val _legalLinks =
            MutableStateFlow(
                LegalLinks(remoteConfig.termsUrl(), remoteConfig.privacyPolicyUrl()),
            )
        val legalLinks: StateFlow<LegalLinks> = _legalLinks.asStateFlow()

        private val _purchasing = MutableStateFlow(false)
        val purchasing: StateFlow<Boolean> = _purchasing.asStateFlow()

        private val _events = MutableSharedFlow<PaywallEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<PaywallEvent> = _events.asSharedFlow()

        init {
            viewModelScope.launch {
                remoteConfig.awaitFirstFetch()
                _legalLinks.value = LegalLinks(remoteConfig.termsUrl(), remoteConfig.privacyPolicyUrl())
            }
        }

        /**
         * The screen reports back when the browser did not actually open. A
         * missing URL and a device with no browser both land here as one honest
         * message — never a silent no-op on a Play-policy-required link.
         */
        fun onLegalLinkUnavailable() {
            _events.tryEmit(PaywallEvent.LINK_UNAVAILABLE)
        }

        /**
         * The store's own prices, keyed by offering id. Empty until it answers —
         * the screen renders that absence rather than filling it in, because a
         * price we have not been told is not a price we may print.
         */
        val prices: StateFlow<Map<String, PlanPrice>> =
            purchaseFlow.prices.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ENTITLEMENT_SUBSCRIBE_TIMEOUT_MS),
                emptyMap(),
            )

        /** PRO auto-dismisses the paywall (already-subscribed or just purchased). */
        val isPro: StateFlow<Boolean> =
            featureAccess.entitlement
                .map { it is Entitlement.Paid }
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(ENTITLEMENT_SUBSCRIBE_TIMEOUT_MS),
                    false,
                )

        fun select(plan: PaywallPlan) {
            _selected.value = plan
        }

        fun purchase() {
            // Double-tap guard: the flag flips BEFORE the launch — set inside the
            // coroutine, two rapid taps both pass the check (caught by test).
            if (_purchasing.value) return
            _purchasing.value = true
            viewModelScope.launch {
                val result = purchaseFlow.purchase(_selected.value.offeringId)
                _purchasing.value = false
                // Success(Free) is NOT a purchase — an unconfigured gateway lands
                // here; never fake success. The one silent case is the user's own
                // dismissal of the store sheet: they know what they did, and an
                // error toast on top of it reads as a broken paywall.
                val cancelledByUser =
                    result is AppResult.Failure && result.error is PurchaseCancelledException
                if (!cancelledByUser &&
                    (result !is AppResult.Success || result.value !is Entitlement.Paid)
                ) {
                    _events.tryEmit(PaywallEvent.PURCHASE_FAILED)
                }
            }
        }

        fun restore() {
            if (_purchasing.value) return
            _purchasing.value = true
            viewModelScope.launch {
                val result = purchaseFlow.restore()
                _purchasing.value = false
                when {
                    result !is AppResult.Success -> _events.tryEmit(PaywallEvent.RESTORE_FAILED)
                    result.value !is Entitlement.Paid -> _events.tryEmit(PaywallEvent.RESTORED_FREE)
                    else -> Unit // entitlement flow flips isPro → screen dismisses
                }
            }
        }
    }
