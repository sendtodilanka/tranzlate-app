plugins {
    alias(libs.plugins.tranzlate.jvm.library)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
