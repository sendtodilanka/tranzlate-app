plugins {
    alias(libs.plugins.tranzlate.jvm.library)
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
}
