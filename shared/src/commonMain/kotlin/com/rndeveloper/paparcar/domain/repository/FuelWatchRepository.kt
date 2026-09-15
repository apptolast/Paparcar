package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.fuel.FollowedFuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelPriceSample
import com.rndeveloper.paparcar.domain.model.FuelType
import kotlinx.coroutines.flow.Flow

/**
 * What the user follows, and what those pumps have charged.
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * The I/O half of the two fuel verdicts: the evaluators stay pure and synchronous, this reads and
 * writes. Local-only by construction for 1.x — a second device rebuilds its own series from the day
 * it starts looking, and a shared history is a backend question
 * [INFRA-AN-ALERT-THAT-TRAVELS-NEEDS-A-BACKEND-001].
 */
interface FuelWatchRepository {

    fun observeFollowed(): Flow<List<FollowedFuelStation>>

    suspend fun getFollowed(): List<FollowedFuelStation>

    suspend fun follow(station: FollowedFuelStation)

    suspend fun unfollow(stationKey: String)

    /**
     * Writes today's reading for each sample handed in, one row per station, fuel and local day.
     *
     * ⛔ Idempotent within a day BY DESIGN: calling this eight times on one afternoon leaves one row,
     * the last. That is what keeps the history a series instead of a log — and what lets any caller
     * record opportunistically (a lane opened, a worker woken) without thinking about frequency.
     */
    suspend fun recordSamples(samples: List<FuelPriceSample>)

    /** The series of one station and fuel, oldest first, within the retention window. */
    suspend fun historyFor(stationKey: String, fuelType: FuelType, days: Int = RETENTION_DAYS): List<FuelPriceSample>

    fun observeHistoryFor(stationKey: String, fuelType: FuelType, days: Int = RETENTION_DAYS): Flow<List<FuelPriceSample>>

    /** Drops readings past the retention and series of stations nobody follows any more. */
    suspend fun prune()

    companion object {
        /**
         * Thirty days. Not a claim about when a price stops being true — it is the window Plus
         * shows; free sees 7, gated above this store exactly like every other history in the app.
         */
        const val RETENTION_DAYS = 30
    }
}
