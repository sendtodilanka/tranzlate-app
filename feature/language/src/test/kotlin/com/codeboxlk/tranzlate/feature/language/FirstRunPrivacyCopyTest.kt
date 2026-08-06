package com.codeboxlk.tranzlate.feature.language

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Ruling ④ (rev3 ruling :199, #130 PR-28): the app may not state a privacy claim
 * a shipping flavor cannot honour.
 *
 * The `tranzlate` brand's translation waterfall is MLKit-offline → GOT (online) →
 * GCT (online cloud) — `RealTranslator.kt:41-42`, and the foundation spec's own
 * per-engine privacy table says the two online tiers send "text to the cloud"
 * (`docs/specs/02-translation-engines-and-language-mgmt.md:20`). So a downloaded
 * pack keeps the text on the device, but a language with no pack (135 of the 194
 * are online-only, and any offline-capable one before its pack is downloaded) is
 * translated over the internet. An unconditional "nothing is uploaded" is a lie
 * that ships for this brand.
 *
 * This is the privacy line the first-run block draws (`lang_first_run_privacy`,
 * rendered at `LanguagePickerScreen.kt` `FirstRunExplainer`). The rev5 export drew
 * the same claim on sheet 19n as "Nothing you type or say is uploaded"; that sheet
 * has no built trigger yet (its "Learn more" host card is unbuilt / on the
 * rejected 15b), so the shipped surface for the claim is this one line.
 *
 * Mutation decided before the test: put back an absolute no-upload claim — the old
 * "…nothing is sent anywhere." or the export's "…is uploaded." The two tests below
 * are RED under that claim and GREEN once the copy discloses the online path, in
 * all three shipped locales because the lie shipped in all three.
 *
 * The strong "nothing uploaded" claim is not deleted from the product; it becomes
 * a future offline-only flavor's own `app/src/<brand>/res/values/strings.xml`
 * override (ruling ④, "each brand states only what is true for it"). This default
 * — read here from the library, which is exactly what `tranzlate` ships because
 * that brand adds no override — is the SAFE fallback: it under-claims privacy,
 * never over-claims it, so a new brand that forgets to override cannot ship a lie.
 */
@RunWith(RobolectricTestRunner::class)
class FirstRunPrivacyCopyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun Context.localized(locale: Locale): Context =
        createConfigurationContext(Configuration(resources.configuration).apply { setLocale(locale) })

    private fun privacyLine(locale: Locale): String =
        context.localized(locale).getString(R.string.lang_first_run_privacy)

    /**
     * The default (English) copy must not assert that nothing leaves the device.
     * The pattern matches a negation word within a short reach of a "leaves the
     * device" verb — "nothing is sent", "Nothing you type or say is uploaded",
     * "never leaves" — while leaving an honest disclosure ("…is sent over the
     * internet") untouched, because that sentence carries no negation.
     */
    @Test
    fun `default privacy copy makes no absolute no-upload claim`() {
        val en = privacyLine(Locale.ENGLISH)

        assertThat(NO_UPLOAD_CLAIM.containsMatchIn(en)).isFalse()
    }

    /**
     * Every shipped locale must name the online path the waterfall actually takes —
     * the one fact ruling ④ turns on. The old copy named no such path in any
     * locale, so this is the assertion that reddens on a revert. Each localized
     * read is also asserted distinct from English, so a locale that silently fell
     * back to the default (and would then pass the disclosure check for the wrong
     * reason) fails loudly instead.
     */
    @Test
    fun `every locale discloses the online path`() {
        val en = privacyLine(Locale.ENGLISH)
        val fil = privacyLine(Locale("fil"))
        val ptBr = privacyLine(Locale("pt", "BR"))

        assertThat(fil).isNotEqualTo(en)
        assertThat(ptBr).isNotEqualTo(en)
        listOf(en, fil, ptBr).forEach { line ->
            assertThat(DISCLOSES_ONLINE.containsMatchIn(line)).isTrue()
        }
    }

    private companion object {
        /** A negation word within a short reach of a "leaves the device" verb. */
        val NO_UPLOAD_CLAIM =
            Regex("""\b(nothing|never|not)\b[^.]{0,40}\b(sent|uploaded|leaves?)\b""", RegexOption.IGNORE_CASE)

        /** Any word that names the online path the waterfall actually takes. */
        val DISCLOSES_ONLINE = Regex("""\b(internet|cloud|online)\b""", RegexOption.IGNORE_CASE)
    }
}
