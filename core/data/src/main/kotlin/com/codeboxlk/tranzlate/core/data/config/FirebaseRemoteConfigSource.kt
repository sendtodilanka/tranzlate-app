package com.codeboxlk.tranzlate.core.data.config

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.codeboxlk.tranzlate.core.common.DispatcherProvider
import com.codeboxlk.tranzlate.core.config.RemoteConfigDefaults
import com.codeboxlk.tranzlate.core.config.RemoteConfigSnapshot
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.config.RemoteValueReader
import com.codeboxlk.tranzlate.core.config.readRemoteConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "RemoteConfig"

/**
 * Firebase-Remote-Config-backed [RemoteConfigSource] — the PROD binding
 * (`:app/src/prod` TranslateModule). The fake variant keeps
 * [StaticRemoteConfigSource].
 *
 * Shape, and why:
 *  - **Getters never touch the SDK.** They read a `@Volatile`
 *    [RemoteConfigSnapshot] that starts at [RemoteConfigSnapshot.DEFAULTS], so the
 *    very first composition and the translation waterfall are answered with a
 *    field read. Reading Firebase directly would risk its blocking cold-cache disk
 *    read (5 s) on whatever thread asked.
 *  - **One bootstrap, on IO.** settings → defaults → `fetchAndActivate`, each
 *    awaited in order, then one snapshot is published. Awaiting `setDefaultsAsync`
 *    before reading is what makes "absent console key ⇒ OUR default" true rather
 *    than the SDK's static `""`/`0`/`false`.
 *  - **Settles, always.** [awaitFirstFetch] returns on success, on failure, on a
 *    missing/misconfigured FirebaseApp, and on timeout. A paywall tap must never
 *    hang on a network.
 *
 * DEGRADED MODE (deliberate, not a bug): if the `com.google.gms.google-services`
 * plugin is not applied to `:app`, no `google_app_id` resource exists,
 * `FirebaseApp` is never initialised and `FirebaseRemoteConfig.getInstance()`
 * throws. That is caught here and the app runs on defaults — no crash, no fake
 * values. See docs/plan/launch-monetization.md §"Track A must add".
 */
@Singleton
class FirebaseRemoteConfigSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        dispatchers: DispatcherProvider,
    ) : RemoteConfigSource {
        @Volatile
        private var snapshot: RemoteConfigSnapshot = RemoteConfigSnapshot.DEFAULTS

        /** Completed exactly once, whatever the outcome — never completed exceptionally. */
        private val settled = CompletableDeferred<Unit>()

        private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

        init {
            scope.launch { bootstrap() }
        }

        override fun limitFreeAi(): Int = snapshot.limitFreeAi

        override fun limitProFairUse(): Int = snapshot.limitProFairUse

        override fun adNth(): Int = snapshot.adNth

        override fun adMinGapSeconds(): Int = snapshot.adMinGapSeconds

        override fun adDailyCap(): Int = snapshot.adDailyCap

        override fun textLimitFree(): Int = snapshot.textLimitFree

        override fun textLimitPro(): Int = snapshot.textLimitPro

        override fun gotEnabled(): Boolean = snapshot.gotEnabled

        override fun gotTimeoutMs(): Long = snapshot.gotTimeoutMs

        override fun gctTimeoutMs(): Long = snapshot.gctTimeoutMs

        override fun qonversionKey(): String = snapshot.qonversionKey

        override fun gctApiKey(): String = snapshot.gctApiKey

        override fun privacyPolicyUrl(): String = snapshot.privacyPolicyUrl

        override fun termsUrl(): String = snapshot.termsUrl

        override fun contactEmail(): String = snapshot.contactEmail

        /**
         * Bounded a SECOND time here on purpose: [bootstrap] already caps itself,
         * but this is the call a UI tap sits on, so it may not depend on another
         * method's discipline to return.
         */
        override suspend fun awaitFirstFetch() {
            withTimeoutOrNull(RemoteConfigDefaults.FIRST_FETCH_TIMEOUT_MS) { settled.await() }
        }

        private suspend fun bootstrap() {
            try {
                val remoteConfig = FirebaseRemoteConfig.getInstance()
                withTimeoutOrNull(RemoteConfigDefaults.FIRST_FETCH_TIMEOUT_MS) {
                    remoteConfig.setConfigSettingsAsync(fetchSettings()).awaitCompletion()
                    remoteConfig.setDefaultsAsync(RemoteConfigSnapshot.asRemoteDefaults).awaitCompletion()
                    remoteConfig.fetchAndActivate().awaitCompletion()
                }
                // Publish whatever the SDK can resolve NOW. Even when the fetch
                // failed or timed out this is still an upgrade on DEFAULTS: an
                // earlier session's activated values live in the SDK's cache.
                snapshot = readRemoteConfig(remoteConfig.asReader())
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                // Includes IllegalStateException("Default FirebaseApp is not
                // initialized") when google-services has not been applied yet.
                Log.w(TAG, "Remote config unavailable — running on defaults", failure)
            } finally {
                settled.complete(Unit)
            }
        }

        /**
         * Debug builds bypass the throttle so a console change is testable
         * immediately; production uses Google's recommended 12 h, which also keeps
         * the app inside the free fetch quota.
         */
        private fun fetchSettings(): FirebaseRemoteConfigSettings {
            val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            return FirebaseRemoteConfigSettings
                .Builder()
                .setMinimumFetchIntervalInSeconds(
                    if (debuggable) {
                        RemoteConfigDefaults.FETCH_INTERVAL_SECONDS_DEBUG
                    } else {
                        RemoteConfigDefaults.FETCH_INTERVAL_SECONDS_PROD
                    },
                ).build()
        }
    }

/** Adapts the SDK to the pure [RemoteValueReader] the mapping is written against. */
private fun FirebaseRemoteConfig.asReader(): RemoteValueReader =
    object : RemoteValueReader {
        override fun string(key: String): String = getString(key)

        override fun long(key: String): Long = getLong(key)

        override fun boolean(key: String): Boolean = getBoolean(key)
    }

/**
 * Awaits a Play-services [Task] for COMPLETION, never for its value, and never
 * throws: a failed fetch is an expected outcome here (offline first launch), and
 * the caller's next step — reading whatever the SDK can resolve — is identical
 * either way.
 */
private suspend fun Task<*>.awaitCompletion() =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { continuation.resume(Unit) }
    }
