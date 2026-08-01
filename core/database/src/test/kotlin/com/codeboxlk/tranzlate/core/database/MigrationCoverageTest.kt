package com.codeboxlk.tranzlate.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * The release gate behind the schema stance in [TRANZLATE_MIGRATIONS].
 *
 * `fallbackToDestructiveMigration` used to make "bumped the version, forgot the
 * migration" survive review — silently, by deleting the user's history on their
 * next launch. It is gone, so the same mistake now has to be caught here instead:
 * bump [TRANZLATE_DB_VERSION] without adding a `Migration` and this fails.
 *
 * This is a structural gate, not a SQL one — it asserts the chain is complete
 * (and, since v3, that every version's exported schema is committed), not that
 * each step produces the right schema. Verifying the SQL needs Room's
 * `MigrationTestHelper` on a device, and the instrumentation suite is red on
 * API 35+ (issue #40); that verification is recorded as follow-up rather than
 * pretended (#111).
 */
class MigrationCoverageTest {
    @Test
    fun `migration chain covers every version up to the current one`() {
        val steps = TRANZLATE_MIGRATIONS.map { it.startVersion to it.endVersion }.sortedBy { it.first }

        assertThat(steps).hasSize(TRANZLATE_DB_VERSION - 1)
        steps.forEachIndexed { index, (start, end) ->
            assertThat(start).isEqualTo(index + 1)
            assertThat(end).isEqualTo(index + 2)
        }
    }

    @Test
    fun `the chain ends at the version the database declares`() {
        assertThat(TRANZLATE_MIGRATIONS.maxOf { it.endVersion }).isEqualTo(TRANZLATE_DB_VERSION)
    }

    @Test
    fun `no migration is registered twice for the same step`() {
        val steps = TRANZLATE_MIGRATIONS.map { it.startVersion to it.endVersion }

        assertThat(steps).containsNoDuplicates()
    }

    /**
     * The v3 step by name (issue #122): a 15a install upgrading into the
     * language-usage build must walk 2→3, and this fails the moment someone
     * "simplifies" the chain by dropping the step the generic assertions would
     * then re-number around.
     */
    @Test
    fun `the language_usage step covers 2 to 3`() {
        val step = TRANZLATE_MIGRATIONS.single { it.startVersion == 2 }

        assertThat(step.endVersion).isEqualTo(3)
        assertThat(step).isEqualTo(MigrationTwoToThree)
    }

    /**
     * The other half of the "same commit" rule in [TRANZLATE_MIGRATIONS]: every
     * version that ever shipped has its exported `<n>.json` committed — that
     * file is what a future `MigrationTestHelper` run (#111) and every schema
     * diff in review reads. Located from the checkout root (same technique as
     * `KonsistArchitectureTest`) so the gate holds no matter which directory
     * Gradle runs the test in.
     */
    @Test
    fun `every schema version up to the current one is exported and committed`() {
        val checkoutRoot =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
        val schemaDir =
            File(checkoutRoot, "core/database/schemas/com.codeboxlk.tranzlate.core.database.TranzlateDatabase")

        val missing =
            (1..TRANZLATE_DB_VERSION)
                .filterNot { version -> File(schemaDir, "$version.json").isFile }
                .map { version -> "$version.json" }
        assertThat(missing).isEmpty()
    }
}
