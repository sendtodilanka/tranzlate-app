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
         * MLKit's translate-model store, relative to `noBackupFilesDir`.
         *
         * First measured by research E3 (issue #90, 2026-07-30) and
         * **re-verified on a device by experiment E-S1** (2026-08-02,
         * `emulator-5554`): downloading one af↔en pack created exactly this
         * directory and put 30 files totalling 44,169,505 bytes in it. Full
         * record in `docs/research/issue-130-e-s1-storage-walk.md`.
         *
         * MLKit has never documented the name, so an update could still move it.
         * That is why [StorageProbe.packsBytes] degrades to `null` rather than to
         * `0` — E-S1 confirmed the absent-directory half too, by renaming the
         * store while 44 MB of models were still on disk under the new name.
         */
        const val MLKIT_TRANSLATE_MODELS_DIR = "com.google.mlkit.translate.models"
    }
}
