package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Toggleable connectivity fake — tests drive [state] to simulate loss/return. */
class FakeConnectivityMonitor(
    initiallyOnline: Boolean = true,
) : ConnectivityMonitor {
    val state: MutableStateFlow<Boolean> = MutableStateFlow(initiallyOnline)

    override val online: Flow<Boolean> get() = state

    /**
     * The failure to throw from [isOnline], or null (issues #248 / #280).
     *
     * The exact twin of [meteredFailure], for the same reason. `isOnline()` is a
     * synchronous binder call into `ConnectivityManager` in production, and a
     * binder call can fail. #248 put an `isOnline()` read inside `DownloadGate`'s
     * metered try/catch — so "what happens when the connectivity state cannot be
     * read" became a question a test must be able to ask, exactly as
     * [meteredFailure] made the unreadable-meter case askable. The #280
     * cross-model lens proved the answer ("ask, never assume") by a throwaway
     * experiment; this hook is what turns that into a committed regression.
     */
    var onlineFailure: Throwable? = null

    override fun isOnline(): Boolean {
        onlineFailure?.let { throw it }
        return state.value
    }

    /** Tests flip this to simulate a mobile-data network (issue #90 gate). */
    var metered: Boolean = false

    /**
     * The failure to throw from [isMetered], or null (issue #238).
     *
     * `isMetered()` is a synchronous binder call into `ConnectivityManager` in
     * production, and a binder call can fail. Until this hook existed the gate's
     * whole matrix was written against a probe that always answered, so "what
     * happens when it cannot answer" was not a question any test could ask.
     */
    var meteredFailure: Throwable? = null

    override fun isMetered(): Boolean {
        meteredFailure?.let { throw it }
        return metered
    }
}
