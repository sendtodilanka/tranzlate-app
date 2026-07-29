package com.codeboxlk.tranzlate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The three persisted metered-counter facts (issue #66). */
data class PersistedUsageCounts(
    val freeSpent: Int,
    val proSpent: Int,
    val dayEpoch: Long,
) {
    companion object {
        /** No day recorded yet — first run, or a wiped store. */
        const val NO_DAY = 0L
    }
}

/**
 * DATA_MODEL `usage.*` typed accessors — keys exact.
 * Semantics (enforced by the Usage/Ads brains, not here):
 *  - advanced_ai_count / pro_ai_count / reset_epoch: owned EXCLUSIVELY by
 *    RealUsagePolicy via readUsage/writeUsage (issue #66) — reset_epoch stores
 *    the device-local epoch DAY (rev.2); no other accessor may touch them;
 *  - ads_shown_today / ad_last_shown / translations_since_ad: D-4 cap, min-gap, every-Nth.
 */
@Singleton
class UsageDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * One consistent read of the metered-counter facts (issue #66). Catch
         * mirrors the prefs source idiom: an unreadable store reads as empty.
         */
        suspend fun readUsage(): PersistedUsageCounts {
            val prefs =
                try {
                    dataStore.data.first()
                } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                    throw rethrown
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
                ) {
                    emptyPreferences()
                }
            return PersistedUsageCounts(
                freeSpent = prefs[KEY_ADVANCED_AI_COUNT] ?: 0,
                proSpent = prefs[KEY_PRO_AI_COUNT] ?: 0,
                dayEpoch = prefs[KEY_RESET_EPOCH] ?: PersistedUsageCounts.NO_DAY,
            )
        }

        /**
         * ONE atomic edit for all three facts — a torn count/epoch pair must be
         * impossible (same rule as the language-pair swap write).
         */
        suspend fun writeUsage(counts: PersistedUsageCounts) {
            dataStore.edit {
                it[KEY_ADVANCED_AI_COUNT] = counts.freeSpent
                it[KEY_PRO_AI_COUNT] = counts.proSpent
                it[KEY_RESET_EPOCH] = counts.dayEpoch
            }
        }

        val adsShownToday: Flow<Int> = dataStore.data.map { it[KEY_ADS_SHOWN_TODAY] ?: 0 }

        val adLastShown: Flow<Long> = dataStore.data.map { it[KEY_AD_LAST_SHOWN] ?: 0L }

        val translationsSinceAd: Flow<Int> = dataStore.data.map { it[KEY_TRANSLATIONS_SINCE_AD] ?: 0 }

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
            private val KEY_PRO_AI_COUNT = intPreferencesKey("usage.pro_ai_count")
            private val KEY_RESET_EPOCH = longPreferencesKey("usage.reset_epoch")
            private val KEY_ADS_SHOWN_TODAY = intPreferencesKey("usage.ads_shown_today")
            private val KEY_AD_LAST_SHOWN = longPreferencesKey("usage.ad_last_shown")
            private val KEY_TRANSLATIONS_SINCE_AD = intPreferencesKey("usage.translations_since_ad")
        }
    }
