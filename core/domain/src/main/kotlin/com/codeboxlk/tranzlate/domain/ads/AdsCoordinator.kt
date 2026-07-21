package com.codeboxlk.tranzlate.domain.ads

/**
 * ADS BRAIN ask-surface — the ONLY interstitial show/no-show decision point in the
 * app (D-4; plan §2 one-home rule). Frequency MECHANICS live in `:lib:ads`; the
 * DECISION (paid tier? N-th completed translation? min-gap? daily cap? never on
 * back-press/utility nav?) lives behind this ask.
 *
 * Screens/use-cases only ever ASK by reporting the event; they never decide.
 */
interface AdsCoordinator {
    /** Report a COMPLETED (successful) translation — D-4 counts completions only. */
    suspend fun onTranslationCompleted()
}
