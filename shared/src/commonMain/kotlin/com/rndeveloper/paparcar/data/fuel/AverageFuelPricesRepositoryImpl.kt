@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.AverageFuelPriceTable
import com.rndeveloper.paparcar.domain.fuel.AverageFuelPricesSource
import com.rndeveloper.paparcar.domain.repository.AverageFuelPricesRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * Holds the national-average table, starting at the snapshot shipped in the binary.
 * [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * Starts at [AverageFuelPriceTable.Embedded], so the very first calculation after a cold install,
 * with the radio off, still prices a drive. A refresh replaces it; a failed refresh leaves it
 * standing.
 *
 * ⛔ **In memory, not on disk, and that is a decision with a reason** — the same one
 * [CachingFuelStationsSource] reached for the station lists. Persisting means either a Room schema
 * bump with its `Migration`, or a serialised blob in DataStore that then has its own versioning
 * problem, and both buy only the minutes between a cold start and the first refresh. The embedded
 * table covers exactly that gap, and it ships fresh with every release because a script regenerates
 * it rather than a person remembering to. If a measurement ever shows cold starts commonly going a
 * long time without a refresh, this is the class that grows a disk and nothing above it changes.
 */
class AverageFuelPricesRepositoryImpl(
    private val source: AverageFuelPricesSource,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AverageFuelPricesRepository {

    private val lock = Mutex()

    @Volatile
    private var table: AverageFuelPriceTable = AverageFuelPriceTable.Embedded

    @Volatile
    private var refreshedAtMs: Long? = null

    override fun current(): AverageFuelPriceTable = table

    override suspend fun refresh(): AverageFuelPriceTable = lock.withLock {
        if (!isStale()) return table
        source.latest().onSuccess { fresh ->
            table = fresh
            refreshedAtMs = clock()
        }
        return table
    }

    /**
     * Whether enough time has passed to be worth asking again.
     *
     * ⛔ The stamp moves only on SUCCESS. A failed refresh leaves the app free to try again at the
     * next opportunity instead of sitting out a whole day because the radio was off for one second
     * — which is the bug the obvious version of this has.
     */
    private fun isStale(): Boolean {
        val last = refreshedAtMs ?: return true
        return clock() - last >= ttlMs
    }

    private companion object {
        /**
         * A day. National averages are published daily at best — the feed's own cache is six hours
         * and its rows carry a date, not a timestamp — so asking more often would spend a request
         * to receive the same numbers. This is not the pump price on the corner; it is the figure
         * behind "gasto estimado", and it moves in cents per week.
         */
        const val DEFAULT_TTL_MS = 24L * 60 * 60 * 1000
    }
}
