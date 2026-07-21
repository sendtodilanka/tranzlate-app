plugins {
    alias(libs.plugins.tranzlate.android.library)
    alias(libs.plugins.tranzlate.android.room)
    alias(libs.plugins.tranzlate.hilt)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
