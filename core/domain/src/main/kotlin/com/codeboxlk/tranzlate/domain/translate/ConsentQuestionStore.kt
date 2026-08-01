package com.codeboxlk.tranzlate.domain.translate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Where [DownloadGate] keeps the ONE unanswered mobile-data question — and
 * nothing else.
 *
 * This exists so the question can outlive the process WITHOUT the gate learning
 * anything about Android. The gate is a `:core:domain` class, and `:core:domain`
 * is JVM-pure by architecture gate (`KonsistArchitectureTest.ring-2 contract
 * modules are JVM-pure`), so it cannot hold a `SavedStateHandle`. It holds this
 * instead, and the composition root decides how durable the storage is.
 *
 * **Storage, never policy.** Nothing here decides whether a download may start.
 * Raising the question is [DownloadGate.requestDownload]'s decision after the
 * metered check; answering it is [DownloadGate.consentOnce]'s, and that is still
 * the only thing in the codebase that can mint a [ConsentedDownload]. An
 * implementation that pre-loads an id therefore re-opens a QUESTION — a dialog
 * the user must still answer — and cannot start a transfer. That is the whole
 * reason the seam is shaped as storage rather than as "the gate's state,
 * settable": the strong invariant (`ConsentedDownload`'s constructor is
 * `internal` and `consentOnce()` is its only producer) survives untouched, and
 * `DownloadGateTest` now holds both halves of it against this file's source
 * instead of only saying so in prose.
 *
 * ## Never bound in the Hilt graph — the reason this type is safe to be public
 *
 * Before PR-13 the pending id was a `private val` inside [DownloadGate] and
 * nothing in the app could read it. Moving it out here made it reachable, and a
 * co-verify lens compiled the consequence: a `@HiltViewModel` that injects this
 * interface alongside [OfflineModelManager] can [take] the id the user is
 * currently being asked about and pass it straight to
 * [OfflineModelManager.download] — no gate, no [ConsentedDownload], no metered
 * check. Issue #90's ruling, walked around one layer above where #166's lens
 * caught it. The app assembled clean with that ViewModel in it.
 *
 * `internal` on the Hilt module does not close it: that restricts who can NAME
 * the module in Kotlin, not who Dagger will hand the bound type to — Dagger
 * resolves by type within a component, and both language ViewModels already hold
 * an [OfflineModelManager] directly.
 *
 * So there is **no binding for this type in any component**, and the gate has no
 * `@Inject` constructor to force one. The composition root builds the durable
 * store and the gate around it in one `@Provides`, and a class that asks Dagger
 * for a [ConsentQuestionStore] fails to compile — *"cannot be provided without
 * an @Provides-annotated method"*. A type nobody can obtain needs no rule about
 * what may be done with it.
 *
 * Constructing one directly is still allowed and still harmless: a store you
 * built yourself is YOUR empty store, not the one the user's open question lives
 * in. That is what keeps the tests below able to drive this interface without a
 * Hilt graph. `KonsistArchitectureTest` holds the binding's absence, so the fix
 * cannot be undone by adding back the one line that Dagger's own error message
 * invites.
 *
 * @see DownloadGate
 */
interface ConsentQuestionStore {
    /** The language id awaiting an answer; null = no question is open. */
    val question: StateFlow<String?>

    /** Open the question for [id]. Replaces any older one — there is only ever one. */
    fun raise(id: String)

    /**
     * Close the question and hand back what it was about, or null when nothing
     * was open. One atomic step, because a double tap must not answer twice.
     */
    fun take(): String?
}

/**
 * The question dies with the process.
 *
 * The shipped behaviour before #130 PR-13, kept as the default for tests and for
 * any caller that has no saved state to write into. Everything user-facing binds
 * the durable implementation instead.
 */
class InMemoryConsentQuestionStore : ConsentQuestionStore {
    private val state = MutableStateFlow<String?>(null)

    override val question: StateFlow<String?> = state.asStateFlow()

    override fun raise(id: String) {
        state.value = id
    }

    override fun take(): String? = state.getAndUpdate { null }
}
