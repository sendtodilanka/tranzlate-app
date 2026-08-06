package com.codeboxlk.tranzlate.feature.language

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeboxlk.tranzlate.core.common.AppClock
import com.codeboxlk.tranzlate.core.common.ApplicationScope
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.common.StorageProbe
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.LanguageTagResolver
import com.codeboxlk.tranzlate.core.model.OfflineModelFailure
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageUsageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * One Manage-packs row source: a catalog language that MLKit can hold offline.
 *
 * @property hasOfflineVoice this device can also SPEAK this language offline — copied
 *   straight from [Language.hasOfflineVoice] (the same device truth the picker's
 *   speaker mark reads), never crossed with the translate-pack state: a voice and a
 *   pack are separate installs. Carried only so the 20c pack-actions sheet can draw
 *   its informational voice line; defaulted so existing constructions are untouched.
 */
data class OfflineLanguageRow(
    val id: String,
    val name: String,
    val state: OfflineModelState,
    val hasOfflineVoice: Boolean = false,
)

/**
 * The ML Kit English pivot's language id (issue #224).
 *
 * Every ML Kit translation model is an `X↔English` pair; there is no standalone
 * `en` pack, and ML Kit reports English as on-device before ANY pack is fetched
 * (a first-run downloaded count of 1). So the English row arrives in the
 * `Downloaded` state like any other pack — except deleting it is a NO-OP (Branch
 * A, `docs/research/issue-224-en-row-delete.md`): English stays in the downloaded
 * set, offline translation is unharmed, and the Download side is unreachable.
 *
 * Owner ruling (2026-08-05): the pivot row is **not hidden** — English is the 59th
 * id in `BundledLanguageCatalog.offlineCapableIds`, so removing it from the list
 * would make the "59 languages" counter (C-11) lie — but it is **non-actionable**:
 * no overflow, no remove, because neither acts on the pivot, and it is never
 * nudged for cleanup (removing it frees nothing).
 *
 * Identified by the tag, not a position: `TranslateLanguage.ENGLISH` is `"en"` and
 * this catalog hands ML Kit tags straight through untranslated, so the pivot's id
 * is exactly `"en"` in every layer that touches it.
 */
internal const val PIVOT_LANGUAGE_ID: String = "en"

/** True only for the ML Kit pivot row, whose controls are stripped (#224). */
internal fun isPivotLanguage(id: String): Boolean = id == PIVOT_LANGUAGE_ID

/** The saved-state key for the open remove question. Namespaced — the handle is shared. */
internal const val KEY_PENDING_REMOVAL = "offline_languages.pending_removal"

/** The saved-state key for a dismissed hygiene nudge, so "Not now" survives a config change / process death. */
internal const val KEY_NUDGE_DISMISSED = "offline_languages.nudge_dismissed"

/**
 * An open "remove this pack?" question, with everything the two sheets need to
 * draw themselves (#130 PR-19).
 *
 * Derived rather than stored: only the language [id] survives process death (a
 * `String` in the `SavedStateHandle`), and the rest is recomputed from the
 * preference seam and the database when the question is restored.
 *
 * @param inUseAsTarget the pack belongs to the language the user is translating
 *   INTO right now, which is the only sense of "in use" either drawn frame has.
 * @param savedCount saved phrases using this language, on either side.
 */
data class PendingPackRemoval(
    val id: String,
    val inUseAsTarget: Boolean,
    val savedCount: Int,
)

/**
 * Everything Manage packs shows, as ONE snapshot, locale-independent on purpose:
 * the names and the alphabetical order are the composable's to apply (via
 * [buildManagePacksSections]), for the same reason the picker localizes in Compose
 * — a `Locale` is a platform read, not ViewModel state. What the ViewModel DOES
 * settle is everything that does not depend on the reader's language: which packs
 * exist and in what state, when each was last used, the aggregate storage, the
 * catalogue counts, and the one instant ("now") the relative dates and the nudge
 * are measured against, captured once per emission so a fling cannot make "used
 * today" flicker to "yesterday" between frames.
 */
