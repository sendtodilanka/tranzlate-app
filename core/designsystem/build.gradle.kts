plugins {
    alias(libs.plugins.tranzlate.android.library)
    alias(libs.plugins.tranzlate.android.library.compose)
}

dependencies {
    // `api`, not `implementation`: TranzlateTheme's ThemeSettings overload is public.
    api(projects.core.model)
}
