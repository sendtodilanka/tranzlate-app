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
 * **The finding worth carrying, corrected by E-S1b (2026-08-02, co-verify).** A
 * fresh install has no model store — it does not exist until the first download
 * — so `null` on first run is ordinary rather than a fault. That much held. What
 * did NOT hold is the conclusion drawn from it: that deciding on the pack COUNT
 * first keeps a first run off the free-space line. A real `pm clear` on
 * `emulator-5554` showed the count is **1, not 0**, before anything is
 * downloaded — ML Kit reports the English pivot as on device while this app's
 * store does not exist — so a first run reaches the free-space answer whichever
 * way the branches are ordered. Free space is what the card says there, and it
 * is true there. See `offlineLibraryMeter` for why the order is still count-first
 * anyway, and `docs/research/issue-130-e-s1-storage-walk.md` §E-S1b.
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
 * ML Kit's scratch directory, a sibling of the per-pair pack directories at the
 * store root — observed as `temp/af_en/` in E-S1, empty once the download had
 * settled.
 *
 * It is excluded from [packsBytesOf] because an interrupted download leaves
 * partial model files there and nothing is documented to clean them up, so they
 * would be counted as pack bytes for as long as the install lives. Measured in
 * co-verify (E-S1c): one real 14,779,264-byte model file dropped into
 * `temp/af_en/` moved the card from **44 MB to 59 MB** — a 34% overstatement —
 * while the catalogue still, correctly, said "2 of 59 packs".
 *
 * **The limit of this, stated rather than implied:** ML Kit documents neither the
 * store's name nor this one, so the exclusion is pinned to an observed layout in
 * exactly the way the store path itself is. If a future ML Kit renames the
 * scratch directory, its debris starts counting again — silently, because there
 * is no degrade for "the number is too big", only for "there is no number".
 * Excluding it is still the better side of that trade: a completed pack's size
 * is the claim the card makes, and scratch files are not part of it.
 */
const val MLKIT_SCRATCH_DIR: String = "temp"

/**
 * Sums every regular file under [storeDir], recursively — the walk behind
 * [StorageProbe.packsBytes], extracted pure so the sum + degrade contract is
 * unit-testable without Android.
 *
 * - Absent path, or a path that is not a directory (e.g. renamed store
 *   replaced by a stray file) → `null` — the honest degrade, never 0-as-fact.
 * - Existing but empty store → `0`, a real "no packs occupy disk".
 * - Directories contribute no bytes themselves; only file lengths count.
 * - The store-root [MLKIT_SCRATCH_DIR] is not descended into. Only that one:
 *   a `temp` folder nested INSIDE a pack directory is the pack's own business
 *   and its bytes are real, and a plain file called `temp` at the root is a
 *   file, not the scratch area.
 */
fun packsBytesOf(storeDir: File): Long? {
    if (!storeDir.isDirectory) return null
    val scratch = File(storeDir, MLKIT_SCRATCH_DIR)
    return storeDir
        .walkTopDown()
        .onEnter { it != scratch }
        .filter(File::isFile)
        .sumOf(File::length)
}
