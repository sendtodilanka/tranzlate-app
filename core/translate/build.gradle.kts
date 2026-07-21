plugins {
    alias(libs.plugins.tranzlate.android.library)
}

// TRANSLATION BRAIN (plan §2 Ring 3). NOTE: deliberately NO Hilt module in this
// module — the prod seam bindings live in :app/src/prod TranslateModule (plan §6.1).
dependencies {
    api(projects.core.common)
    api(projects.core.domain)
    api(projects.core.model)
    implementation(libs.kotlinx.coroutines.core)
}
