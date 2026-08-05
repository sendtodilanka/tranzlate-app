plugins {
    alias(libs.plugins.tranzlate.android.library)
    alias(libs.plugins.tranzlate.android.room)
    alias(libs.plugins.tranzlate.hilt)
    // Robolectric, so a DAO query can be RUN against a real SQLite in the ordinary
    // unit-test task instead of being reasoned about (#130 PR-19).
    //
    // The plugin is named for Compose because that is what #186 needed it for, and
    // this module has no Compose in it. It is applied anyway because it is the
    // project's ONE Robolectric wiring — including the pinned offline SDK image
    // that #163 exists for — and copying that wiring into a second build file is
    // exactly what its own KDoc says it was written to stop. Splitting a
    // `tranzlate.robolectric` plugin out of it is a build-logic change and belongs
    // in its own PR, not smuggled into a sheet PR.
    alias(libs.plugins.tranzlate.compose.test)
}

android {
    // MigrationThreeToFourTest runs the migration's real SQL under Robolectric, and
    // room-testing's `MigrationTestHelper` reads the exported schemas through the
    // unit-test AssetManager as `<db-fully-qualified-name>/<version>.json` — it does
    // NOT read the `schemaDirectory` on the filesystem (verified against
    // room-testing 2.8.4: `AndroidMigrationTestHelper.loadSchema` opens from assets).
    // The Room gradle plugin exports those same JSONs to `schemas/`, so point the
    // unit-test asset source at that directory rather than duplicating the files.
    // `test`, not `main`: schema exports are test inputs and have no place in the
    // shipped APK. (#241)
    sourceSets {
        getByName("test") {
            assets.directories.add("schemas")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    // MigrationTestHelper — runs a migration step's real SQL against a v3 database
    // built from the exported `3.json` and validates the result against `4.json`,
    // under Robolectric in the ordinary unit-test task (#241). The alias is pinned
    // to the same `room` version as runtime/compiler in the catalogue.
    testImplementation(libs.androidx.room.testing)
}
