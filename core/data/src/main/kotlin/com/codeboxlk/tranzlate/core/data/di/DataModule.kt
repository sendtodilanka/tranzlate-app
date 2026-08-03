package com.codeboxlk.tranzlate.core.data.di

import android.util.Log
import com.codeboxlk.tranzlate.core.common.ApplicationScope
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.data.repository.DownloadPrefsRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.LanguageRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.LanguageUsageRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.ThemePrefsRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.TranslatePrefsRepositoryImpl
import com.codeboxlk.tranzlate.core.data.repository.TranslationRepositoryImpl
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageRepository
import com.codeboxlk.tranzlate.domain.repository.LanguageUsageRepository
import com.codeboxlk.tranzlate.domain.repository.ThemePrefsRepository
import com.codeboxlk.tranzlate.domain.repository.TranslatePrefsRepository
import com.codeboxlk.tranzlate.domain.repository.TranslationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Engine-agnostic data bindings — present in BOTH prod and fake variants.
 * The four brain seams are deliberately NOT bound here (plan §6.1 — they live in
 * `:app/src/prod` TranslateModule / `:core:translate-fake` FakeTranslateModule).
 *
 * `RemoteConfigSource` left this module for the same reason: it now has two
 * genuinely different implementations per engine flavor (Firebase in prod, the
 * static defaults in fake), so binding it here would have forced the network-
 * backed one onto Maestro runs.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun translationRepository(impl: TranslationRepositoryImpl): TranslationRepository

    @Binds
    abstract fun languageRepository(impl: LanguageRepositoryImpl): LanguageRepository

    @Binds
    abstract fun languageUsageRepository(impl: LanguageUsageRepositoryImpl): LanguageUsageRepository

    @Binds
    abstract fun translatePrefsRepository(impl: TranslatePrefsRepositoryImpl): TranslatePrefsRepository

    @Binds
    abstract fun themePrefsRepository(impl: ThemePrefsRepositoryImpl): ThemePrefsRepository

    @Binds
    abstract fun downloadPrefsRepository(impl: DownloadPrefsRepositoryImpl): DownloadPrefsRepository

    companion object {
        /**
         * Application-lifetime scope for fire-and-forget data writes (first
         * user: the translate flow's usage stamper, issue #122). Provided HERE
         * — not in the per-flavor TranslateModules — because it is
         * engine-agnostic plumbing both flavors need, and a single provider
         * cannot drift. SupervisorJob so one failed write never cancels the
         * scope for every later one; IO because everything launched on it is
         * disk work.
         *
         * ### The handler (issue #238), and why `SupervisorJob` alone was not it
         *
         * `SupervisorJob` stops one child's failure cancelling its siblings. It
         * does **not** swallow: the failure is still delivered to the context's
         * `CoroutineExceptionHandler`, and with none installed that is
         * `Thread.defaultUncaughtExceptionHandler` — process death, no dialog,
         * nothing the user can act on. Three files carried KDoc saying "appScope
         * has no `CoroutineExceptionHandler`, so guard your write", and #236 is
         * what happens when a write gets added by someone who did not read them.
         * A rule written down is not a rule enforced (mandatory rule 8's lesson,
         * paid for twice); this is the enforcement.
         *
         * It does **not** replace the local guards. A handler catches the throw
         * after the launch is already dead, so everything left in that coroutine
         * is skipped — at `LanguagePickerViewModel.select` a failing language
         * write would take the Recents stamp below it down too. The local catch
         * keeps each degrade precise; this keeps the process alive when there is
         * no local catch at all, including in code not yet written.
         *
         * It logs rather than reporting: every user of this scope is by
         * definition work the user is not waiting on, so there is no screen owed
         * a message. `Log.e` keeps it visible in logcat and in crash reporting's
         * breadcrumbs instead of vanishing.
         */
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(dispatchers: DispatcherProvider): CoroutineScope =
            CoroutineScope(
                SupervisorJob() +
                    dispatchers.io +
                    CoroutineExceptionHandler { _, thrown ->
                        Log.e(APP_SCOPE_TAG, "Fire-and-forget work failed on the application scope", thrown)
                    },
            )

        private const val APP_SCOPE_TAG = "TranzlateAppScope"
    }
}
