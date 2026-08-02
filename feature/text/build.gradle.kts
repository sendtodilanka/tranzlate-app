plugins {
    alias(libs.plugins.tranzlate.android.feature)
    // #186 — Robolectric + createComposeRule in `src/test`. The three C-4 announcements
    // this module renders (`a11y_translating`, `a11y_result_ready`, `a11y_error`) had no
    // test anywhere that could hear them.
    alias(libs.plugins.tranzlate.compose.test)
}

dependencies {
    implementation(projects.core.config)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
