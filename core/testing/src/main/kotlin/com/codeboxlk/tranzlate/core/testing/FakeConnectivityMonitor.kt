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
}
