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
 * ### Experiment status
 * **The on-device directory fact is NOT yet re-verified, deliberately.**
 * [packsBytes] walks the path research E3 measured on 2026-07-30
 * (`docs/research/issue-90-offline-download-lifecycle.md`). If ML Kit has
 * renamed or moved that store since, every device returns null and the meter
 * degrades to free-space-only — which is the honest outcome, not a wrong one.
 *
 * Experiment E-S1 (download a pack, walk, assert the sum exceeds zero; then
 * simulate the directory absent) is what settles it, and it is a **merge gate
 * on PR-15**, not on this PR — re-ruled by the owner on 2026-08-01 and recorded
 * in `docs/plan/issue-130-language-rev3.md`. The reasoning: this file defines a
 * function nothing calls, while PR-15 draws the number a user reads. An
 * experiment protects a claim, and the claim is the meter's.
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
