package com.codeboxlk.tranzlate.di

import android.content.Context
import android.os.StatFs
import com.codeboxlk.tranzlate.core.common.StorageProbe

/**
 * StatFs over the volume that holds MLKit's model store (issue #90 pre-flight).
 * Models land under `noBackupFilesDir` (`no_backup/com.google.mlkit.translate.
 * models` — research E3), so that path's volume is the one that must fit them.
 */
class AndroidStorageProbe(
    private val context: Context,
) : StorageProbe {
    override fun freeBytes(): Long = StatFs(context.noBackupFilesDir.absolutePath).availableBytes
}
