package com.codeboxlk.tranzlate.core.config

/**
 * Per-brand build-time configuration (plan §4 white-label config flow).
 * Only `:app` reads `BuildConfig`; everything else consumes this typed interface.
 * Libraries (`:lib:*`) receive their own config types mapped FROM this in `:app`.
 */
interface AppConfig {
    /** AdMob application id (manifest placeholder mirrors this). Empty until ads phase. */
    val admobAppId: String

    /** Ad unit ids (empty until the ads integration phase — never Google test ids in prod). */
    val adUnitBanner: String

    val adUnitInterstitial: String

    /** Qonversion project key (empty until the subscription integration phase). */
    val qonversionKey: String

    /** Defaults table: fresh-install language pair (en → fr). */
    val defaultSourceLang: String

    val defaultTargetLang: String

    /** Parsed ONCE from the flavor's FEATURES csv (plan §4 — typed set, no re-parsing). */
    val featureToggles: Set<FeatureToggle>
}

/**
 * White-label feature switches (plan §4 R2 — the nav shell is toggle-aware from
 * day 1; e.g. the camera entry only registers when [CAMERA] is present).
 */
enum class FeatureToggle {
    TEXT,
    CAMERA,
    VOICE,
    DIALOG,
    HISTORY,
    SETTINGS,
    ;

    companion object {
        /** Parse a FEATURES csv ("text,camera,history,settings") — unknown names are an error. */
        fun parseCsv(csv: String): Set<FeatureToggle> =
            csv
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { valueOf(it.uppercase()) }
                .toSet()
    }
}
