package com.codeboxlk.tranzlate.feature.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.AppResult
import com.codeboxlk.tranzlate.core.model.Entitlement
import com.codeboxlk.tranzlate.domain.access.FeatureAccess
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
 * BUSINESS_MODEL §3 billing options — one tier, three periods. The offering ids
 * are the contract with the store side; the Qonversion batch maps them to real
 * offerings (until then the NoOp gateway answers and every purchase surfaces an
 * HONEST failure).
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
}

/** Screens ASK (PurchaseFlow · FeatureAccess); the brains do the work. */
@HiltViewModel
class PaywallViewModel
    @Inject
    constructor(
        private val purchaseFlow: PurchaseFlow,
        featureAccess: FeatureAccess,
    ) : ViewModel() {
        private val _selected = MutableStateFlow(PaywallPlan.YEARLY) // §4: Yearly pre-selected
        val selected: StateFlow<PaywallPlan> = _selected.asStateFlow()

        private val _purchasing = MutableStateFlow(false)
        val purchasing: StateFlow<Boolean> = _purchasing.asStateFlow()

        private val _events = MutableSharedFlow<PaywallEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<PaywallEvent> = _events.asSharedFlow()

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
                // Success(Free) is NOT a purchase — the NoOp gateway (and a
                // cancelled store dialog later) land here; never fake success.
                if (result !is AppResult.Success || result.value !is Entitlement.Paid) {
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
