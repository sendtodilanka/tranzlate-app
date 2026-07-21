plugins {
    alias(libs.plugins.tranzlate.android.library)
}

// ADS BRAIN (plan §2 Ring 3). NO Hilt module here — seam bindings live in
// :app/src/prod TranslateModule (plan §6.1). `api(:lib:ads)`/`api(:lib:consent)`
// so the prod wiring in :app/src/prod can map AppConfig → AdsConfig/consent.
dependencies {
    api(projects.core.common)
    api(projects.core.config)
    api(projects.core.domain)
    api(projects.lib.ads)
    api(projects.lib.consent)
    implementation(libs.kotlinx.coroutines.core)
}
