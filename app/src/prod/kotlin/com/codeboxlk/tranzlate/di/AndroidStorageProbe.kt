package com.codeboxlk.tranzlate.di

import android.content.Context
import android.os.StatFs
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.common.StorageProbe
import com.codeboxlk.tranzlate.core.common.packsBytesOf
import kotlinx.coroutines.withContext
import java.io.File

/**
 * StatFs over the volume that holds MLKit's model store (issue #90 pre-flight)
 * plus the aggregate store walk (issue #130 ruling U-5). Models land under
 * `noBackupFilesDir` (`no_backup/com.google.mlkit.translate.models` — research
 * E3), so that path's volume is the one the free/total numbers must describe
 * and that dir is the one the walk sums.
 *
 * The walk runs on [DispatcherProvider.io]; an absent/renamed store dir yields
 * `null` (the seam's honest-degrade contract — see [StorageProbe]).
 */
class AndroidStorageProbe(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : StorageProbe {
    override fun freeBytes(): Long = StatFs(context.noBackupFilesDir.absolutePath).availableBytes

    override fun totalBytes(): Long = StatFs(context.noBackupFilesDir.absolutePath).totalBytes

    override suspend fun packsBytes(): Long? =
        withContext(dispatchers.io) {
            packsBytesOf(File(context.noBackupFilesDir, MLKIT_TRANSLATE_MODELS_DIR))
        }

    private companion object {
        /**
         * MLKit's translate-model store, relative to `noBackupFilesDir` —
         * device-verified in research E3 (issue #90). An MLKit update renaming
         * The directory this walks is the one research E3 measured; experiment
         * E-S1 re-verifies it on a device and gates PR-15, where the number is
         * actually shown (re-ruled 2026-08-01, see the epic plan doc).
         * pins that on a real device.
         */
        const val MLKIT_TRANSLATE_MODELS_DIR = "com.google.mlkit.translate.models"
    }
}
