plugins {
    alias(libs.plugins.tranzlate.android.library)
}

// TRANSLATION BRAIN (plan §2 Ring 3). NOTE: deliberately NO Hilt module in this
// module — the prod seam bindings live in :app/src/prod TranslateModule (plan §6.1).
dependencies {
    api(projects.core.common)
    api(projects.core.config)
    api(projects.core.domain)
    api(projects.core.model)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.okhttp)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
