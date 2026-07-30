package com.codeboxlk.tranzlate.core.common

import kotlinx.coroutines.flow.Flow

/**
 * ACTIVE internet monitor (issue #61 — the owner's "network issues godak
 * balapanawa" requirement): the waterfall pre-flights online tiers against
 * this instead of discovering offline via a timeout, and UI can observe
 * [online] to react live.
 *
 * Interface lives here (like [AppClock]); the `NetworkCallback`-backed
 * implementation is prod-side wiring, the fake variant binds a toggleable
 * fake from `:core:testing`.
 */
interface ConnectivityMonitor {
    /** Hot connectivity state; starts with the current state. */
    val online: Flow<Boolean>

    /** Synchronous snapshot for pre-flight checks. */
    fun isOnline(): Boolean

    /**
     * Metered snapshot for the model-download consent gate (issue #90 ruling:
     * metered is CONSENT, not availability — the gate lives in our code, not
     * in MLKit's untested `requireWifi`).
     */
    fun isMetered(): Boolean
}
