package com.codeboxlk.subscription

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * How this library gets the [Activity] a store dialog must be launched from.
 *
 * WHY it exists: every Play-billing wrapper (Qonversion included) requires a real
 * `android.app.Activity` to start the purchase flow, but [SubscriptionGateway] is
 * called from a ViewModel and its domain-side ask-surface (`PurchaseFlow`) lives
 * in a JVM-pure contract module that may not name Android types at all. Passing
 * an Activity down through those layers would drag `android.app` into the
 * contracts; asking for one at the moment of purchase does not.
 *
 * Returning `null` is a legitimate answer (app in the background, no Activity
 * yet) — callers must fail honestly, never queue a purchase against a dead window.
 */
fun interface ActivityProvider {
    fun current(): Activity?
}

/**
 * [ActivityProvider] backed by the process's own lifecycle callbacks.
 *
 * Holds the CURRENT RESUMED activity only, through a [WeakReference], and drops
 * it on pause — an Activity kept in a `@Singleton` is the classic billing-wrapper
 * leak, and a paused Activity is not a legal launch target anyway.
 *
 * Host-agnostic on purpose (Ring 1 rule): it takes an [Application] and knows
 * nothing else about the app installing it.
 */
class ForegroundActivityProvider :
    ActivityProvider,
    Application.ActivityLifecycleCallbacks {
    private var resumed: WeakReference<Activity>? = null

    override fun current(): Activity? = resumed?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    /**
     * Starts listening. **Call from `Application.onCreate`, never from a lazy
     * DI provider.**
     *
     * Android does not replay lifecycle callbacks, so a listener registered
     * after the first `onResume` never learns about it and [current] answers
     * null until something else resumes an Activity. Registering from a lazy
     * `@Provides` is the trap: nothing necessarily builds that node before
     * the first composition, and first composition happens after the first
     * resume — so the first purchase of a session fails and everything after
     * a rotation works, which reads as flakiness rather than as a bug.
     *
     * Idempotent: re-registering the same callbacks object is harmless, and
     * `Application` keeps one entry per instance.
     */
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }
}
