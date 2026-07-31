plugins {
    alias(libs.plugins.tranzlate.android.feature)
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)
    // Terms/Privacy URLs are remote-served (Play policy requires them on the
    // purchase screen). Reading the config seam is an ASK, like every other
    // brain surface — the screen still owns no fetching of its own.
    implementation(projects.core.config)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
