package com.codeboxlk.tranzlate.config

import com.codeboxlk.tranzlate.BuildConfig
import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.config.FeatureToggle
import javax.inject.Inject

/**
 * The ONLY BuildConfig reader in the codebase (plan §4 config flow):
 * flavor buildConfigFields → this typed [AppConfig] → everything else.
 * FEATURES csv parses ONCE into the typed set.
 */
class BuildConfigAppConfig
    @Inject
    constructor() : AppConfig {
        override val admobAppId: String = BuildConfig.ADMOB_APP_ID
        override val adUnitBanner: String = BuildConfig.AD_UNIT_BANNER
        override val adUnitInterstitial: String = BuildConfig.AD_UNIT_INTERSTITIAL
        override val qonversionKey: String = BuildConfig.QONVERSION_KEY
        override val gctApiKey: String = BuildConfig.GCT_API_KEY
        override val defaultSourceLang: String = BuildConfig.DEFAULT_SOURCE_LANG
        override val defaultTargetLang: String = BuildConfig.DEFAULT_TARGET_LANG
        override val defaultAllowMobileData: Boolean = BuildConfig.DEFAULT_ALLOW_MOBILE_DATA
        override val featureToggles: Set<FeatureToggle> by lazy {
            FeatureToggle.parseCsv(BuildConfig.FEATURES)
        }
    }
