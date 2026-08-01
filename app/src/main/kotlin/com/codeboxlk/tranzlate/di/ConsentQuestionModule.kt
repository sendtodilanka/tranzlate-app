package com.codeboxlk.tranzlate.di

import androidx.lifecycle.SavedStateHandle
import com.codeboxlk.tranzlate.domain.translate.ConsentQuestionStore
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
 * Where an unanswered consent question lives — a composition-root decision, so
 * it is made in the composition root.
 *
 * Installed in [ViewModelComponent] ALONE, on purpose. Every consumer of
 * `DownloadGate` today is a `@HiltViewModel` (the picker and the offline
 * manager), which is what makes a [SavedStateHandle] available at all; a future
 * `@Singleton` that injects the gate will fail to compile here rather than
 * silently getting a question that no screen can see. That failure is the useful
 * one — it asks the author where the question is supposed to be kept.
 *
 * Not `@ViewModelScoped`: a fresh store per gate keeps the ruling's "one
 * instance per state holder" property (`DownloadGate`'s KDoc), and both gates in
 * a single ViewModel would share the handle's key anyway.
 */
@Module
@InstallIn(ViewModelComponent::class)
internal object ConsentQuestionModule {
    @Provides
    fun consentQuestionStore(handle: SavedStateHandle): ConsentQuestionStore = SavedStateConsentQuestionStore(handle)
}
