plugins {
    alias(libs.plugins.tranzlate.android.library)
    alias(libs.plugins.tranzlate.hilt)
}

android {
    // The application scope's CoroutineExceptionHandler logs through
    // android.util.Log, and the default unit-test android.jar throws "Stub!" from
    // it — a handler that throws while handling is worse than no test. Same
    // precedent and same reason as lib/subscription: return-default stubs make
    // the log call inert for logic that never touches a real Context.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(projects.core.common)
    api(projects.core.config)
    api(projects.core.domain)
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.core)

    // Firebase Remote Config backs the PROD RemoteConfigSource binding
    // (FirebaseRemoteConfigSource). It sits in this shared module rather than a
    // prod-only one because :app already depends on :core:data for both engine
    // flavors — see docs/plan/launch-monetization.md §5 for the trade-off. The
    // fake flavor still binds StaticRemoteConfigSource, so no fake run ever
    // reads a console value.
    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.config)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // The shipped seam fakes (FakeOfflineVoiceCatalog): a repository test that
    // rolled its own would stop proving the double features will actually use.
    testImplementation(projects.core.testing)
}
