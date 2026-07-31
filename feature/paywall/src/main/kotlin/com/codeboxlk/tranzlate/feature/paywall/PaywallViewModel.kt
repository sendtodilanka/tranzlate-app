package com.codeboxlk.tranzlate.feature.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.core.model.PlanPrices
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
        val prices: StateFlow<PlanPrices> =
            purchaseFlow.prices.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ENTITLEMENT_SUBSCRIBE_TIMEOUT_MS),
                // Loading, never Unavailable: the initial value is shown before
                // the flow has said anything, and "couldn't reach Play" is not
                // something we may claim on an attempt that has not finished.
                PlanPrices.Loading,
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

        init {
            // Every open re-asks. The first attempt happens at process start,
            // when an offline launch or one store error would otherwise leave
            // this screen unable to sell anything for the rest of the session.
            refreshPrices()
        }

        /** Retry affordance behind the "couldn't reach Play" state. */
        fun refreshPrices() {
            viewModelScope.launch { purchaseFlow.refreshPrices() }
        }

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

/**
 * **May this button charge?**
 *
 * A control that takes money must not be tappable while the amount it will take
 * is unknown. That is the rule the whole price change exists to enforce, and it
 * is a named function rather than a condition inside the Composable because a
 * review round deleted the inline version and every test still passed. An
 * invariant nothing can fail is not enforced.
 *
 * Two ways to be un-tappable, and they are different: a purchase already in
 * flight (double-tap guard), and a price the store has not given us.
 */
fun canPurchase(
    prices: PlanPrices,
    selected: PaywallPlan,
    purchasing: Boolean,
): Boolean = !purchasing && prices[selected.offeringId] != null
