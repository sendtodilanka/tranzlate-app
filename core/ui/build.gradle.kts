plugins {
    alias(libs.plugins.tranzlate.android.library)
    alias(libs.plugins.tranzlate.android.library.compose)
}

dependencies {
    api(projects.core.designsystem)
    api(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
