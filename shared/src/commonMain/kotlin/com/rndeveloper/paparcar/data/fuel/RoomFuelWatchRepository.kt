@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.data.datasource.local.room.FollowedFuelStationEntity
import com.rndeveloper.paparcar.data.datasource.local.room.FuelPriceHistoryEntity
import com.rndeveloper.paparcar.data.datasource.local.room.FuelWatchDao
import com.rndeveloper.paparcar.domain.fuel.FollowedFuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelPriceSample
import com.rndeveloper.paparcar.domain.fuel.localDayOf
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.repository.FuelWatchRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** Room behind [FuelWatchRepository]. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
class RoomFuelWatchRepository(
    private val dao: FuelWatchDao,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : FuelWatchRepository {

    override fun observeFollowed(): Flow<List<FollowedFuelStation>> =
        dao.observeFollowed().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getFollowed(): List<FollowedFuelStation> =
        dao.getFollowed().map { it.toDomain() }

    override suspend fun follow(station: FollowedFuelStation) {
        dao.follow(
            FollowedFuelStationEntity(
                stationKey = station.stationKey,
                source = station.source,
                id = station.id,
                name = station.name,
                latitude = station.latitude,
                longitude = station.longitude,
                followedAtMs = station.followedAtMs,
            ),
        )
    }

    override suspend fun unfollow(stationKey: String) {
        dao.unfollow(stationKey)
        // ⛔ The series goes with it, and it goes NOW rather than at the next prune: an unfollow is
        // the user withdrawing a subject, and keeping its price history around would be keeping a
        // record of a place they just said they do not want watched.
        dao.deleteOrphanedHistory()
    }

    override suspend fun recordSamples(samples: List<FuelPriceSample>) {
        if (samples.isEmpty()) return
        dao.upsertSamples(
            samples.map { sample ->
                FuelPriceHistoryEntity(
                    stationKey = sample.stationKey,
                    fuelType = sample.fuelType.name,
                    priceAmount = sample.perUnit.amount,
                    priceCurrency = sample.perUnit.currency,
                    reportedAtMs = sample.reportedAtMs,
                    observedAtMs = sample.observedAtMs,
                    observedDay = sample.observedDay,
                    isSelf = sample.isSelf,
                )
            },
        )
    }

    override suspend fun historyFor(stationKey: String, fuelType: FuelType, days: Int): List<FuelPriceSample> =
        dao.historyFor(stationKey, fuelType.name, sinceDay(days)).mapNotNull { it.toDomain() }

    override fun observeHistoryFor(stationKey: String, fuelType: FuelType, days: Int): Flow<List<FuelPriceSample>> =
        dao.observeHistoryFor(stationKey, fuelType.name, sinceDay(days))
            .map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun prune() {
        dao.pruneHistoryBefore(sinceDay(FuelWatchRepository.RETENTION_DAYS))
        dao.deleteOrphanedHistory()
    }

    private fun sinceDay(days: Int): Long = localDayOf(clock()) - days

    private fun FollowedFuelStationEntity.toDomain() = FollowedFuelStation(
        stationKey = stationKey,
        source = source,
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        followedAtMs = followedAtMs,
    )

    /**
     * Null for a row whose `fuelType` no longer names anything — an enum entry can be renamed and the
     * disk cannot be. Dropping the row is right: an unreadable measurement is not a measurement, and
     * the alternative is a crash on open [DATA-A-DB-IT-CANNOT-OPEN-MUST-NOT-DEAD-END-THE-USER-001].
     */
    private fun FuelPriceHistoryEntity.toDomain(): FuelPriceSample? {
        val fuel = FuelType.entries.firstOrNull { it.name == fuelType } ?: run {
            PaparcarLogger.w(TAG, "dropping price row with unknown fuelType=$fuelType")
            return null
        }
        return FuelPriceSample(
            stationKey = stationKey,
            fuelType = fuel,
            perUnit = Money(amount = priceAmount, currency = priceCurrency),
            reportedAtMs = reportedAtMs,
            observedAtMs = observedAtMs,
            observedDay = observedDay,
            isSelf = isSelf,
        )
    }

    private companion object {
        const val TAG = "FuelWatch"
    }
}
