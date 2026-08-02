plugins {
    alias(libs.plugins.tranzlate.android.feature)
    // #186 — Robolectric + createComposeRule in `src/test`. The row-height rule this
    // module owns is decided inside a composable, and until now nothing could read it.
    alias(libs.plugins.tranzlate.compose.test)
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
