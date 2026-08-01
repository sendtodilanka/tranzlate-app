package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.core.model.OfflineModelState
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

/**
 * THE issue-#90 consent matrix — one suite, where the rule now lives. The
 * picker and the offline manager each carried their own copy of both the gate
 * and this matrix until #130 PR-6/PR-7 put them in one module and made the
 * duplication visible; two copies of a consent rule is two places for it to
 * drift, and the half that drifts spends the user's mobile data.
 *
 * Every cell asserts BOTH halves — what the download manager was asked to do,
 * and what the dialog was left showing. A cell that only checked one of them
 * would pass a gate that raised the question and downloaded anyway.
 */
class DownloadGateTest {
    private val connectivity = FakeConnectivityMonitor()
    private val prefs = FakeDownloadPrefsRepository()
    private val manager = RecordingModelManager()
    private val gate = DownloadGate(connectivity, prefs, manager)

    // ---- Wi-Fi ---------------------------------------------------------------

    @Test
    fun `an unmetered connection never asks - it just downloads`() =
        runTest {
            connectivity.metered = false

            gate.requestDownload("de")

            assertThat(manager.downloads).containsExactly("de")
            assertThat(gate.pendingConsent.value).isNull()
        }

    // ---- metered, no standing permission -------------------------------------

    @Test
    fun `metered without permission raises the question and starts NOTHING`() =
        runTest {
            connectivity.metered = true
            prefs.state.value = false

            gate.requestDownload("de")

            assertThat(manager.downloads).isEmpty()
            assertThat(gate.pendingConsent.value).isEqualTo("de")
        }

    // ---- metered, standing permission ----------------------------------------

    @Test
    fun `the standing permission from Settings skips the question`() =
        runTest {
            connectivity.metered = true
            prefs.state.value = true

            gate.requestDownload("de")

            assertThat(manager.downloads).containsExactly("de")
            assertThat(gate.pendingConsent.value).isNull()
        }

    // ---- "Download once" -----------------------------------------------------

    /**
     * The cell a careless refactor breaks in silence. "Download once" answers
     * for THIS tap only: it must start the download, close the dialog, and
     * leave the standing preference exactly where it was — so the NEXT metered
     * tap asks again. Fold the one-off answer into the standing preference and
     * every later download quietly bills the user's data plan, with nothing in
     * the UI to say so.
     */
    @Test
    fun `Download once covers this tap only - the standing preference is untouched`() =
        runTest {
            connectivity.metered = true
            gate.requestDownload("de")

            val consented = gate.consentOnce()
            assertThat(consented?.id).isEqualTo("de")
            gate.downloadConsented(consented!!)

            assertThat(manager.downloads).containsExactly("de")
            assertThat(gate.pendingConsent.value).isNull()
            assertThat(prefs.allowMobileData.first()).isFalse()

            // The proof that "once" meant once: the next one asks again, and
            // still downloads nothing until it is answered.
            gate.requestDownload("fr")

            assertThat(gate.pendingConsent.value).isEqualTo("fr")
            assertThat(manager.downloads).containsExactly("de")
        }

    /** A second tap on a dialog already answered has nothing left to consent to. */
    @Test
    fun `consenting twice downloads once`() =
        runTest {
            connectivity.metered = true
            gate.requestDownload("de")

            gate.consentOnce()?.let { gate.downloadConsented(it) }
            gate.consentOnce()?.let { gate.downloadConsented(it) }

            assertThat(manager.downloads).containsExactly("de")
        }

    // ---- dismiss -------------------------------------------------------------

    /**
     * "Wait for Wi-Fi" is a no-op with the dialog closed — no download, no
     * spinner, no half-answered state left behind. The row is exactly as it was
     * and the user can tap it again, which is what the second half asserts:
     * a dismissal that quietly recorded consent would let the re-tap through.
     */
    @Test
    fun `dismissing downloads nothing and leaves the row re-tappable`() =
        runTest {
            connectivity.metered = true
            gate.requestDownload("de")

            gate.dismiss()

            assertThat(manager.downloads).isEmpty()
            assertThat(gate.pendingConsent.value).isNull()

            gate.requestDownload("de")

            assertThat(manager.downloads).isEmpty()
            assertThat(gate.pendingConsent.value).isEqualTo("de")
        }

    // ---- the structural rules ------------------------------------------------

    /**
     * The two invariants the extraction's safety argument rests on, checked
     * where they are actually written — in the gate's SOURCE.
     *
     *  1. **No scope of its own.** A shared gate that launched on a scope it
     *     owned would decide the lifetime of every screen's downloads at once —
     *     the defect class that blocked #130 PR-4, where a write moved onto a
     *     scope that dies.
     *  2. **No Hilt scope annotation.** One instance per state holder is the
     *     reason a question raised on the picker stays on the picker; a
     *     `@Singleton` (or any `@…Scoped`, or `@Reusable`) would carry a
     *     half-answered dialog into Settings → Offline languages, where
     *     "Download once" would spend mobile data on a language nobody asked
     *     for there.
     *
     * This replaces a reflection check over `DownloadGate::class.java
     * .constructors`, which read constructor PARAMETERS and so was green on
     * both of the mutations a co-verify lens actually wrote: a
     * `CoroutineScope` held as a private FIELD, and `@Singleton` on the class.
     * The mutations were picked first this time and the check written to fail
     * them (mandatory rule 11, third cause) — see the PR body for the four runs.
     *
     * Reading the file instead of Konsist's project scope is deliberate: #163
     * has a Konsist gate passing locally and failing in CI on the same commit,
     * cause still undiagnosed, so a Konsist result from a worktree is not
     * evidence. One named file, read directly, gives the same answer everywhere.
     * The rule is scoped to this ONE file rather than all of `:core:domain`,
     * because `TranslateTextUseCase` legitimately takes an injected
     * `@ApplicationScope CoroutineScope` and launches on it (:71, :180) — a
     * module-wide ban would be red on correct code.
     */
    @Test
    fun `the gate owns no scope - neither a coroutine one nor a Hilt one`() {
        val checkoutRoot =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        // Comments come out first: this is a rule about CODE, so the gate's
        // KDoc stays free to explain why it has no scope and to name the
        // `downloadScope` the transfer really runs on.
        val code =
            checkoutRoot
                .resolve(GATE_SOURCE)
                .readText()
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("//.*"), "")

        // Never vacuous: a moved, renamed or unreadable gate fails here instead
        // of satisfying every "does not contain" below by being empty.
        assertThat(code).contains("class DownloadGate")

        listOf("Scope", "launch", "async", "Job", "@Singleton", "@Reusable").forEach { banned ->
            assertWithMessage("DownloadGate.kt names `$banned` in code — see this test's KDoc")
                .that(code)
                .doesNotContain(banned)
        }
    }
}

private const val GATE_SOURCE =
    "core/domain/src/main/kotlin/com/codeboxlk/tranzlate/domain/translate/DownloadGate.kt"

/** Records what the Translation brain was actually asked to download. */
private class RecordingModelManager : OfflineModelManager {
    val downloads = mutableListOf<String>()

    override fun modelStates(): Flow<Map<String, OfflineModelState>> = flowOf(emptyMap())

    override suspend fun download(languageTag: String) {
        downloads += languageTag
    }

    override suspend fun delete(languageTag: String) = Unit
}
