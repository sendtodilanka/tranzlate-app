plugins {
    alias(libs.plugins.tranzlate.android.library)
    alias(libs.plugins.tranzlate.android.library.compose)
}

dependencies {
    // JVM contract tests for the sheet anatomy (issue #130 PR-8): pure
    // tone→role and type-metric resolvers, no Android runtime involved.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
