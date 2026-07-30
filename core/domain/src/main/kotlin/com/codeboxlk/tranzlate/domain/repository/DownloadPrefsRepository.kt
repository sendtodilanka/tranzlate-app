package com.codeboxlk.tranzlate.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Model-download preferences (issue #90 ruling): metered downloads are a
 * CONSENT question — this repo holds the standing answer; the per-tap dialog
 * covers the one-off case. Default is per-brand (AppConfig), applied by the
 * implementation.
 */
interface DownloadPrefsRepository {
    /** True = downloads may use mobile data without asking again. */
    val allowMobileData: Flow<Boolean>

    suspend fun setAllowMobileData(value: Boolean)
}
