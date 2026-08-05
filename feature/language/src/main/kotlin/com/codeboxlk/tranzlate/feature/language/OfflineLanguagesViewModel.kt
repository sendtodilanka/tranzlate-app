package com.codeboxlk.tranzlate.feature.language

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.ApplicationScope
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One Screen-B row: a catalog language that MLKit can hold offline. */
data class OfflineLanguageRow(
    val id: String,
    val name: String,
    val state: OfflineModelState,
)

/**
 * The ML Kit English pivot's language id (issue #224).
 *
 * Every ML Kit translation model is an `X↔English` pair; there is no standalone
 * `en` pack, and ML Kit reports English as on-device before ANY pack is fetched
 * (a first-run downloaded count of 1). So on Screen B the English row arrives in
 * the `Downloaded` state and draws the same 🗑 every other downloaded pack does —
 * except tapping it does nothing. Measured on an emulator in
 * `docs/research/issue-224-en-row-delete.md`: `deleteDownloadedModel("en")` is a
 * NO-OP (Branch A) — English stays in the downloaded set, offline translation is
 * unharmed, and the Download side is unreachable because `en` never leaves the set.
 *
 * Owner ruling (2026-08-05): the pivot row is **not hidden** — English is the 59th
 * id in `BundledLanguageCatalog.offlineCapableIds`, so removing it from the list
 * would make the "59 languages" counter (C-11) lie — but it is **non-actionable**:
 * no Download, no Delete, because neither acts on the pivot. [OfflineRow] renders
 * it as "included with every language" instead of a control.
 *
 * Identified by the tag, not a position: `TranslateLanguage.ENGLISH` is `"en"` and
 * this catalog hands ML Kit tags straight through untranslated, so the pivot's id
 * is exactly `"en"` in every layer that touches it (the picker, the catalog, the
 * model store). Because the screen renders the mapped [OfflinePackRow] — whose
 * `buildOfflineRows` transform lives in another file — the identity is a shared
 * predicate here rather than a flag carried on the row, so both the row builder
 * and the screen decide "is this the pivot?" the same way and cannot drift.
 */
internal const val PIVOT_LANGUAGE_ID: String = "en"

/** True only for the ML Kit pivot row, whose Download/Delete controls are stripped (#224). */
internal fun isPivotLanguage(id: String): Boolean = id == PIVOT_LANGUAGE_ID

/** The saved-state key for the open remove question. Namespaced — the handle is shared. */
internal const val KEY_PENDING_REMOVAL = "offline_languages.pending_removal"

/**
 * An open "remove this pack?" question, with everything the two sheets need to
 * draw themselves (#130 PR-19).
 *
 * Derived rather than stored: only the language [id] survives process death (a
 * `String` in the `SavedStateHandle`), and the rest is recomputed from the
 * preference seam and the database when the question is restored. Storing the
 * derived fields would freeze a count and a target that the restore is supposed
 * to re-read.
 *
 * @param inUseAsTarget the pack belongs to the language the user is translating
 *   INTO right now, which is the only sense of "in use" either drawn frame has.
 *   Removing it changes no selection — see [RemoveInUseSheet] — it just means
 *   the very next translation is the one that needs a connection.
 * @param savedCount saved phrases using this language, on either side. Read once
 *   per question and only when [inUseAsTarget]: 19f does not draw the line, so
 *   the common removal costs no query at all.
 */
data class PendingPackRemoval(
    val id: String,
    val inUseAsTarget: Boolean,
    val savedCount: Int,
)

/**
 * Screen B state holder (spec 02 D-E2): rows = bundled catalog ∩ MLKit-capable —
 * online-only languages NEVER appear here (they live in the picker with a badge).
 * The screen only ASKS the Translation brain's model manager.
 *
 * Issue #90 (debate ruling): a metered download is a CONSENT question, and it
 * is decided by [DownloadGate] — never by MLKit's untested `requireWifi`. This
 * screen only routes the taps and lends the gate its scope.
 */
