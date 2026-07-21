plugins {
    alias(libs.plugins.tranzlate.jvm.library)
    alias(libs.plugins.tranzlate.hilt)
}

// Fake-variant binding module (plan §2 Ring 3 last item + §6.4): a PRODUCTION
// @InstallIn module covering the COMPLETE binding surface of the four excluded
// prod brains. Ships ONLY inside engine=fake variants (fakeImplementation in
// :app); the CI guard asserts it never reaches a prod/release classpath.
dependencies {
    api(projects.core.testing)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.turbine)
}
