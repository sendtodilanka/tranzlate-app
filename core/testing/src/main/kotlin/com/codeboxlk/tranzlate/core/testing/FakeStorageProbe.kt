package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.common.StorageProbe

/**
 * Settable storage fake — tests drive [free], [total], and [packs] directly.
 *
 * Defaults model a healthy fresh install: plenty of room, and `packs = null`
 * because the model store dir does not exist before the first download
 * (research E3) — which is exactly the seam's free-space-only degrade case
 * (issue #130 ruling U-5). Tests asserting a live meter set [packs]
 * explicitly.
 */
class FakeStorageProbe(
    var free: Long = 8L * GIB,
    var total: Long = 64L * GIB,
    var packs: Long? = null,
) : StorageProbe {
    /**
     * The failure to throw from [freeBytes], or null (issue #238).
     *
     * The prod probe is `StatFs(context.noBackupFilesDir.absolutePath)`, and
     * `StatFs` throws `IllegalArgumentException` when the underlying `statvfs`
     * fails — `android/os/StatFs.java:53`,
     * `throw new IllegalArgumentException("Invalid path: " + path, e)`. A fake
     * that can only ever return a number cannot express the pre-flight's real
     * failure mode, which is why the download tap had no test covering it.
     */
    var freeFailure: Throwable? = null

    override fun freeBytes(): Long {
        freeFailure?.let { throw it }
        return free
    }

    override fun totalBytes(): Long = total

    override suspend fun packsBytes(): Long? = packs

    private companion object {
        const val GIB = 1024L * 1024 * 1024
    }
}
