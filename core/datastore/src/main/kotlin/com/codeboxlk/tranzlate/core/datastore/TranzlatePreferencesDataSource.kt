package com.codeboxlk.tranzlate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DATA_MODEL `prefs.*` typed accessors — keys exact, defaults per the DECISIONS
 * defaults table (en / fr / AUTO / system). Brand-specific default languages
 * (AppConfig) are seeded at first-run by the Settings vertical (later phase);
 * these constants are the spec fallbacks.
 */
@Singleton
class TranzlatePreferencesDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * Every read derives from here rather than from `dataStore.data` directly,
         * so this one catch protects all of them (issue #254).
         *
         * DataStore does not recover from a read failure on its own — the exception
         * is delivered to the collector, which for us is a `viewModelScope`, and an
         * unhandled one there takes the app down on launch with no way out but
         * clearing app data. The factory's `corruptionHandler` repairs a corrupt
         * *file*; this covers the transient failures it does not. The eager readers
         * make it a launch crash specifically: `TextViewModel` collects source /
         * target / mode through `stateIn(…, Eagerly)`, which starts at construction
         * with no handler attached, so the first read on a cold start is the one
         * that would fall over.
         *
         * Degrades ANY non-cancellation `Throwable`, not only `IOException` (issue
         * #254, widening #236's persistence guards to the one read that was missed).
         * The narrow `IOException`-only form let everything else — a decode bug, a
         * keystore / JNI `LinkageError`, an `IllegalStateException` from a DataStore
         * internal — escape into those eager scopes and crash the Text screen before
         * it drew a frame. A preference read is a best-effort convenience; none of
         * it is worth the launch. The failure is not hidden from engineers, only
         * from the user: it still reaches logcat / crash breadcrumbs through the
         * app-scope `CoroutineExceptionHandler` (issue #238).
         *
         * `CancellationException` is rethrown by name FIRST and never degraded: it
         * is structured concurrency's cancellation signal, not a read failure, and
         * emitting in its place would keep a cancelled collector alive. Same shape
         * as [editSafely] and the #236 guards.
         *
         * The asymmetry with [editSafely] is deliberate. A failed *read* into an
         * eager scope degrades so the screen still opens; a failed *write* stays
         * loud (`IOException`-only) so a preference that did not persist is a
         * visible mis-save, never a silent lie. Read degrades, write surfaces.
         */
        private val preferences: Flow<Preferences> =
            dataStore.data.catch { cause ->
                if (cause is kotlin.coroutines.cancellation.CancellationException) throw cause
                emit(emptyPreferences())
            }

        /**
         * The WRITE half of the guard above (issue #238) — every setter in this
         * class goes through here, for the same reason every read derives from
         * [preferences].
         *
         * Reads were guarded and writes were not, and the asymmetry had no
         * argument behind it: `DataStore.edit` is documented to throw `IOException`,
         * and several of these setters are launched fire-and-forget on the
         * application scope, where an escape used to end the process. **A consent
         * checkbox that fails to persist should mis-save, not crash** — the user
         * gets asked about mobile data one more time than they wanted, which is the
         * cheap side of that trade and the side that keeps their choice reversible.
         *
         * The rule is deliberately as narrow as the read guard's: `IOException`
         * only, because that is what `edit` documents. Anything else is a
         * programming error and must stay loud. `CancellationException` is not an
         * `IOException`, so it propagates untouched and structured concurrency is
         * unaffected.
         */
        private suspend fun editSafely(transform: suspend (MutablePreferences) -> Unit) {
            try {
                dataStore.edit(transform)
            } catch (ignored: IOException) {
                // The value in memory is already what the caller asked for; the
                // next successful write re-states it. Nothing is owed a message:
                // every one of these is a preference the user can set again.
            }
        }

        val sourceLang: Flow<String> = preferences.map { it[KEY_SOURCE_LANG] ?: DEFAULT_SOURCE_LANG }

        val targetLang: Flow<String> = preferences.map { it[KEY_TARGET_LANG] ?: DEFAULT_TARGET_LANG }

        /** ModeId name; AUTO by default — never the metered mode (defaults table). */
        val textMode: Flow<String> = preferences.map { it[KEY_TEXT_MODE] ?: DEFAULT_TEXT_MODE }

        /** 0 = system (defaults table). */
        val theme: Flow<Int> = preferences.map { it[KEY_THEME] ?: DEFAULT_THEME }

        /** Material You. Off by default — the GT-identical palette is the brand (issue #15). */
        val dynamicColor: Flow<Boolean> = preferences.map { it[KEY_DYNAMIC_COLOR] ?: DEFAULT_DYNAMIC_COLOR }

        suspend fun setSourceLang(value: String) {
            editSafely { it[KEY_SOURCE_LANG] = value }
        }

        suspend fun setTargetLang(value: String) {
            editSafely { it[KEY_TARGET_LANG] = value }
        }

        /** Swap-safe: both ids in ONE edit so no observer ever sees a torn pair. */
        suspend fun setLanguagePair(
            sourceValue: String,
            targetValue: String,
        ) {
            editSafely {
                it[KEY_SOURCE_LANG] = sourceValue
                it[KEY_TARGET_LANG] = targetValue
            }
        }

        suspend fun setTextMode(value: String) {
            editSafely { it[KEY_TEXT_MODE] = value }
        }

        suspend fun setTheme(value: Int) {
            editSafely { it[KEY_THEME] = value }
        }

        suspend fun setDynamicColor(value: Boolean) {
            editSafely { it[KEY_DYNAMIC_COLOR] = value }
        }

        /**
         * Issue #90 consent gate. The DEFAULT is per-brand (AppConfig), so the
         * caller supplies it — this layer stays config-blind.
         */
        fun allowMobileData(defaultValue: Boolean): Flow<Boolean> =
            preferences.map { it[KEY_ALLOW_MOBILE_DATA] ?: defaultValue }

        suspend fun setAllowMobileData(value: Boolean) {
            editSafely { it[KEY_ALLOW_MOBILE_DATA] = value }
        }

        /**
         * When each language was last chosen — what the picker's "Recent" section
         * is built from (issue #117), split per picking side since issue #130
         * rev.3 (16a shows source-recents and target-recents separately).
         *
         * This lives in preferences and NOT in the `language` Room table, which
         * is where it looks like it should live. That table is never seeded —
         * `LanguageDao.upsertAll` has no production caller — so its `last_used_at`
         * UPDATE always matched zero rows and Recent could never populate. A
         * recents list is a UI convenience about a handful of ids, not catalog
         * data; keeping it here means it does not depend on a seeding decision
         * that has not been made, and it survives the catalog changing under it.
         *
         * Three keys, one codec: the pre-split single key is read-only legacy —
         * new picks land under the side-specific key. Continuity for upgraders
         * is a read-time union in [recentLanguages] (chosen over a one-shot
         * seed: no write ever happens on the read path, there is no "has the
         * seed run" flag to corrupt, and the old data stays byte-identical on
         * disk for a rollback). The legacy entries carry no side information,
         * so the per-side flows honestly EXCLUDE them rather than guessing a
         * side that was never recorded.
         *
         * Stored as `id:millis` entries joined by U+001F — a unit separator can
         * appear in neither a BCP-47 tag nor a decimal, so the codec has no
         * escaping to get wrong. Malformed entries are dropped rather than
         * throwing: a corrupt preference must cost the user their recents list,
         * never the screen. Each key decodes independently, so one corrupt map
         * never takes the other two down with it.
         */
        val recentSourceLanguages: Flow<Map<String, Long>> =
            preferences.map { prefs -> decodeRecents(prefs[KEY_RECENT_LANGS_SOURCE].orEmpty()) }

        /** See [recentSourceLanguages]. */
        val recentTargetLanguages: Flow<Map<String, Long>> =
            preferences.map { prefs -> decodeRecents(prefs[KEY_RECENT_LANGS_TARGET].orEmpty()) }

        /**
         * The MERGED view the shipped 15a picker renders: union of the legacy
         * pre-split key and both side keys, newest stamp per id. Exactly the
         * single-map shape it always served, so the split is invisible there.
         */
        val recentLanguages: Flow<Map<String, Long>> =
            preferences.map { prefs ->
                val union =
                    decodeRecents(prefs[KEY_RECENT_LANGS].orEmpty()).asSequence() +
                        decodeRecents(prefs[KEY_RECENT_LANGS_SOURCE].orEmpty()).asSequence() +
                        decodeRecents(prefs[KEY_RECENT_LANGS_TARGET].orEmpty()).asSequence()
                union
                    .groupingBy { it.key }
                    .fold(Long.MIN_VALUE) { newest, entry -> maxOf(newest, entry.value) }
            }

        /**
         * Records a source-side pick and keeps only the [RECENT_STORE_LIMIT] most
         * recent, so the preference cannot grow with every language the user ever
         * touches. The cap is PER MAP — splitting must never remember less than
         * the single key did.
         */
        suspend fun recordSourceLanguageUse(
            languageId: String,
            atMillis: Long,
        ) = recordUse(KEY_RECENT_LANGS_SOURCE, languageId, atMillis)

        /** See [recordSourceLanguageUse]. */
        suspend fun recordTargetLanguageUse(
            languageId: String,
            atMillis: Long,
        ) = recordUse(KEY_RECENT_LANGS_TARGET, languageId, atMillis)

        private suspend fun recordUse(
            key: Preferences.Key<String>,
            languageId: String,
            atMillis: Long,
        ) {
            if (languageId.isBlank()) return
            editSafely { prefs ->
                val merged = decodeRecents(prefs[key].orEmpty()) + (languageId to atMillis)
                prefs[key] =
                    merged.entries
                        .sortedByDescending { it.value }
                        .take(RECENT_STORE_LIMIT)
                        .joinToString(RECENT_ENTRY_SEPARATOR) { "${it.key}$RECENT_FIELD_SEPARATOR${it.value}" }
            }
        }

        companion object {
            private val KEY_SOURCE_LANG = stringPreferencesKey("prefs.source_lang")
            private val KEY_TARGET_LANG = stringPreferencesKey("prefs.target_lang")
            private val KEY_TEXT_MODE = stringPreferencesKey("prefs.text_mode")
            private val KEY_THEME = intPreferencesKey("prefs.theme")
            private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("prefs.dynamic_color")
            private val KEY_ALLOW_MOBILE_DATA = booleanPreferencesKey("prefs.allow_mobile_data")

            /** Pre-split recents (issue #117 shape). READ-ONLY legacy since #130 rev.3 — never written again. */
            private val KEY_RECENT_LANGS = stringPreferencesKey("prefs.recent_languages")
            private val KEY_RECENT_LANGS_SOURCE = stringPreferencesKey("prefs.recent_languages.source")
            private val KEY_RECENT_LANGS_TARGET = stringPreferencesKey("prefs.recent_languages.target")

            const val DEFAULT_SOURCE_LANG = "en"
            const val DEFAULT_TARGET_LANG = "fr"
            const val DEFAULT_TEXT_MODE = "AUTO"
            const val DEFAULT_THEME = 0
            const val DEFAULT_DYNAMIC_COLOR = false

            /** Stored beyond what the picker shows, so trimming the UI list never loses history. */
            const val RECENT_STORE_LIMIT = 10

            private const val RECENT_ENTRY_SEPARATOR = "\u001F"
            private const val RECENT_FIELD_SEPARATOR = ":"

            internal fun decodeRecents(raw: String): Map<String, Long> =
                raw
                    .split(RECENT_ENTRY_SEPARATOR)
                    .mapNotNull { entry ->
                        val id = entry.substringBefore(RECENT_FIELD_SEPARATOR, missingDelimiterValue = "")
                        val millis =
                            entry
                                .substringAfter(
                                    RECENT_FIELD_SEPARATOR,
                                    missingDelimiterValue = "",
                                ).toLongOrNull()
                        if (id.isNotBlank() && millis != null) id to millis else null
                    }.toMap()
        }
    }
