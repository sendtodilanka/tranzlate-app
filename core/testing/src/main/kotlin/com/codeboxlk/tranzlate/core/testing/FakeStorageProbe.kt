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
    override fun freeBytes(): Long = free

    override fun totalBytes(): Long = total

    override suspend fun packsBytes(): Long? = packs

    private companion object {
        const val GIB = 1024L * 1024 * 1024
    }
}
