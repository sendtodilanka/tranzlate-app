package com.codeboxlk.tranzlate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DATA_MODEL `usage.*` typed accessors — keys exact.
 * Semantics (enforced by the Usage/Ads brains, not here):
 *  - advanced_ai_count: today's metered pool, all features share one NLP3.5 pool (D-2);
 *  - reset when device-local date(now) != date(reset_epoch) via AppClock;
 *  - ads_shown_today / ad_last_shown / translations_since_ad: D-4 cap, min-gap, every-Nth.
 */
@Singleton
class UsageDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val advancedAiCount: Flow<Int> = dataStore.data.map { it[KEY_ADVANCED_AI_COUNT] ?: 0 }

        val resetEpoch: Flow<Long> = dataStore.data.map { it[KEY_RESET_EPOCH] ?: 0L }

        val adsShownToday: Flow<Int> = dataStore.data.map { it[KEY_ADS_SHOWN_TODAY] ?: 0 }

        val adLastShown: Flow<Long> = dataStore.data.map { it[KEY_AD_LAST_SHOWN] ?: 0L }

        val translationsSinceAd: Flow<Int> = dataStore.data.map { it[KEY_TRANSLATIONS_SINCE_AD] ?: 0 }

        suspend fun setAdvancedAiCount(value: Int) {
            dataStore.edit { it[KEY_ADVANCED_AI_COUNT] = value }
        }

        suspend fun setResetEpoch(value: Long) {
            dataStore.edit { it[KEY_RESET_EPOCH] = value }
        }

        suspend fun setAdsShownToday(value: Int) {
            dataStore.edit { it[KEY_ADS_SHOWN_TODAY] = value }
        }

        suspend fun setAdLastShown(value: Long) {
            dataStore.edit { it[KEY_AD_LAST_SHOWN] = value }
        }

        suspend fun setTranslationsSinceAd(value: Int) {
            dataStore.edit { it[KEY_TRANSLATIONS_SINCE_AD] = value }
        }

        companion object {
            private val KEY_ADVANCED_AI_COUNT = intPreferencesKey("usage.advanced_ai_count")
            private val KEY_RESET_EPOCH = longPreferencesKey("usage.reset_epoch")
            private val KEY_ADS_SHOWN_TODAY = intPreferencesKey("usage.ads_shown_today")
            private val KEY_AD_LAST_SHOWN = longPreferencesKey("usage.ad_last_shown")
            private val KEY_TRANSLATIONS_SINCE_AD = intPreferencesKey("usage.translations_since_ad")
        }
    }
