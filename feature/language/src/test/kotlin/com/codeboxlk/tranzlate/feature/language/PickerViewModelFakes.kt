package com.codeboxlk.tranzlate.feature.language

import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.DownloadAttempt
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.codeboxlk.tranzlate.domain.translate.PackEvent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow

/**
 * The picker ViewModel's collaborators, faked once for the two suites that drive
 * it — `LanguagePickerViewModelTest` (the ask-seams) and
 * `PackFailureSheetRaisingTest` (#130 PR-18's failure sheets).
 *
 * They were nested inside the first of those until PR-18, and moved out for a
 * reason worth recording rather than for tidiness: adding the sheet tests to
 * that class tripped detekt's `LargeClass`. The choice was to split the SUITE or
 * to suppress the rule, and a suppression here would have been the second copy
 * of these fakes waiting to happen — which is the same defect shape PR-18 exists
 * to remove from the production code.
 *
 * Nothing about them changed in the move: same bodies, same journal, same
 * per-role recents maps.
 */
internal class FakeLanguageRepository(
    /** Shared call journal — proves cross-fake ordering (stamp before choice write). */
    private val journal: MutableList<String>? = null,
) : LanguageRepository {
    val catalog =
        MutableStateFlow(
            listOf(
                Language("en", "English", offlineAvailable = true, offlineDownloaded = false),
                Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
            ),
        )
    val lastUsed = mutableListOf<Triple<String, LanguageRole, Long>>()

    /** Set to make the stamp fail the way a full disk or a locked DB does. */
    var failWith: Throwable? = null

    /**
     * Read at stamp time. Lets a test assert what the CHOICE store held at
     * the moment the stamp ran — the only way to tell "choice first, in one
     * coroutine" apart from "two launches that happened to land in order".
     */
    var choiceAtStampTime: (() -> String)? = null
    var observedChoice: String? = null

    /**
     * Per-role recents, kept as two independent maps precisely because the
     * production store keeps them apart: a fake that served one map for
     * both roles could not tell a target-scoped section from a merged one,
     * which is the whole claim 16a's header makes.
     */
    val sourceRecents = MutableStateFlow<Map<String, Long>>(emptyMap())
    val targetRecents = MutableStateFlow<Map<String, Long>>(emptyMap())

    override fun languages(): Flow<List<Language>> = catalog

    override fun recentSelections(role: LanguageRole): Flow<Map<String, Long>> =
        when (role) {
            LanguageRole.SOURCE -> sourceRecents
            LanguageRole.TARGET -> targetRecents
        }

    override suspend fun setLastUsed(
        languageId: String,
        role: LanguageRole,
        atMillis: Long,
    ) {
        observedChoice = choiceAtStampTime?.invoke()
        failWith?.let { throw it }
        lastUsed += Triple(languageId, role, atMillis)
        journal?.add("stamp:$languageId")
    }
}

/**
 * The selection store as the picker sees it — same interface the composer's
 * `TextViewModel` injects, so "same repository methods" is literal here.
 * Values are stored RAW on purpose: canonicalising is the production
 * implementation's job (write side) and this ViewModel's job (read side),
 * and a fake that quietly fixed ids would hide a regression in either.
 */
internal class FakeTranslatePrefs(
    private val journal: MutableList<String>? = null,
) : TranslatePrefsRepository {
    val source = MutableStateFlow("en")
    val target = MutableStateFlow("fr")

    override val sourceLang: Flow<String> = source
    override val targetLang: Flow<String> = target
    override val textMode: Flow<ModeId> = MutableStateFlow(ModeId.AUTO)

    override suspend fun setSourceLang(id: String) {
        source.value = id
        journal?.add("source:$id")
    }

    override suspend fun setTargetLang(id: String) {
        target.value = id
        journal?.add("target:$id")
    }

    override suspend fun setLanguagePair(
        sourceId: String,
        targetId: String,
    ) {
        source.value = sourceId
        target.value = targetId
        journal?.add("pair:$sourceId>$targetId")
    }
}

internal class PickerModelManager : OfflineModelManager {
    val states = MutableStateFlow<Map<String, OfflineModelState>>(emptyMap())
    val downloads = mutableListOf<String>()
    val deletes = mutableListOf<String>()

    /**
     * What `download()` does to the state map before it returns — the real
     * manager's synchronous half (`takeTransient`: `Downloading`, or
     * `Failed(STORAGE)` when the free-space pre-flight refuses).
     *
     * Since issue #234 it also decides what the call ANSWERS, because the real
     * manager's pre-flight answer travels by return value: a conflating state map
     * cannot carry "the same refusal happened again". A hook that writes
     * `Failed(STORAGE)` and answers `Started` would be modelling a manager that
     * does not exist, so the two are set together on purpose.
     *
     * Default: touch nothing and answer `Started` — an enqueued transfer whose
     * outcome the test then publishes with [put], which is what every test
     * written before #130 PR-18 assumed.
     */
    var onDownload: (String) -> DownloadAttempt = { DownloadAttempt.Started }

    /** Publish a state for one tag, as the manager's own merge would. */
    fun put(
        tag: String,
        state: OfflineModelState,
    ) {
        states.value = states.value + (tag to state)
    }

    override fun modelStates(): Flow<Map<String, OfflineModelState>> = states

    override val packEvents: SharedFlow<PackEvent> = MutableSharedFlow() // picker fake never emits

    override suspend fun download(languageTag: String): DownloadAttempt {
        downloads += languageTag
        return onDownload(languageTag)
    }

    override suspend fun delete(languageTag: String) {
        deletes += languageTag
    }
}

/** Stands in for ML Kit never answering — the flow that emits nothing, ever. */
internal class SilentPickerModelManager : OfflineModelManager {
    override fun modelStates(): Flow<Map<String, OfflineModelState>> = flow { awaitCancellation() }

    override val packEvents: SharedFlow<PackEvent> = MutableSharedFlow() // picker fake never emits

    override suspend fun download(languageTag: String) = DownloadAttempt.Started

    override suspend fun delete(languageTag: String) = Unit
}
