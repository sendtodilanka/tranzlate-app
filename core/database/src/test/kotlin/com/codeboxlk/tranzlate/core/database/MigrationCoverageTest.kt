package com.codeboxlk.tranzlate.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The release gate behind the schema stance in [TRANZLATE_MIGRATIONS].
 *
 * `fallbackToDestructiveMigration` used to make "bumped the version, forgot the
 * migration" survive review — silently, by deleting the user's history on their
 * next launch. It is gone, so the same mistake now has to be caught here instead:
 * bump [TRANZLATE_DB_VERSION] without adding a `Migration` and this fails.
 *
 * This is a structural gate, not a SQL one — it asserts the chain is complete, not
 * that each step produces the right schema. Verifying the SQL needs Room's
 * `MigrationTestHelper` on a device, and the instrumentation suite is red on
 * API 35+ (issue #40); that verification is recorded as follow-up rather than
 * pretended.
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
}
