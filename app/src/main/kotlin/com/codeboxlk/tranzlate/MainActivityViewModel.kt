package com.codeboxlk.tranzlate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.ThemePrefsRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SUBSCRIBE_TIMEOUT_MS = 5_000L

/**
 * The 19h request the shell should draw right now (#130 PR-20): the target that
 * has no pack, and the on-device packs to offer instead.
 *
 * @property missingLangId the target the composer could not translate to offline
 *   — named in the sheet body, never offered (it has no pack).
 * @property onDeviceLangIds the packs already on the device, minus the missing
 *   one and minus the current source (so no "Use X" can make the same-language
 *   pair 19m guards). Never empty — the host returns null instead.
 */
data class OfflinePackMissing(
    val missingLangId: String,
    val onDeviceLangIds: List<String>,
)

/**
 * Whether sheet 19h should be on screen, and what it offers (#130 PR-20). Pure,
 * so the whole gate is testable without the Activity's `viewModelScope` or a
 * live `ConnectivityMonitor` — [MainActivityViewModel.offlinePackMissing] is only
 * this function lifted onto the flows.
 *
 * Returns null (no sheet, the composer's offline error card guides instead) when
 * there is no wall, when [online] again (the online tiers can translate, so the
 * refusal is moot), or when there is nothing to offer. What is offered is every
 * on-device pack EXCEPT the missing target itself and the current [source] —
 * excluding the source is what keeps a "Use X" here from producing the
 * same-language pair 19m guards.
 */
internal fun offlinePackMissingOf(
    pending: String?,
    online: Boolean,
    languages: List<Language>,
    source: String,
): OfflinePackMissing? {
    if (pending == null || online) return null
    val onDevice =
        languages
            .filter { it.offlineDownloaded && it.id != pending && it.id != source }
            .map { it.id }
    return if (onDevice.isEmpty()) null else OfflinePackMissing(pending, onDevice)
}

/**
 * Holds the app-shell state that outlives any single destination.
 *
 * Activity-scoped rather than per-screen for two reasons that share a home here:
 * the appearance choice wraps every destination and the splash screen (issue #17
 * A6) waits on it before the first frame; and sheet **19h** (#130 PR-20) is
 * raised BY the Text composer but hosted ABOVE the `NavDisplay`, because the
 * `app` module is the composition root that already depends on every feature and
 * so can host a composer-raised cross-feature sheet without `:feature:text`
 * depending on `:feature:language` (ruling §0 P3, :26).
 */
@HiltViewModel
class MainActivityViewModel
    @Inject
    constructor(
        themePrefs: ThemePrefsRepository,
        private val connectivity: ConnectivityMonitor,
        private val languageRepository: LanguageRepository,
        private val translatePrefs: TranslatePrefsRepository,
    ) : ViewModel() {
        // ---- Appearance (issue #17) --------------------------------------------

        /**
         * The initial value is deliberately `null` and means **"not read yet"**,
         * not "the user chose the defaults". A6 uses exactly that distinction to
         * hold the splash; a non-null default here would make a user who picked
         * Dark see a light frame first, which is the whole problem the splash gate
         * exists to prevent.
         */
        val themeSettings: StateFlow<ThemeSettings?> =
            themePrefs.settings.stateIn(
                scope = viewModelScope,
                // Eagerly, not WhileSubscribed: the first frame cannot wait for a
                // subscriber, and this flow lives exactly as long as the Activity.
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

        // ---- Sheet 19h — offline, pack missing (#130 PR-20) --------------------

        /** The target the composer last hit an offline wall on, or null. */
        private val pendingMissingLang = MutableStateFlow<String?>(null)

        /**
         * The composer HOISTS its offline-pack-missing signal here: the target of a
         * translation that failed offline, or null when the composer has no such
         * wall (or has left the screen). This is only the RAW signal; whether it
         * becomes a sheet is [offlinePackMissing]'s decision.
         */
        fun onOfflinePackMissing(langId: String?) {
            pendingMissingLang.value = langId
        }

        /**
         * The 19h sheet to draw, or null. A sheet exists only when the composer has
         * raised a wall AND the device is still offline — if connectivity returns,
         * the online tiers can translate and the sheet is moot — AND there is at
         * least one other on-device pack to offer, so the sheet always has a way to
         * finish the task (EDGE_CASES §7). When it is null the composer's own
         * offline error card guides the user instead.
         */
        val offlinePackMissing: StateFlow<OfflinePackMissing?> =
            combine(
                pendingMissingLang,
                connectivity.online,
                languageRepository.languages(),
                translatePrefs.sourceLang,
                ::offlinePackMissingOf,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), null)

        /**
         * "Use %1$s": switch the target to an on-device pack so the next
         * translation runs on the device, and clear the request so the sheet
         * dismisses. On [viewModelScope], which is the Activity's — the sheet has
         * already led the user somewhere, so unlike the picker's own select there
         * is no screen about to pop and cancel the write.
         */
        fun useLanguage(id: String) {
            pendingMissingLang.value = null
            viewModelScope.launch { translatePrefs.setTargetLang(id) }
        }

        /** "Close" / scrim / back: the request is answered, the error card remains. */
        fun dismissOfflinePackMissing() {
            pendingMissingLang.value = null
        }
    }
