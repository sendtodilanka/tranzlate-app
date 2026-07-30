package com.codeboxlk.tranzlate.core.common

/**
 * Free-space snapshot for the model-download pre-flight (issue #90 ruling:
 * refuse BEFORE enqueue when the disk can't hold a model — a partial download
 * plus a generic failure is a dead end). Platform-backed implementation is
 * prod-side wiring, like [ConnectivityMonitor].
 */
interface StorageProbe {
    /** Free bytes on the volume that holds the offline model store. */
    fun freeBytes(): Long
}
