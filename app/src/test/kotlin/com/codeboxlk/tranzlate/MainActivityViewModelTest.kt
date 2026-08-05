package com.codeboxlk.tranzlate

import com.codeboxlk.tranzlate.core.model.Language
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.core.model.ModeId
import com.codeboxlk.tranzlate.core.model.ThemeMode
import com.codeboxlk.tranzlate.core.model.ThemeSettings
import com.codeboxlk.tranzlate.core.testing.FakeConnectivityMonitor
import com.codeboxlk.tranzlate.core.testing.TestDispatcherRule
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.ThemePrefsRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Sheet 19h's host — #130 PR-20. Two halves:
 *
 * - [offlinePackMissingOf] is pure, so the gate (offline · a wall · packs to
 *   offer, minus the source) is pinned without the Activity's scope. Mutations
 *   decided BEFORE the assertions (rule 11), each reddened its test:
 *   - drop the `online` gate → `...online again...` goes non-null;
 *   - drop `it.id != source` → `...never offered...` starts offering the source;
 *   - return an empty [OfflinePackMissing] instead of null → `...no sheet, not an
 *     empty one...` goes non-null.
 * - The integration tests drive a real [FakeConnectivityMonitor] end-to-end, which
 *   is the "offline trigger (FakeConnectivityMonitor)" the ruling names: the sheet
 *   appears only while offline, and Use switches the target and clears it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val dispatcherRule = TestDispatcherRule(dispatcher)

    private val catalog =
        listOf(
            Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = true),
            Language("en", "English", offlineAvailable = true, offlineDownloaded = true),
            Language("fr", "French", offlineAvailable = true, offlineDownloaded = false),
            Language("de", "German", offlineAvailable = true, offlineDownloaded = false),
        )

    // ---- offlinePackMissingOf (pure) ---------------------------------------

    @Test
    fun `no wall means no sheet`() {
        assertThat(offlinePackMissingOf(pending = null, online = false, languages = catalog, source = "en"))
            .isNull()
    }

    @Test
    fun `online again means no sheet - the online tiers can translate`() {
        assertThat(offlinePackMissingOf(pending = "fr", online = true, languages = catalog, source = "de"))
            .isNull()
    }

    @Test
    fun `offline with a wall offers the on-device packs`() {
        val request = offlinePackMissingOf(pending = "fr", online = false, languages = catalog, source = "de")
        assertThat(request).isNotNull()
        assertThat(request!!.missingLangId).isEqualTo("fr")
        assertThat(request.onDeviceLangIds).containsExactly("es", "en").inOrder()
    }

    @Test
    fun `the current source is never offered`() {
        // en is on device AND the source; offering it would make the same-language
        // pair 19m guards.
        val request = offlinePackMissingOf(pending = "fr", online = false, languages = catalog, source = "en")
        assertThat(request!!.onDeviceLangIds).containsExactly("es")
    }

    @Test
    fun `nothing on device means no sheet, not an empty one`() {
        val allOnline =
            listOf(
                Language("es", "Spanish", offlineAvailable = true, offlineDownloaded = false),
                Language("en", "English", offlineAvailable = true, offlineDownloaded = false),
            )
        assertThat(offlinePackMissingOf(pending = "fr", online = false, languages = allOnline, source = "de"))
            .isNull()
    }

    // ---- the ViewModel end to end (FakeConnectivityMonitor) ----------------

    @Test
    fun `the sheet appears only while offline`() =
        runTest(dispatcher) {
            val connectivity = FakeConnectivityMonitor(initiallyOnline = false)
            val vm = viewModel(connectivity = connectivity)
            backgroundScope.launch { vm.offlinePackMissing.collect {} }
            vm.onOfflinePackMissing("fr")
            advanceUntilIdle()
            assertThat(vm.offlinePackMissing.value).isNotNull()

            // Connectivity returns: the online tiers can translate, so the refusal
            // is moot and the sheet must go.
            connectivity.state.value = true
            advanceUntilIdle()
            assertThat(vm.offlinePackMissing.value).isNull()
        }

    @Test
    fun `Use switches the target and dismisses the sheet`() =
        runTest(dispatcher) {
            val prefs = FakeTranslatePrefs(source = "de")
            val vm = viewModel(prefs = prefs)
            backgroundScope.launch { vm.offlinePackMissing.collect {} }
            vm.onOfflinePackMissing("fr")
            advanceUntilIdle()
            assertThat(vm.offlinePackMissing.value).isNotNull()

            vm.useLanguage("es")
            advanceUntilIdle()

            assertThat(prefs.target.value).isEqualTo("es")
            assertThat(vm.offlinePackMissing.value).isNull()
        }

    private fun viewModel(
        connectivity: FakeConnectivityMonitor = FakeConnectivityMonitor(initiallyOnline = false),
        languages: List<Language> = catalog,
        prefs: FakeTranslatePrefs = FakeTranslatePrefs(source = "de"),
    ) = MainActivityViewModel(
        themePrefs = FakeThemePrefs(),
        connectivity = connectivity,
        languageRepository = FakeLangRepo(languages),
        translatePrefs = prefs,
    )
}

private class FakeThemePrefs : ThemePrefsRepository {
    override val settings: Flow<ThemeSettings> = flowOf(ThemeSettings.Default)

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setDynamicColor(enabled: Boolean) = Unit
}

private class FakeLangRepo(
    private val catalog: List<Language>,
) : LanguageRepository {
    override fun languages(): Flow<List<Language>> = flowOf(catalog)

    override fun recentSelections(role: LanguageRole): Flow<Map<String, Long>> = flowOf(emptyMap())

    override suspend fun setLastUsed(
        languageId: String,
        role: LanguageRole,
        atMillis: Long,
    ) = Unit
}

private class FakeTranslatePrefs(
    source: String = "en",
) : TranslatePrefsRepository {
    val src = MutableStateFlow(source)
    val target = MutableStateFlow("fr")
    val mode = MutableStateFlow(ModeId.AUTO)

    override val sourceLang: Flow<String> = src
    override val targetLang: Flow<String> = target
    override val textMode: Flow<ModeId> = mode

    override suspend fun setSourceLang(id: String) {
        src.value = id
    }

    override suspend fun setTargetLang(id: String) {
        target.value = id
    }

    override suspend fun setLanguagePair(
        sourceId: String,
        targetId: String,
    ) {
        src.value = sourceId
        target.value = targetId
    }
}
