package com.codeboxlk.tranzlate.domain.speech

/**
 * Which languages this device can READ ALOUD with no connection (issue #130
 * rev.3 U-3 — the 16a speaker mark asks this and nothing else. The 19j "no
 * offline voice" sheet asked it too, until rev 5 cut 19j: the mark is drawn
 * only where a voice exists, so there is no absence left to explain — #180).
 *
 * Three things this contract deliberately fixes about the question:
 *
 * 1. **Ids are catalog ids, not locales.** The platform answers in locales
 *    (`es-ES`, `pt-BR`, `zh-HK`); the language surface is keyed by
 *    `LanguageTagResolver.canonicalIds`. Resolving at the seam means no screen
 *    ever holds a locale it has to guess how to match, and the whole app has
 *    exactly one place where a tag becomes an id.
 * 2. **It is a one-shot ASK, not an observable.** The set of installed voices
 *    only changes when the user installs or removes voice data, which cannot
 *    happen without leaving the app — so a `Flow` would promise live updates
 *    nobody can deliver, and a standing subscription would mean a standing TTS
 *    engine connection (the documented leak class this seam exists to avoid,
 *    ruling risk R4).
 * 3. **It cannot fail.** Every failure — no engine installed, an engine that
 *    never initialises, package visibility filtering the engine out of view —
 *    resolves to the empty set. A missing speaker mark is a false negative the
 *    user can live with; an exception crossing into a screen that is only
 *    trying to decorate a list row is not.
 *
 * The false-negative-safe direction is a design constraint, not an accident:
 * the mark must under-promise. Claiming a voice that is not there sends the
 * user to a Speak button that does nothing.
 */
interface OfflineVoiceCatalog {
    /**
     * Catalog ids this device can speak offline; empty when it can speak none,
     * or when the device could not be asked. Suspends on the first call while
     * the platform engine is enumerated, and answers from cache after that.
     */
    suspend fun offlineVoiceLanguageIds(): Set<String>
}
