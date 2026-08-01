package com.codeboxlk.tranzlate.di

import androidx.lifecycle.SavedStateHandle
import com.codeboxlk.tranzlate.core.common.ConnectivityMonitor
import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import com.codeboxlk.tranzlate.domain.translate.ConsentQuestionStore
import com.codeboxlk.tranzlate.domain.translate.DownloadGate
import com.codeboxlk.tranzlate.domain.translate.OfflineModelManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** The saved-state key. Namespaced, because the handle is shared with the ViewModel. */
internal const val KEY_PENDING_CONSENT = "download_gate.pending_consent"

/**
 * Keeps the mobile-data question alive across a process death.
 *
 * The harm, plainly: the user taps ⬇ on a language while on mobile data, the
 * dialog asks whether to spend the data plan, they switch apps to think about
 * it, Android reclaims the process, and on return the question has silently
 * withdrawn itself. Nothing was downloaded and nothing was charged — but the app
 * asked something and then pretended it had not.
 *
 * A [SavedStateHandle] survives exactly that, and only that: it is not storage,
 * it is the Activity's saved instance state, so a genuine "back out and forget
 * it" (a finished task, a swipe from Recents) still clears the question, which
 * is the behaviour we want. A DataStore key would resurrect a week-old dialog.
 *
 * The [MutableStateFlow] in front of the handle is not a cache — it is what
 * makes [take] one atomic step, so a double tap on "Download once" cannot mint
 * two consent tokens. The handle is written on the same line; it is the durable
 * copy, never the source the gate reads from within a process.
 *
 * `internal`, and constructed only by [DownloadGateModule] below: an instance of
 * this class IS the user's open question, and #192's co-verify lens showed what
 * happens as soon as anything else can get hold of one.
 */
internal class SavedStateConsentQuestionStore(
    private val handle: SavedStateHandle,
) : ConsentQuestionStore {
    private val state = MutableStateFlow(handle.get<String>(KEY_PENDING_CONSENT))

    override val question: StateFlow<String?> = state.asStateFlow()

    override fun raise(id: String) {
        state.value = id
        handle[KEY_PENDING_CONSENT] = id
    }

    override fun take(): String? =
        state.getAndUpdate { null }.also {
            handle[KEY_PENDING_CONSENT] = null
        }
}

/**
 * Where the download gate is BUILT — and, just as deliberately, the only place
 * an unanswered consent question is ever handed to anything.
 *
 * The gate is assembled here rather than by an `@Inject` constructor because of
 * what an `@Inject` constructor would require: a Hilt binding for every
 * parameter, including [ConsentQuestionStore]. A bound store is a store any
 * `@HiltViewModel` can ask Dagger for — and a co-verify lens on #192 compiled
 * the ViewModel that does, draining the question the user is being asked and
 * handing the id straight to `OfflineModelManager.download()`. Gate skipped,
 * consent token skipped, mobile data spent. Read [ConsentQuestionStore]'s own
 * KDoc for the full account.
 *
 * With the store constructed HERE instead, it is never in the graph, so that
 * ViewModel does not compile: *"ConsentQuestionStore cannot be provided without
 * an @Provides-annotated method"*. `KonsistArchitectureTest` holds the absence,
 * because Dagger's error message reads as an invitation to add the binding back.
 *
 * Installed in [ViewModelComponent] ALONE, on purpose. Every consumer of
 * [DownloadGate] today is a `@HiltViewModel` (the picker and the offline
 * manager), which is what makes a [SavedStateHandle] available at all; a future
 * `@Singleton` that injects the gate will fail to compile here rather than
 * silently getting a question that no screen can see. That failure is the useful
 * one — it asks the author where the question is supposed to be kept.
 *
 * Not `@ViewModelScoped`, and no other scope: a fresh gate — and with it a fresh
 * store — per injection point is the ruling's "one instance per state holder"
 * property ([DownloadGate]'s KDoc). A scope here would carry one screen's
 * half-answered dialog into another, which is why `KonsistArchitectureTest`
 * checks this `@Provides` for a scope annotation as well: moving the gate's
 * construction out of `DownloadGate.kt` moved it out of sight of
 * `DownloadGateTest`'s file-scoped rule, and a guard that quietly stops covering
 * the thing it names is worse than no guard.
 */
@Module
@InstallIn(ViewModelComponent::class)
internal object DownloadGateModule {
    @Provides
    fun downloadGate(
        connectivity: ConnectivityMonitor,
        downloadPrefs: DownloadPrefsRepository,
        modelManager: OfflineModelManager,
        handle: SavedStateHandle,
    ): DownloadGate =
        DownloadGate(
            connectivity = connectivity,
            downloadPrefs = downloadPrefs,
            modelManager = modelManager,
            consentQuestion = SavedStateConsentQuestionStore(handle),
        )
}
