package com.codeboxlk.tranzlate.core.model

/**
 * App appearance choice — DATA_MODEL `prefs.theme`, default `0` = follow the system.
 *
 * Three states, not a two-way switch: the documented default *is* "follow the
 * system", and a boolean cannot say that.
 *
 * [storedValue] is the persisted wire format and is declared explicitly rather
 * than leaning on the ordinal, so reordering or inserting a constant here can
 * never silently repaint every existing install.
 */
enum class ThemeMode(
    val storedValue: Int,
) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    ;

    companion object {
        /**
         * Unknown values degrade to [SYSTEM] — the documented default. Reachable
         * from a downgrade, or from a preferences file replaced after corruption.
         */
        fun fromStoredValue(value: Int): ThemeMode = entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

/**
 * Everything the theme needs, in one emission.
 *
 * Deliberately one value rather than two flows: `TranzlateTheme` takes both at
 * once, and the splash screen holds until the preference has resolved (issue #17
 * A6). Two flows would make "has it arrived yet?" two questions and reintroduce
 * the light-flash-before-dark problem this exists to prevent.
 *
 * No parameter defaults on purpose — "not loaded yet" is expressed by the
 * *absence* of a value at the collector (a nullable state), never by a
 * default-looking instance that could be mistaken for the user's real choice.
 * Use [Default] where a concrete fallback is genuinely wanted.
 */
data class ThemeSettings(
    val mode: ThemeMode,
    val dynamicColor: Boolean,
) {
    companion object {
        /** The DATA_MODEL defaults: follow the system, static brand palette. */
        val Default = ThemeSettings(mode = ThemeMode.SYSTEM, dynamicColor = false)
    }
}
