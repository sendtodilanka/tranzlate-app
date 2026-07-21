package com.codeboxlk.tranzlate.core.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connectivity axis input for Availability resolvers (EDGE_CASES §2/§6 — the
 * resolver READS this; it never re-implements the check).
 */
interface ConnectivityMonitor {
    val isOnline: Flow<Boolean>
}

/**
 * TODO(#4-brains): real implementation — placeholder returns safe defaults.
 * Real impl = ConnectivityManager NetworkCallback (default-network based),
 * callbackFlow + distinctUntilChanged; lands with the brains phase.
 */
@Singleton
class StubConnectivityMonitor
    @Inject
    constructor() : ConnectivityMonitor {
        override val isOnline: Flow<Boolean> = flowOf(true)
    }
