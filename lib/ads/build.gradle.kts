plugins {
    alias(libs.plugins.tranzlate.android.library)
}

// Reusable AAR (plan §2 Ring 1): own namespace, ZERO project dependencies.
// AdMob SDK is added INTERNALLY in a later phase without changing the public API.
android {
    namespace = "com.codeboxlk.ads"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
