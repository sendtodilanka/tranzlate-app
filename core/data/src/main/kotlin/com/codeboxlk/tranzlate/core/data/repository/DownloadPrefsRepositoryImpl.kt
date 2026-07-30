package com.codeboxlk.tranzlate.core.data.repository

import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.datastore.TranzlatePreferencesDataSource
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** `prefs.allow_mobile_data` over DataStore; the brand default comes from [AppConfig]. */
@Singleton
class DownloadPrefsRepositoryImpl
    @Inject
    constructor(
        private val dataSource: TranzlatePreferencesDataSource,
        appConfig: AppConfig,
    ) : DownloadPrefsRepository {
        override val allowMobileData: Flow<Boolean> =
            dataSource.allowMobileData(appConfig.defaultAllowMobileData)

        override suspend fun setAllowMobileData(value: Boolean) {
            dataSource.setAllowMobileData(value)
        }
    }
