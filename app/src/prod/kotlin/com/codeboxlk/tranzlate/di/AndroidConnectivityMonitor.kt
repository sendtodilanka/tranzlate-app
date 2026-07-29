package com.codeboxlk.tranzlate.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * `NetworkCallback`-backed monitor (prod-side platform wiring, plan §6.2).
 * "Online" = a network validated for INTERNET capability — captive portals
 * and connected-but-dead Wi-Fi count as offline, which is exactly what the
 * waterfall's pre-flight wants.
 */
class AndroidConnectivityMonitor(
    context: Context,
) : ConnectivityMonitor {
    private val manager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val online: Flow<Boolean> =
        callbackFlow {
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(isOnline())
                    }

                    override fun onLost(network: Network) {
                        trySend(isOnline())
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        trySend(isOnline())
                    }
                }
            trySend(isOnline())
            manager.registerNetworkCallback(
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.conflate().distinctUntilChanged()

    override fun isOnline(): Boolean {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
