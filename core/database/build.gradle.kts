plugins {
    alias(libs.plugins.tranzlate.android.library)
    alias(libs.plugins.tranzlate.android.room)
    alias(libs.plugins.tranzlate.hilt)
    // Robolectric, so a DAO query can be RUN against a real SQLite in the ordinary
    // unit-test task instead of being reasoned about (#130 PR-19).
    //
    // The plugin is named for Compose because that is what #186 needed it for, and
    // this module has no Compose in it. It is applied anyway because it is the
    // project's ONE Robolectric wiring — including the pinned offline SDK image
    // that #163 exists for — and copying that wiring into a second build file is
    // exactly what its own KDoc says it was written to stop. Splitting a
    // `tranzlate.robolectric` plugin out of it is a build-logic change and belongs
    // in its own PR, not smuggled into a sheet PR.
    alias(libs.plugins.tranzlate.compose.test)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
