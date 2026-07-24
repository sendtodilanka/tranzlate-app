plugins {
    alias(libs.plugins.tranzlate.android.feature)
}

dependencies {
    // AutoMirrored ArrowBack for the top bar. The app already bundles this set,
    // so declaring it here adds nothing to the shipped APK.
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
