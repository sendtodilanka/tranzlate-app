plugins {
    alias(libs.plugins.tranzlate.android.feature)
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)
    // Window size class + posture (hinge) for the adaptive layouts (issue #56).
    implementation(libs.androidx.compose.material3.adaptive)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
