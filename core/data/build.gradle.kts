plugins {
    alias(libs.plugins.tranzlate.android.library)
    alias(libs.plugins.tranzlate.hilt)
}

dependencies {
    api(projects.core.common)
    api(projects.core.config)
    api(projects.core.domain)
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
