package com.codeboxlk.tranzlate.di

import android.app.Application
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Work that must happen in `Application.onCreate` rather than whenever the DI
 * graph first happens to need it.
 *
 * This seam exists because "register it from the provider that uses it" is a
 * real and expensive trap: a lazy `@Provides` runs at first *use*, and some
 * things — anything listening for lifecycle callbacks Android will not replay —
 * are already too late by then. The first purchase of every session failed for
 * exactly that reason (see `TranzlateApplication.onCreate`).
 *
 * It is a SET because the answer is flavour-dependent: the prod variant
 * contributes the store-launch Activity tracker, and the fake variant, which
 * ships no billing at all, contributes nothing. `@Multibinds` is what makes the
 * empty case legal.
 */
fun interface AppStartupTask {
    fun onAppCreate(application: Application)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppStartupModule {
    /** Declares the set so a variant contributing nothing still builds. */
    @Multibinds
    abstract fun startupTasks(): Set<AppStartupTask>
}
