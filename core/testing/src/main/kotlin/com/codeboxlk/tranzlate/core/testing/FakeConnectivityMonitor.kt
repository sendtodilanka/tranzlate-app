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

    override fun isOnline(): Boolean = state.value

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
