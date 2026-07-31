plugins {
    alias(libs.plugins.tranzlate.android.library)
}

// Reusable AAR (plan §2 Ring 1): own namespace, ZERO project dependencies —
// droppable into other apps. The Qonversion SDK lives HERE and nowhere else:
// the public API (SubscriptionGateway / Entitlement) never names it, so swapping
// providers is a change to this module only.
android {
    namespace = "com.codeboxlk.subscription"
    // Gateway unit tests construct Application() and cross android.util.Log; the
    // default unit-test android.jar throws "Stub!" from both. Return-default
    // stubs make them inert — no Robolectric needed for logic that never touches
    // a real Context (the SDK itself stays behind QonversionApi and out of tests).
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // `implementation`, never `api` — Qonversion re-exports Play Billing with an
    // `api` scope of its own, and letting that reach feature modules would put
    // billing types in reach of screens that must only ASK.
    implementation(libs.qonversion)

    // This module had NO test source set until a lens proved what that cost:
    // deleting the blank-price filter, and deleting the CTA gate, each left the
    // whole suite green. Both were the fixes an earlier review round had asked
    // for. Tests that live two layers above the code they are named for defend
    // nothing.
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
