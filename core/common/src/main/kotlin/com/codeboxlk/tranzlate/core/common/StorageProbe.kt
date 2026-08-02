package com.codeboxlk.tranzlate.core.common

import java.io.File

/**
 * Storage snapshot seam. Two consumers, one home:
 *
 * 1. **Model-download pre-flight** (issue #90 ruling) — [freeBytes]: refuse
 *    BEFORE enqueue when the disk can't hold a model; a partial download plus
 *    a generic failure is a dead end.
 * 2. **Manage-packs aggregate meter** (issue #130 ruling U-5, drawn by PR-15)
 *    — [totalBytes] + [packsBytes]. Per-pack byte counts stay impossible
 *    (designer-brief rule: ML Kit exposes no per-model size API); the
 *    AGGREGATE store size is verified possible by summing the on-disk store.
 *
 * ### Degrade contract (U-5, binding on every implementation)
 * [packsBytes] returns `null` when the model store directory is absent or has
 * been renamed by an ML Kit update. `null` means "unknown" and the meter must
 * degrade to free-space-only — an HONEST degrade. Implementations must never
 * substitute `0` for "couldn't find the store": zero is a factual claim
 * ("no packs installed") that a missing dir cannot support.
 *
 * ### Experiment status — E-S1 RAN AND PASSED (2026-08-02, PR-15)
 * [packsBytes] walks the path research E3 measured on 2026-07-30
 * (`docs/research/issue-90-offline-download-lifecycle.md`), and experiment E-S1
 * re-measured it on `emulator-5554` the day the meter was built: downloading one
 * af↔en pack created exactly that directory and put 30 files totalling
 * 44,169,505 bytes in it, and renaming the store made the walk find nothing
 * rather than zero. Full record in
 * `docs/research/issue-130-e-s1-storage-walk.md`.
 *
 * ML Kit still does not document the name, so the degrade above is not
 * hypothetical insurance — it is what a future rename would land on, and it is
 * unit-tested (`StorageProbeWalkTest`) and consumer-tested
 * (`OfflineLibraryMeterTest`) rather than reasoned about.
 *
 * **One finding worth carrying:** a fresh install has no model store either — it
 * does not exist until the first download — so `null` on first run is ordinary
 * rather than a fault. The meter therefore decides on the pack COUNT first and
 * only reaches the degrade with packs installed. See `offlineLibraryMeter`.
 *
 * Platform-backed implementation is prod-side wiring, like
 * [ConnectivityMonitor].
 */
interface StorageProbe {
    /** Free bytes on the volume that holds the offline model store. */
    fun freeBytes(): Long

    /**
     * Total bytes on that same volume — the meter's denominator. Same-volume
     * as [freeBytes] so used/free/total describe one disk, not a mix.
     */
    fun totalBytes(): Long

    /**
     * Aggregate on-disk size of the offline translate-model store, or `null`
     * when the store directory is absent/renamed (degrade contract above).
     * Implementations walk the store off the caller's thread
     * (`Dispatchers.IO`); callers may invoke from any dispatcher.
     */
    suspend fun packsBytes(): Long?
}

/**
 * Sums every regular file under [storeDir], recursively — the walk behind
 * [StorageProbe.packsBytes], extracted pure so the sum + degrade contract is
 * unit-testable without Android.
 *
 * - Absent path, or a path that is not a directory (e.g. renamed store
 *   replaced by a stray file) → `null` — the honest degrade, never 0-as-fact.
 * - Existing but empty store → `0`, a real "no packs occupy disk".
 * - Directories contribute no bytes themselves; only file lengths count.
 */
fun packsBytesOf(storeDir: File): Long? {
    if (!storeDir.isDirectory) return null
    return storeDir
        .walkTopDown()
        .filter(File::isFile)
        .sumOf(File::length)
}