@Immutable
data class ManagePacksData(
    val rows: List<OfflineLanguageRow>,
    val usage: Map<String, Long>,
    val targetId: String,
    val storage: StorageCard?,
    val total: Int,
    val capable: Int,
    val nowMillis: Long,
    val nudgeDismissed: Boolean,
    val loading: Boolean,
) {
    companion object {
        /**
         * The pre-data seed: the bundled catalogue has not arrived, so there is
         * nothing to show yet and [loading] is true. It can never be a real
         * device state — the offline catalogue is static and has 59 rows — so an
         * empty [rows] means "not read yet", never "this device has no packs",
         * exactly the distinction the picker's meter draws.
         */
        val Initial =
            ManagePacksData(
                rows = emptyList(),
                usage = emptyMap(),
                targetId = "",
                storage = null,
                total = 0,
                capable = 0,
                nowMillis = 0L,
                nudgeDismissed = false,
                loading = true,
            )
    }
}

/**
 * Manage packs (20b/20f · #130 PR-23) state holder — the rewrite of Screen B into
 * the full management screen. Only offline-capable languages ever reach it;
 * online-only languages live in the picker with a badge and are never managed
 * here.
 *
 * It ASKS four brains and settles the answer: the language catalog + the model
 * manager (what exists and in what state), the usage store (#122 — when each was
 * last PROVEN used, translation-success-stamped, never selection-stamped), and the
 * storage probe (U-5 aggregate bytes). The consent gate (#90) and the remove
 * confirm (#130 PR-19) are unchanged seams carried through from Screen B.
 */
