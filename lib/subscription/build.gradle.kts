plugins {
    alias(libs.plugins.tranzlate.android.library)
}

// Reusable AAR (plan §2 Ring 1): own namespace, ZERO project dependencies —
// droppable into other apps. The Qonversion SDK lives HERE and nowhere else:
// the public API (SubscriptionGateway / Entitlement) never names it, so swapping
// providers is a change to this module only.
android {
    namespace = "com.codeboxlk.subscription"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // `implementation`, never `api` — Qonversion re-exports Play Billing with an
    // `api` scope of its own, and letting that reach feature modules would put
    // billing types in reach of screens that must only ASK.
    implementation(libs.qonversion)
}
