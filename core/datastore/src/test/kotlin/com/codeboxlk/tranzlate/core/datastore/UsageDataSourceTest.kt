package com.codeboxlk.tranzlate.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/**
 * The metered counters' read guard (issue #66), which had no test file at all
 * until issue #236 — the class was written, guarded, and reasoned about in a
 * KDoc, and nothing ever ran the guard.
 *
 * What is pinned here is the degrade the KDoc promises: an unreadable store
 * reads as EMPTY, so the quota gate opens at a full allowance rather than
 * taking the app down on the metered translate path.
 */
class UsageDataSourceTest {
    @Test
    fun `an IO failure reads as an empty store rather than propagating`() =
        runTest {
            val counts = UsageDataSource(FailingUsageDataStore(IOException("disk gone"))).readUsage()

            assertThat(counts.freeSpent).isEqualTo(0)
            assertThat(counts.proSpent).isEqualTo(0)
            assertThat(counts.dayEpoch).isEqualTo(PersistedUsageCounts.NO_DAY)
        }

    /**
     * The same claim against an `Error` (issue #236).
     *
     * The guard caught `Exception`, so this whole class of failure walked past it
     * to `Thread.defaultUncaughtExceptionHandler` — and `readUsage` is reached
     * from the quota gate, i.e. from a metered translation the user is waiting on.
     * Stated precisely: the reason is NOT `TextViewModel.kt:768-779`'s JNI
     * citation, because this store is DataStore and not Room. It is that guard's
     * general premise — nothing may escape into a scope with no handler — plus
     * #236's own argument that one persistence guard may not be narrower than the
     * next by accident.
     */
    @Test
    fun `a failure that is an Error also reads as an empty store`() =
        runTest {
            val counts =
                UsageDataSource(FailingUsageDataStore(UnsatisfiedLinkError("nativeExecute"))).readUsage()

            assertThat(counts.freeSpent).isEqualTo(0)
            assertThat(counts.proSpent).isEqualTo(0)
            assertThat(counts.dayEpoch).isEqualTo(PersistedUsageCounts.NO_DAY)
        }
}

/** A store whose every read fails with [cause] — the dying-disk stand-in. */
private class FailingUsageDataStore(
    private val cause: Throwable,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw cause }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        error("not used by these tests")
}
