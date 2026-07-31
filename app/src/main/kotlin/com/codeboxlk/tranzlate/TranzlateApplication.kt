package com.codeboxlk.tranzlate

import android.app.Application
import com.codeboxlk.tranzlate.di.AppStartupTask
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TranzlateApplication : Application() {
    /**
     * Injected in order to be RUN in [onCreate]. The graph builds each task
     * lazily, and lazily is too late — that is the entire point of this field.
     * Empty on the fake variant, which ships no billing.
     */
    @Inject
    lateinit var startupTasks: Set<@JvmSuppressWildcards AppStartupTask>

    /**
     * Starts tracking the Activity a store dialog launches from, before any
     * Activity exists.
     *
     * The tracker used to register itself from inside the lazy `@Provides` for
     * `SubscriptionGateway`, and that made the **first purchase of every session
     * fail**. Nothing pulls the gateway before composition — MainActivity
     * injects only `AppConfig` — and first composition happens at
     * `wm.addView` inside `handleResumeActivity`, which is strictly *after*
     * `dispatchActivityResumed`. So the callbacks registered just after the only
     * resume that had happened, Android does not replay it, `current()` answered
     * null, and the purchase died with `NoForegroundActivity`. It came right
     * after a rotation or a background-and-return, which is exactly why it
     * survived to a third review round.
     *
     * `Application.onCreate` runs before any Activity is created, so registering
     * here cannot miss a resume.
     */
    override fun onCreate() {
        super.onCreate()
        startupTasks.forEach { it.onAppCreate(this) }
    }
}
