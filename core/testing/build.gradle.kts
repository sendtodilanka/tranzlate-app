plugins {
    alias(libs.plugins.tranzlate.jvm.library)
}

dependencies {
    api(projects.core.common)
    api(projects.core.config)
    api(projects.core.domain)
    api(projects.core.model)
    api(libs.kotlinx.coroutines.test)
    api(libs.junit)

    testImplementation(libs.turbine)
}
