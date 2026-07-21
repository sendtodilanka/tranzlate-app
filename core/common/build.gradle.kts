plugins {
    alias(libs.plugins.tranzlate.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)
}