@HiltViewModel
@Suppress("LongParameterList")
class OfflineLanguagesViewModel
    @Inject
    constructor(
        // Twelve collaborators, above detekt's ten — and this IS the anti-god-VM
        // pressure the rev3 ruling names (§4). Manage packs genuinely consumes four
        // brains plus its own consent/remove/nudge seams; the honest option is one
        // wide constructor Dagger can read, not a parameter object that hides the
        // same dependencies from the next reader. A THIRTEENTH collaborator is a
        // finding — split the screen — not a threshold to raise.
        languageRepository: LanguageRepository,
        private val modelManager: OfflineModelManager,
        private val downloadGate: DownloadGate,
        private val downloadPrefs: DownloadPrefsRepository,
        private val translatePrefs: TranslatePrefsRepository,
        private val translations: TranslationRepository,
        private val usageRepository: LanguageUsageRepository,
        private val storageProbe: StorageProbe,
        private val clock: AppClock,
        private val handle: SavedStateHandle,
        private val dispatchers: DispatcherProvider,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        /**
         * Offline-capable catalogue rows with their live model state, and the full
         * catalogue size for the footer's "59 of 194" line.
         *
         * The `onStart { emit(emptyMap()) }` is the same guard Screen B and the
         * picker put on this exact source: `combine` waits for EVERY input, and on
         * a device without Play Services the ML Kit answer may effectively never
         * come — unprefixed, that parked the screen on a wait state forever (the
         * EDGE_CASES dead-end class). Prefixed empty, rows paint at their resting
         * state immediately and flip when the real state arrives.
         */
        private val catalog: StateFlow<CapableCatalog> =
            combine(
                languageRepository.languages(),
                modelManager.modelStates().onStart { emit(emptyMap()) },
            ) { catalog, states ->
                CapableCatalog(
                    rows =
                        catalog
                            .filter(Language::offlineAvailable)
                            .map { language ->
                                OfflineLanguageRow(
                                    id = language.id,
                                    name = language.name,
                                    state = states[language.id] ?: OfflineModelState.NotDownloaded,
                                    hasOfflineVoice = language.hasOfflineVoice,
                                )
                            },
                    total = catalog.size,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), CapableCatalog.Empty)

        /**
         * The aggregate storage card (U-5), or null until the disk has been read.
         *
         * Recomputed only when the on-device pack COUNT changes: `packsBytes()`
         * walks ML Kit's model store file by file, so keying on the count and
         * dropping repeats is a deliberate trigger, not an optimisation — the same
         * discipline the picker's meter keeps. All three probe calls run on IO
         * together (`freeBytes`/`totalBytes` are `StatFs` syscalls, `packsBytes` a
         * directory walk).
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val storage: StateFlow<StorageCard?> =
            catalog
                .map(::onDevicePackCount)
                .distinctUntilChanged()
                .mapLatest { count ->
                    withContext(dispatchers.io) {
                        storageCard(
                            packCount = count,
                            packsBytes = storageProbe.packsBytes(),
                            freeBytes = storageProbe.freeBytes(),
                            totalBytes = storageProbe.totalBytes(),
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), null)

        /**
         * When each language was last PROVEN in use, merged across both roles: a
         * pack is "used" if it was translated INTO or OUT OF, whichever happened
         * more recently. The nudge is about a LANGUAGE nobody uses, not a role, so
         * a language used only as a source is not stale.
         */
        private val usage: StateFlow<Map<String, Long>> =
            combine(
                usageRepository.lastUsed(LanguageRole.SOURCE),
                usageRepository.lastUsed(LanguageRole.TARGET),
            ) { source, target -> mergeLatestUse(source, target) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyMap())

        /**
         * The current TARGET (canonical), for the "IN USE" badge and the in-use
         * removal question. Read through the SAME repository and canonicalisation
         * the picker uses, so a preference persisted before write-side
         * canonicalisation (`iw`, `zh-CN`) still matches a catalog id.
         */
        private val target: StateFlow<String> =
            translatePrefs.targetLang
                .map(LanguageTagResolver::canonicalOrSelf)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), "")

        /** "Not now" on the hygiene nudge — durable so it survives a rotation / process death this session. */
        private val nudgeDismissed: StateFlow<Boolean> =
            handle.getStateFlow(KEY_NUDGE_DISMISSED, false)

        /** The whole screen in one snapshot; the composable localizes and sections it. */
        val uiState: StateFlow<ManagePacksData> =
            combine(catalog, usage, storage, target, nudgeDismissed) { catalog, usage, storage, target, dismissed ->
                ManagePacksData(
                    rows = catalog.rows,
                    usage = usage,
                    targetId = target,
                    storage = storage,
                    total = catalog.total,
                    capable = catalog.rows.size,
                    nowMillis = clock.nowMillis(),
                    nudgeDismissed = dismissed,
                    // The bundled catalogue is never empty, so an empty row list is
                    // the pre-emission frame — a loading placeholder, not the 20f
                    // zero-PACKS empty state (which the composable reads off the
                    // built sections, since a device can be capable-but-pack-less).
                    loading = catalog.rows.isEmpty(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), ManagePacksData.Initial)

        /** "Not now": hide the nudge for this session without touching a pack. */
        fun dismissNudge() {
            handle[KEY_NUDGE_DISMISSED] = true
        }

        /** Language id awaiting the mobile-data consent sheet; null = no sheet. */
        val pendingConsent: StateFlow<String?> = downloadGate.pendingConsent

        /**
         * Sheet 19a's checkbox, exactly as the picker exposes it — the stored
         * `allowMobileData` read from the other end ([alwaysAskOf]). Both screens
         * raise the SAME sheet, so both must answer it the same way.
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
         * One-shot "your download could not even start" notices — the SYNCHRONOUS
         * refusal the manager returns from a pre-flight ([DownloadAttempt.Refused],
         * issues #234/#250). A `Channel`, the one-shot seam the coroutines rule keeps
         * for a screen message, and deliberately NOT the shared state map nor the U-1
         * `PackEvents` channel: a repeat `Failed(STORAGE)` writes a value-EQUAL map so
         * `modelStates()` does not re-emit, and the refusal fires no `PackEvent`, so a
         * Retry on a still-full disk was a silent no-op behind an enabled 48 dp pill
         * (the #250 dead-end). The screen drains [refusals] into a snackbar over the
         * removable packs — the honest "not enough space" the Retry now owes.
         */
        private val refusalEvents = Channel<OfflineModelFailure>(Channel.BUFFERED)

        /** Synchronous download refusals, for the screen's snackbar. See [refusalEvents]. */
        val refusals: Flow<OfflineModelFailure> = refusalEvents.receiveAsFlow()

        /**
         * A suggestion's "Get", or a Retry on a failed row. Metered + no standing
         * permission → ask first, download never starts.
         *
         * Off the main thread (issue #238): `isMetered()` is a synchronous binder
         * IPC and the manager's free-space pre-flight is a `statvfs` syscall, and
         * `viewModelScope` is `Dispatchers.Main.immediate`.
         *
         * The attempt is now CAPTURED, not discarded (#234/#250): a `null` return is
         * the gate only ASKING (the consent sheet is up, nothing to report), and any
         * real attempt is passed to [reportOutcome]. This is the same capture the
         * picker proves at `LanguagePickerViewModel.download`.
         */
        fun download(id: String) {
            viewModelScope.launch {
                val attempt = withContext(dispatchers.io) { downloadGate.requestDownload(id) } ?: return@launch
                reportOutcome(attempt)
            }
        }

        /** Dialog "Download now": THIS download only — the standing pref is untouched. */
        fun downloadAnyway() {
            val consented = downloadGate.consentOnce() ?: return
            viewModelScope.launch {
                val attempt = withContext(dispatchers.io) { downloadGate.downloadConsented(consented) }
                reportOutcome(attempt)
            }
        }

        /**
         * Surface the SYNCHRONOUS half of a download attempt.
         *
         * Only a [DownloadAttempt.Refused] needs a word from this screen. The manager
         * already wrote its `Failed(cause)` onto the row, but a refusal REPEATED for
         * the same reason writes a value-equal map — invisible on the conflating
         * `modelStates()` — and emits no `PackEvent`, so without this the Retry is the
         * #234/#250 silent no-op. A [DownloadAttempt.Started]'s outcome arrives
         * through `modelStates()` (the row turns red, or downloaded) AND the U-1
         * `PackEvents` app snackbar, so reporting it here too would double it;
         * [DownloadAttempt.Ignored] wrote nothing and has nothing to say.
         */
        private suspend fun reportOutcome(attempt: DownloadAttempt) {
            if (attempt is DownloadAttempt.Refused) refusalEvents.send(attempt.cause)
        }

        /** Dialog "Not now" (or dismiss): the row stays as it was — no dead end. */
        fun dismissConsent() = downloadGate.dismiss()

        /**
         * The row ⏹ while Downloading — delete-to-cancel, the verified ML Kit limit
         * (there is no cancel API, so stopping IS deleting the partial model).
         *
         * **Deliberately NOT routed through the remove sheet** [requestRemove]
         * raises: a download in flight is not a pack the user HAS, nothing is being
         * taken away that they had a moment ago, and the ⏹ has always been the way
         * out of a download they no longer want. The ruling asks for the
         * unconfirmed 🗑 to become confirmed; this is the ⏹, and it stays immediate.
         */
        fun stopDownload(id: String) {
            viewModelScope.launch { modelManager.delete(id) }
        }

        /**
         * The open remove question, or null (#130 PR-19). Only the id is durable;
         * `inUseAsTarget` and `savedCount` are DERIVED on every restore, so a
         * question that outlives a process death is answered against the state that
         * exists now.
         *
         * `distinctUntilChanged` on the target keeps an unrelated preference write
         * from re-running the count query underneath an open sheet.
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
                    PendingPackRemoval(
                        id = id,
                        inUseAsTarget = target == id,
                        savedCount = savedCountOf(id),
                    )
                }

        /**
         * Best-effort count. A database that cannot answer must not stop the user
         * removing a pack: zero renders as an ABSENT line, which is what a user who
         * has saved nothing sees anyway — a missing reassurance, never a false one.
         *
         * `Throwable`, not `Exception` (issue #236): this query is Room, every
         * statement ends in a `native` method on `SQLiteConnection`, and a JNI link
         * that cannot be satisfied raises `UnsatisfiedLinkError` — a `LinkageError`,
         * so an `Error`, so NOT an `Exception` (full reasoning `TextViewModel.kt`
         * #195). `CancellationException` is rethrown FIRST and by name because it IS
         * an `Exception`: widening protects it not at all, only the rethrow does.
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

        /**
         * 20c "Use as target now" — make this pack's language the one the app
         * translates INTO, written through the SAME [TranslatePrefsRepository] (and
         * therefore the same DataStore key) the picker's target selection uses, so the
         * composer's chip and this screen agree by construction. The repository
         * canonicalises on the write side, so the raw catalog id is handed straight
         * through exactly as `LanguagePickerViewModel.select` does.
         *
         * On [appScope], not `viewModelScope`, for the reason the picker's `select`
         * proved by co-verify and [confirmRemove] repeats here: this is a write the
         * user explicitly asked for, and the screen can go away underneath it — a Back
         * press right after the tap pops [LanguagesNavKey], the nav decorator clears
         * this ViewModel's store, and DataStore's `edit` runs in the CALLER's context,
         * so a `viewModelScope` write would be DROPPED on the pop and the target would
         * silently not change. It does NOT stamp the per-role recents the picker does:
         * "Recently used as target" is fed by the picker's own selections, and adding a
         * second writer here is out of this sheet's scope (#130 PR-24).
         */
        fun useAsTarget(id: String) {
            appScope.launch { translatePrefs.setTargetLang(id) }
        }

        /** Row overflow / suggestion 🗑: ask first. Nothing is deleted until [confirmRemove]. */
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
         * Reads the id from the durable handle rather than a parameter, so a
         * confirm can only ever remove the pack the sheet is asking about, and a
         * second tap on an answered sheet finds nothing to do.
         *
         * **It writes no language preference.** The drawn 19g says the target
         * switches to English; it does not (PR-19). Removing the model returns the
         * row to `NotDownloaded`; the selection is untouched and the language keeps
         * translating through the waterfall's online tiers.
         *
         * On [appScope], not `viewModelScope`: this is a write the user explicitly
         * asked for and the screen can go away underneath it.
         */
        fun confirmRemove() {
            val id = handle.get<String>(KEY_PENDING_REMOVAL) ?: return
            handle[KEY_PENDING_REMOVAL] = null
            appScope.launch { modelManager.delete(id) }
        }

        /**
         * 20e "Free up space" — remove EVERY selected stale pack in one confirm
         * (#130 PR-25). It is the batch form of [confirmRemove] and reuses the exact
         * same per-pack path ([OfflineModelManager.delete]); there is no bulk-delete
         * API and one would only re-implement the single delete N times less
         * carefully. Each id is deleted, not just the first — the mutation this
         * guards against (`ids.first()`) is pinned in `OfflineLanguagesViewModelTest`.
         *
         * On [appScope], not `viewModelScope`, for the reason [confirmRemove] and
         * `useAsTarget` give: the 20e sheet dismisses the instant the user confirms
         * and the screen can be popped underneath the deletes, so a `viewModelScope`
         * launch would be cancelled mid-batch and silently leave packs behind. The
         * deletes are launched in one coroutine, in order, so the appScope holds a
         * single child until the whole batch is done.
         *
         * There is no re-download here and so no #234 trap to re-open: this only
         * removes. A pack the user wants back is fetched again through the honest
         * [download] path (PR-23's `reportOutcome`), never a discarding shortcut.
         */
        fun removePacks(ids: List<String>) {
            if (ids.isEmpty()) return
            appScope.launch { ids.forEach { modelManager.delete(it) } }
        }
    }

/** Offline-capable rows with their state, plus the full catalogue size for the footer. */
@Immutable
internal data class CapableCatalog(
    val rows: List<OfflineLanguageRow>,
    val total: Int,
) {
    companion object {
        val Empty = CapableCatalog(rows = emptyList(), total = 0)
    }
}

/** Packs actually on the device — Downloaded or mid-delete (still on disk), the storage card's count. */
internal fun onDevicePackCount(catalog: CapableCatalog): Int =
    catalog.rows.count { it.state == OfflineModelState.Downloaded || it.state == OfflineModelState.Deleting }

/**
 * Per-language latest use across both roles — the larger of the two stamps, or
 * whichever exists. A language used as a target last week and a source in April
 * is "used last week"; the nudge should not call it stale on the older half.
 */
internal fun mergeLatestUse(
    source: Map<String, Long>,
    target: Map<String, Long>,
): Map<String, Long> {
    if (source.isEmpty()) return target
    if (target.isEmpty()) return source
    val merged = HashMap<String, Long>(source)
    target.forEach { (id, millis) ->
        merged[id] = maxOf(merged[id] ?: Long.MIN_VALUE, millis)
    }
    return merged
}