@HiltViewModel
class OfflineLanguagesViewModel
    @Inject
    constructor(
        languageRepository: LanguageRepository,
        private val modelManager: OfflineModelManager,
        private val downloadGate: DownloadGate,
        private val downloadPrefs: DownloadPrefsRepository,
        private val translatePrefs: TranslatePrefsRepository,
        private val translations: TranslationRepository,
        private val handle: SavedStateHandle,
        private val dispatchers: DispatcherProvider,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        val rows: StateFlow<List<OfflineLanguageRow>> =
            combine(
                languageRepository.languages(),
                // Same guard LanguageRepositoryImpl.languages() puts on this exact
                // source, for the same reason: `combine` waits for EVERY source
                // before it can emit at all, and on a device without Play Services
                // the ML Kit answer may effectively never come — unprefixed, that
                // parked this screen on "Loading…" forever with no retry (the
                // EDGE_CASES dead-end class: a wait state that guides nowhere).
                // Prefixed empty, rows paint immediately at their resting state
                // and flip when the real state arrives — the contract the picker
                // already honours (its list renders; badges arrive when they do).
                modelManager.modelStates().onStart { emit(emptyMap()) },
            ) { catalog, states ->
                catalog
                    .filter(Language::offlineAvailable)
                    .map { language ->
                        OfflineLanguageRow(
                            id = language.id,
                            name = language.name,
                            // Capability is compile-time catalog truth (D-E2:
                            // `offlineAvailable` is derived from ML Kit's own tag
                            // list), so a missing map entry can only mean "no
                            // answer yet", never "not capable" — the resting
                            // state is NotDownloaded, not a hidden row.
                            state = states[language.id] ?: OfflineModelState.NotDownloaded,
                        )
                    }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyList())

        /** Language id awaiting the mobile-data consent sheet; null = no sheet. */
        val pendingConsent: StateFlow<String?> = downloadGate.pendingConsent

        /**
         * Sheet 19a's checkbox, exactly as the picker exposes it — the stored
         * `allowMobileData` read from the other end ([alwaysAskOf]). Both screens
         * raise the SAME sheet, so both must answer it the same way; a second
         * polarity or a second scope here is the drift the one-home rule exists
         * to stop. See `LanguagePickerViewModel.alwaysAsk` for why the seed is
         * `true` and why the write runs on the application scope.
         */
        val alwaysAsk: StateFlow<Boolean> =
            downloadPrefs.allowMobileData
                .map(::alwaysAskOf)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), true)

        /** The checkbox moved: write the STANDING preference Settings and the gate share. */
        fun onAlwaysAskChange(alwaysAsk: Boolean) {
            appScope.launch { downloadPrefs.setAllowMobileData(allowMobileDataOf(alwaysAsk)) }
        }

        /**
         * Row ⬇ / ↻. Metered + no standing permission → ask first, download never starts.
         *
         * Off the main thread (issue #238), for the reason
         * `LanguagePickerViewModel.download` spells out: `isMetered()` is a
         * synchronous binder IPC and the manager's free-space pre-flight is a
         * `statvfs` syscall, and `viewModelScope` is `Dispatchers.Main.immediate`.
         * Both screens raise the SAME sheet through the SAME gate, so both must
         * reach it the same way — a second answer here is the drift the one-home
         * rule exists to stop.
         */
        fun download(id: String) {
            viewModelScope.launch { withContext(dispatchers.io) { downloadGate.requestDownload(id) } }
        }

        /** Dialog "Download once": THIS download only — the standing pref is untouched. */
        fun downloadAnyway() {
            val consented = downloadGate.consentOnce() ?: return
            viewModelScope.launch { withContext(dispatchers.io) { downloadGate.downloadConsented(consented) } }
        }

        /** Dialog "Wait for Wi-Fi" (or dismiss): the row stays NotDownloaded — no dead end. */
        fun dismissConsent() = downloadGate.dismiss()

        /**
         * The row ⏹ while Downloading — delete-to-cancel, the verified ML Kit
         * limit (there is no cancel API, so stopping IS deleting the partial
         * model).
         *
         * **Deliberately NOT routed through the remove sheet** that [requestRemove]
         * raises. 19f's body — *"Frees space on this device. Spanish will need a
         * connection to translate until you download it again"* — describes
         * removing a pack the user HAS. A download still in flight is not that:
         * nothing is being taken away that they had a moment ago, and the ⏹ has
         * always been the way out of a download they no longer want. Putting a
         * confirmation in front of an abort turns an escape hatch into a second
         * decision. The ruling asks for the unconfirmed 🗑 to become confirmed;
         * this is the ⏹, and it stays immediate.
         */
        fun stopDownload(id: String) {
            viewModelScope.launch { modelManager.delete(id) }
        }

        /**
         * The open remove question, or null (#130 PR-19).
         *
         * Only the id is durable — a `String` in the `SavedStateHandle`, the same
         * shape the consent question uses. `inUseAsTarget` and `savedCount` are
         * DERIVED on every restore, so a question that outlives a process death is
         * answered against the state that exists now rather than against a
         * snapshot taken before the app was killed.
         *
         * `distinctUntilChanged` on the target keeps an unrelated preference write
         * from re-running the count query underneath an open sheet — the same
         * guard `LanguageRepositoryImpl` puts on its own combine, for the same
         * reason.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        val pendingRemoval: StateFlow<PendingPackRemoval?> =
            handle
                .getStateFlow<String?>(KEY_PENDING_REMOVAL, null)
                .flatMapLatest { id -> if (id == null) flowOf(null) else removalFor(id) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), null)

        private fun removalFor(id: String): Flow<PendingPackRemoval> =
            translatePrefs.targetLang
                .distinctUntilChanged()
                .map { target ->
                    val inUse = target == id
                    PendingPackRemoval(
                        id = id,
                        inUseAsTarget = inUse,
                        // 19f draws no saved line, so the query runs only for the
                        // sheet that has one to draw.
                        savedCount = if (inUse) savedCountOf(id) else 0,
                    )
                }

        /**
         * Best-effort count. A database that cannot answer must not stop the user
         * removing a pack: zero renders as an ABSENT line, which is what a user
         * who has saved nothing sees anyway — a missing reassurance, never a false
         * one.
         *
         * `Throwable`, not `Exception` (issue #236). This query is Room, and every
         * statement Room runs here ends in a `native` method on
         * `android.database.sqlite.SQLiteConnection`; a JNI link that cannot be
         * satisfied raises `UnsatisfiedLinkError`, which is a `LinkageError`, so an
         * `Error`, so NOT an `Exception` — the full reasoning and its citations are
         * `TextViewModel.kt:768-779` (#195), and this call was simply written on the
         * un-migrated side of it. It reaches the user through `removalFor`'s `map`,
         * terminated by `stateIn(viewModelScope, …)`, which installs no
         * `CoroutineExceptionHandler`: a narrow catch hands the failure back to
         * `Thread.defaultUncaughtExceptionHandler` and the app disappears on the
         * trash tap for the pack the user is currently translating INTO.
         *
         * `CancellationException` is rethrown FIRST and by name because it is an
         * `Exception` — widening protects it not at all, and only the rethrow does.
         * That arm is entered by no test and the fixture cannot express it; the gap
         * is recorded as #242 and is unchanged by the widening above it.
         */
        private suspend fun savedCountOf(id: String): Int =
            try {
                translations.savedCountUsing(id)
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Throwable,
            ) {
                0
            }

        /** Row 🗑: ask first. Nothing is deleted until [confirmRemove]. */
        fun requestRemove(id: String) {
            handle[KEY_PENDING_REMOVAL] = id
        }

        /** Cancel, back, scrim or drag — the row is left exactly as it was (no dead end). */
        fun dismissRemove() {
            handle[KEY_PENDING_REMOVAL] = null
        }

        /**
         * "Remove" / "Remove anyway" — the one place a downloaded pack is deleted.
         *
         * Reads the id from the durable handle rather than taking it as a
         * parameter, so a confirm can only ever remove the pack the sheet is
         * currently asking about, and a second tap on an already-answered sheet
         * finds nothing to do.
         *
         * **It writes no language preference, and there is nothing here to fall
         * back to.** The drawn 19g says the target switches to English; it does
         * not. `OfflineModelManager.delete` removes the model and the row returns
         * to `NotDownloaded` — the selection is untouched, and the language keeps
         * translating through the waterfall's online tiers.
         *
         * On [appScope], not `viewModelScope`, for the reason the picker's `select`
         * gives: this is a write the user explicitly asked for and the screen it
         * was asked from can go away underneath it. The delete already outlives its
         * caller inside `RealOfflineModelManager` (its own scope plus a `join`), so
         * this is the belt rather than the braces — named because no JVM test in
         * this repo can tell the two scopes apart here.
         */
        fun confirmRemove() {
            val id = handle.get<String>(KEY_PENDING_REMOVAL) ?: return
            handle[KEY_PENDING_REMOVAL] = null
            appScope.launch { modelManager.delete(id) }
        }
    }
