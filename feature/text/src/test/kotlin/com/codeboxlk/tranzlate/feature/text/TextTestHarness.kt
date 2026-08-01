package com.codeboxlk.tranzlate.feature.text

import androidx.lifecycle.SavedStateHandle
import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.testing.FakeClock
import com.codeboxlk.tranzlate.core.testing.FakeFeatureAccess
import com.codeboxlk.tranzlate.core.testing.FakeLanguageUsageRepository
import com.codeboxlk.tranzlate.core.testing.FakeRemoteConfig
import com.codeboxlk.tranzlate.core.testing.FakeTranslationRepository
import com.codeboxlk.tranzlate.core.testing.FakeTranslator
import com.codeboxlk.tranzlate.core.testing.FakeUsagePolicy
import com.codeboxlk.tranzlate.core.testing.TestDispatcherProvider
import com.codeboxlk.tranzlate.domain.ads.AdsCoordinator
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.translate.TranslateTextUseCase
import com.codeboxlk.tranzlate.domain.translate.Translator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher

/**
 * The Text vertical's shared test harness — one fake per seam, and the builder
 * that wires them into a [TextViewModel].
 *
 * Lifted out of `TextViewModelTest` when the speech-lifetime tests (#149 / #159)
 * gave that class a second concern and pushed it past detekt's `LargeClass`.
 * Same package, so nothing at the call sites changed: the fakes are still
 * referenced by their simple names.
 */

internal class RecordingAdsCoordinator : AdsCoordinator {
    var completedCount = 0

    override suspend fun onTranslationCompleted() {
        completedCount++
    }
}

internal class FakeResultSpeaker : ResultSpeaker {
    val state = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val speaking: kotlinx.coroutines.flow.StateFlow<Boolean> = state
    var speaks = 0
        private set
    var stops = 0
        private set
    var lastLanguage: String? = null

    /** What the platform will answer once the bind reports. */
    var outcome = SpeakOutcome.STARTED

    /**
     * How long the engine takes to finish binding (issue #159, block 2). The
     * real one measured 478-601ms, and a cache hit can put the result on
     * screen inside that window — so `speak` SUSPENDS here exactly as the
     * adapter does, instead of answering "unavailable" for an engine that is
     * on its way.
     */
    var bindDelayMs = 0L

    /**
     * Issue #149 — the real adapter's [ResultSpeaker.prepare] binds a system
     * speech service and [ResultSpeaker.release] gives it back, so a fake
     * that only counted `speak` could not tell a released engine from a
     * held one. [held] is what the platform actually pays for.
     */
    var prepares = 0
        private set
    var releases = 0
        private set
    var held = false
        private set

    override fun prepare() {
        if (held) return // idempotent, like the adapter
        prepares++
        held = true
    }

    override fun release() {
        if (!held) return
        releases++
        held = false
        state.value = false
    }

    override suspend fun speak(
        text: String,
        languageTag: String,
    ): SpeakOutcome {
        prepare()
        if (bindDelayMs > 0) delay(bindDelayMs)
        // Released while we waited — the adapter reports this rather than a
        // failure, because the user is no longer looking at the request.
        if (!held) return SpeakOutcome.CANCELLED
        if (outcome != SpeakOutcome.STARTED) return outcome
        speaks++
        lastLanguage = languageTag
        state.value = true
        return SpeakOutcome.STARTED
    }

    override fun stop() {
        stops++
        state.value = false
    }
}

internal class FakeTranslatePrefsRepository : TranslatePrefsRepository {
    val source = MutableStateFlow("en")
    val target = MutableStateFlow("fr")
    val mode = MutableStateFlow(ModeId.AUTO)

    override val sourceLang: Flow<String> = source
    override val targetLang: Flow<String> = target
    override val textMode: Flow<ModeId> = mode

    override suspend fun setSourceLang(id: String) {
        source.value = id
    }

    override suspend fun setTargetLang(id: String) {
        target.value = id
    }

    override suspend fun setLanguagePair(
        sourceId: String,
        targetId: String,
    ) {
        source.value = sourceId
        target.value = targetId
    }
}

internal class FakeLanguageRepository(
    private val catalog: List<Language> = DEFAULT_CATALOG,
) : LanguageRepository {
    override fun languages(): Flow<List<Language>> = flowOf(catalog)

    override suspend fun setLastUsed(
        languageId: String,
        role: LanguageRole,
        atMillis: Long,
    ) = Unit

    companion object {
        val DEFAULT_CATALOG =
            listOf(
                Language("en", "English", offlineAvailable = true, offlineDownloaded = false),
                Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
            )
    }
}

/**
 * A [TextViewModel] with every seam faked. `dispatcher` is passed in rather than
 * created here so a test's own `TestDispatcherRule` stays the single scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList") // the test builder aggregates one fake per seam
internal fun textViewModel(
    dispatcher: TestDispatcher,
    translator: Translator = FakeTranslator(),
    prefs: FakeTranslatePrefsRepository = FakeTranslatePrefsRepository(),
    clock: FakeClock = FakeClock(),
    handle: SavedStateHandle = SavedStateHandle(),
    usage: FakeUsagePolicy = FakeUsagePolicy(left = 5),
    access: FakeFeatureAccess = FakeFeatureAccess(),
    repository: FakeTranslationRepository = FakeTranslationRepository(),
    speaker: FakeResultSpeaker = FakeResultSpeaker(),
    catalog: List<Language> = FakeLanguageRepository.DEFAULT_CATALOG,
): TextViewModel {
    val useCase =
        TranslateTextUseCase(
            translator,
            access,
            usage,
            RecordingAdsCoordinator(),
            repository,
            FakeLanguageUsageRepository(),
            clock,
            CoroutineScope(SupervisorJob() + dispatcher),
        )
    return TextViewModel(
        translateText = useCase,
        prefs = prefs,
        translationRepository = repository,
        languageRepository = FakeLanguageRepository(catalog),
        usagePolicy = usage,
        featureAccess = access,
        config = FakeRemoteConfig(),
        dispatchers = TestDispatcherProvider(dispatcher),
        clock = clock,
        speaker = speaker,
        savedStateHandle = handle,
    )
}
