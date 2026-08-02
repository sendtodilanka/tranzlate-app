package com.codeboxlk.tranzlate.navigation

import androidx.navigation3.runtime.NavKey
import com.codeboxlk.tranzlate.core.model.LanguageRole
import com.codeboxlk.tranzlate.feature.language.PickerHost
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * What a language chip tap does, and what the card's docked action does — the
 * two decisions #130 PR-16 puts in the shell.
 *
 * Both live in `TranzlateApp.kt` as named functions rather than as lines inside
 * a lambda, for the reason `popEntry` does: this repo has no instrumentation
 * harness in CI (#40, #111), and a rule that only exists as two adjacent
 * statements is a rule no test can name.
 */
class PickerHostRoutingTest {
    // ---- the chip tap -------------------------------------------------------

    /** On a phone the picker is a destination, and the key carries the side. */
    @Test
    fun `a nav-entry host pushes the picker`() {
        val pushed = mutableListOf<NavKey>()
        var dialogRole: LanguageRole? = null

        openLanguagePicker(
            role = LanguageRole.TARGET,
            host = PickerHost.NAV_ENTRY,
            openDialog = { dialogRole = it },
            navigate = pushed::add,
        )

        assertThat(pushed).containsExactly(LanguagePickerNavKey(forSource = false))
        assertThat(dialogRole).isNull()
    }

    @Test
    fun `a nav-entry host pushes the source side with forSource true`() {
        val pushed = mutableListOf<NavKey>()

        openLanguagePicker(
            role = LanguageRole.SOURCE,
            host = PickerHost.NAV_ENTRY,
            openDialog = {},
            navigate = pushed::add,
        )

        assertThat(pushed).containsExactly(LanguagePickerNavKey(forSource = true))
    }

    /**
     * On a tablet the card is raised and **nothing is pushed**.
     *
     * The second half is the load-bearing one. Doing both would leave the card
     * floating over a picker destination, and dismissing it would reveal the
     * full-screen list the card exists to replace — a state the user cannot get
     * out of without pressing back twice for reasons nothing on screen explains.
     */
    @Test
    fun `a dialog host raises the card and pushes nothing`() {
        val pushed = mutableListOf<NavKey>()
        var dialogRole: LanguageRole? = null

        openLanguagePicker(
            role = LanguageRole.SOURCE,
            host = PickerHost.DIALOG,
            openDialog = { dialogRole = it },
            navigate = pushed::add,
        )

        assertThat(dialogRole).isEqualTo(LanguageRole.SOURCE)
        assertWithMessage("a card over a pushed picker is a screen the user cannot leave once")
            .that(pushed)
            .isEmpty()
    }

    @Test
    fun `a dialog host carries the role it was opened for`() {
        var dialogRole: LanguageRole? = null

        openLanguagePicker(
            role = LanguageRole.TARGET,
            host = PickerHost.DIALOG,
            openDialog = { dialogRole = it },
            navigate = {},
        )

        assertThat(dialogRole).isEqualTo(LanguageRole.TARGET)
    }

    // ---- the docked action, and its order ------------------------------------

    /**
     * **`dialogVisible = false` THEN `push(LanguagesNavKey)`** — the ruling names
     * this order in §2 and it is the whole of what this function does.
     *
     * The card is not a back-stack entry, so the push does not dismiss it. Push
     * first and Manage packs arrives underneath a language picker that is still
     * floating over it.
     */
    @Test
    fun `manage packs dismisses the card before it navigates`() {
        val order = mutableListOf<String>()

        manageLanguagePacks(
            dismissDialog = { order += "dismiss" },
            navigate = { order += "navigate:$it" },
        )

        assertWithMessage("the card is not on the back stack, so a push does not dismiss it")
            .that(order)
            .containsExactly("dismiss", "navigate:$LanguagesNavKey")
            .inOrder()
    }

    /** …and it goes to Manage packs, not to somewhere that merely compiles. */
    @Test
    fun `manage packs navigates to the languages destination`() {
        val pushed = mutableListOf<NavKey>()

        manageLanguagePacks(dismissDialog = {}, navigate = pushed::add)

        assertThat(pushed).containsExactly(LanguagesNavKey)
    }

    // ---- keep-host-until-closed ---------------------------------------------

    /**
     * **The host is decided once, when the chip is tapped, and never asked again
     * while the card is open** (ruling §2: "open-අතරතුර window-class change →
     * keep-host-until-closed").
     *
     * The obvious way to write the shell is to gate the card on the window as
     * well as on the saved flag, and it is wrong in a way nothing else here would
     * catch: unfolding a foldable, or dragging a split-screen divider, would then
     * close a card the user was reading with a search half typed. The saved flag
     * is the only term, and this counts the reads that would break it.
     *
     * A source rule, and honest about being one: rendering `TranzlateApp` needs a
     * Hilt-instrumented application and this repo runs no instrumented tests in
     * CI (#40). It is precise rather than fuzzy — the window answer is read
     * exactly ONCE, to decide where a NEW picker goes — so a second read anywhere
     * in the shell reddens it, whatever it was for.
     */
    @Test
    fun `the shell reads the window answer once, to open with`() {
        val shell = shellSource()

        // Never vacuous: a renamed shell fails here rather than counting zero.
        assertThat(shell).contains("PickerDialogHost(")

        // The declaration is not a read, so it comes out first — otherwise the
        // expected count is "one more than the reads", which is a number nobody
        // can check against the rule it is supposed to express.
        val body = shell.lines().filterNot { it.contains("val hostForNextPicker") }.joinToString("\n")
        val reads = Regex("""\bhostForNextPicker\b""").findAll(body).count()
        assertWithMessage(
            "TranzlateApp reads the window's host answer $reads times. One is the chip tap. A " +
                "second would gate the OPEN card on the window and close it under a user who " +
                "unfolded the device mid-search.",
        ).that(reads)
            .isEqualTo(1)
    }

    /** The shell's shipped body — comments and string literals removed. */
    private fun shellSource(): String {
        val checkoutRoot =
            generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { java.io.File(it, "settings.gradle.kts").isFile }
        return checkoutRoot
            .resolve("app/src/main/kotlin/com/codeboxlk/tranzlate/navigation/TranzlateApp.kt")
            .readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//.*"), "")
            .replace(Regex(""""(\\.|[^"\\])*""""), "\"\"")
    }
}
