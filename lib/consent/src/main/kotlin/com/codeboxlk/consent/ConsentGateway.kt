package com.codeboxlk.consent

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User consent status as this library reports it (UMP wrapper surface).
 * STRUCTURAL RULE (fixes the old app's auto-granted-consent bug): consent starts
 * [UNKNOWN] and only ever becomes [OBTAINED] through an actual user decision —
 * never by default, never programmatically.
 */
enum class ConsentStatus {
    UNKNOWN,
    REQUIRED,
    NOT_REQUIRED,
    OBTAINED,
}

/**
 * Public consent API. The UMP SDK stays `internal` behind this surface.
 */
interface ConsentGateway {
    /** Hot consent state; starts at [ConsentStatus.UNKNOWN]. */
    val consentStatus: Flow<ConsentStatus>

    /**
     * Request/refresh consent info and, when required, present the consent form.
     * Returns the resulting status — callers must handle every value (no-dead-end).
     */
    suspend fun requestConsent(activity: Activity): ConsentStatus

    /** True when a privacy-options entry point must be surfaced (e.g. EEA re-prompt). */
    val privacyOptionsRequired: Boolean

    /** Present the privacy-options form (only meaningful when [privacyOptionsRequired]). */
    suspend fun showPrivacyOptions(activity: Activity)
}

/**
 * SDK-free stand-in until the UMP integration phase: stays [ConsentStatus.UNKNOWN]
 * and never grants — the structurally safe default (ads layer treats non-OBTAINED
 * as no-consent).
 */
class NoOpConsentGateway : ConsentGateway {
    private val state = MutableStateFlow(ConsentStatus.UNKNOWN)

    override val consentStatus: Flow<ConsentStatus> = state.asStateFlow()

    override suspend fun requestConsent(activity: Activity): ConsentStatus = ConsentStatus.UNKNOWN

    override val privacyOptionsRequired: Boolean = false

    override suspend fun showPrivacyOptions(activity: Activity) = Unit
}
