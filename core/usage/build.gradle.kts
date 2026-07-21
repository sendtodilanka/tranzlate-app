plugins {
    alias(libs.plugins.tranzlate.android.library)
}

// USAGE BRAIN (plan §2 Ring 3). NO Hilt module here — seam bindings live in
// :app/src/prod TranslateModule (plan §6.1).
dependencies {
    api(projects.core.common)
    api(projects.core.config)
    api(projects.core.domain)
    api(projects.core.model)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.core)
}
