package com.codeboxlk.tranzlate.core.usage

import com.codeboxlk.tranzlate.core.datastore.PersistedUsageCounts
import com.codeboxlk.tranzlate.core.datastore.UsageDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence seam for the metered counters (issue #66) — the policy's unit
 * tests fake this; prod adapts [UsageDataSource]. Load happens ONCE per
 * process (inside the policy's mutex); save follows every mutation.
 */
interface UsagePersistence {
    suspend fun load(): PersistedUsageCounts

    suspend fun save(counts: PersistedUsageCounts)
}

/** DataStore-backed adapter — one atomic edit per save (torn pairs impossible). */
@Singleton
class DataStoreUsagePersistence
    @Inject
    constructor(
        private val dataSource: UsageDataSource,
    ) : UsagePersistence {
        override suspend fun load(): PersistedUsageCounts = dataSource.readUsage()

        override suspend fun save(counts: PersistedUsageCounts) = dataSource.writeUsage(counts)
    }
