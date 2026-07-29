plugins {
    alias(libs.plugins.tranzlate.android.library)
}

// ACCESS BRAIN (plan §2 Ring 3). NO Hilt module here — seam bindings live in
// :app/src/prod TranslateModule (plan §6.1). `api(:lib:subscription)` so the
// prod wiring in :app/src/prod can map AppConfig → SubscriptionConfig.
dependencies {
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.model)
    api(projects.lib.subscription)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
