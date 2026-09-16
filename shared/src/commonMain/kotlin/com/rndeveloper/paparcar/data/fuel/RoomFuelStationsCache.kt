@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.data.cache.CoverageDisc
import com.rndeveloper.paparcar.data.cache.CoverageLayer
import com.rndeveloper.paparcar.data.cache.boundingBox
import com.rndeveloper.paparcar.data.cache.covers
import com.rndeveloper.paparcar.data.cache.coverageKey
import com.rndeveloper.paparcar.data.cache.effectiveRadiusMeters
import com.rndeveloper.paparcar.data.datasource.local.room.FuelStationCacheDao
import com.rndeveloper.paparcar.data.datasource.local.room.FuelStationCacheEntity
import com.rndeveloper.paparcar.data.datasource.local.room.FuelStationFuelEntity
import com.rndeveloper.paparcar.data.datasource.local.room.OsmCoverageDao
import com.rndeveloper.paparcar.data.datasource.local.room.OsmCoverageEntity
import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.fuelStationKey
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.time.Clock

/** What the disk can say about an area: the forecourts, and when we last managed to ask. */
data class CachedFuelStations(
    val stations: List<FuelStation>,
    /** The stamp of the OLDEST disc that answers, never the newest row. */
    val fetchedAt: Long,
)

/**
 * The fuel layer's disk. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * Shares [CoverageDisc] and its geometry with the car-park lane, on its own [CoverageLayer]: the two
 * never lend each other coverage, because a 5 km fuel answer says nothing about car parks and a
 * 1,5 km car-park answer says nothing about forecourts.
 *
 * ⛔ **The station is saved, the price is saved with its own hour, and no TTL is invented for
 * either.** `CachingFuelStationsSource` refused to persist precisely because nobody had measured how
 * long a price stays useful — Italy carries 69% of its prices at two to seven days and a tail to
 * 2013. That objection stands, and this class does not overrule it: it sidesteps it by keeping the
 * two halves apart. Geography gets a retention because a forecourt does not move; the price gets
 * [StationPrice.reportedAtMs], which it already carried, and the reader goes on saying the age
 * exactly as it does for a live answer. No number about price freshness is written into the schema.
 */
class RoomFuelStationsCache(
    private val dao: FuelStationCacheDao,
    private val coverageDao: OsmCoverageDao,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /** What the disk holds for [query], or null when nothing recorded having asked about it. */
    suspend fun get(query: FuelStationsQuery): CachedFuelStations? {
        val now = clock()
        val answering = coverageDao.getLive(CoverageLayer.FUEL_STATIONS, now - RETENTION_MS)
            .map { it.toDisc() }
            .filter { it.covers(query.latitude, query.longitude, query.radiusMeters) }
        // No covering disc is "nobody asked", NOT "there are none" — see [OsmCoverageEntity].
        if (answering.isEmpty()) return null

        val box = boundingBox(query.latitude, query.longitude, query.radiusMeters)
        val rows = dao
            .getInBoundingBox(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)
            .filter { it.distanceTo(query) <= query.radiusMeters }
            .sortedBy { it.distanceTo(query) }
        val fuels = if (rows.isEmpty()) emptyList() else dao.getFuelsFor(rows.map { it.stationKey })
        val byStation = fuels.groupBy { it.stationKey }

        return CachedFuelStations(
            stations = rows.map { it.toDomain(byStation[it.stationKey].orEmpty()) },
            fetchedAt = answering.minOf { it.fetchedAt },
        )
    }

    /**
     * Records a fresh answer as the truth about its disc: stations inside it that did not come back
     * are deleted, the rest are upserted, and each surviving station's fuel rows are REPLACED rather
     * than merged — a diesel price that disappeared upstream must not survive as a leftover row.
     */
    suspend fun put(query: FuelStationsQuery, result: FuelStationsResult) {
        val now = clock()
        val radius = effectiveRadiusMeters(
            requestedRadiusMeters = query.radiusMeters,
            centreLatitude = query.latitude,
            centreLongitude = query.longitude,
            resultCoordinates = result.stations.map { it.latitude to it.longitude },
            isTruncated = result.isTruncated,
        )
        val disc = CoverageDisc(query.latitude, query.longitude, radius, now)
        val keyed = result.stations.associateBy { stationKey(result.source, it.id) }

        val box = boundingBox(query.latitude, query.longitude, radius)
        val gone = dao
            .getInBoundingBox(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)
            .filter { disc.covers(it.latitude, it.longitude, queryRadiusMeters = 0) }
            .map { it.stationKey }
            .filterNot { it in keyed }
        if (gone.isNotEmpty()) {
            dao.deleteStations(gone)
            dao.deleteFuelsFor(gone)
        }

        dao.upsertStations(keyed.map { (key, station) -> station.toEntity(key, result.source, now) })
        // ⛔ Replace, never merge: an upsert alone would leave a fuel row that the fresh answer no
        // longer carries, and the station would go on quoting a pump it may not have.
        dao.deleteFuelsFor(keyed.keys.toList())
        dao.upsertFuels(keyed.flatMap { (key, station) -> station.fuelRows(key) })

        coverageDao.upsert(
            OsmCoverageEntity(
                discKey = coverageKey(
                    CoverageLayer.FUEL_STATIONS, query.latitude, query.longitude, query.radiusMeters
                ),
                layer = CoverageLayer.FUEL_STATIONS,
                latitude = query.latitude,
                longitude = query.longitude,
                radiusMeters = radius,
                fetchedAt = now,
            )
        )
        evict(now)
    }

    /** Retention prunes DISCS; stations no live disc covers follow, and their fuel rows follow them. */
    private suspend fun evict(now: Long) {
        val expiry = now - RETENTION_MS
        coverageDao.evictExpired(expiry)
        coverageDao.evictBeyond(CoverageLayer.FUEL_STATIONS, MAX_DISCS)
        dao.evictExpiredStations(expiry)

        val live = coverageDao.getLive(CoverageLayer.FUEL_STATIONS, expiry).map { it.toDisc() }
        val orphans = dao.getAllStations()
            .filterNot { row -> live.any { it.covers(row.latitude, row.longitude, 0) } }
            .map { it.stationKey }
        if (orphans.isNotEmpty()) dao.deleteStations(orphans)
        dao.deleteOrphanedFuels()
    }

    private fun FuelStationCacheEntity.distanceTo(query: FuelStationsQuery): Double =
        haversineMeters(query.latitude, query.longitude, latitude, longitude)

    private fun OsmCoverageEntity.toDisc() =
        CoverageDisc(latitude, longitude, radiusMeters, fetchedAt)

    private fun FuelStation.toEntity(key: String, source: String, now: Long) = FuelStationCacheEntity(
        stationKey = key,
        source = source,
        id = id,
        name = name,
        address = address,
        town = town,
        latitude = latitude,
        longitude = longitude,
        schedule = schedule,
        cachedAt = now,
    )

    /**
     * One row per fuel this station says anything about — a price, a verdict, or both.
     *
     * ⛔ Fuels it says NOTHING about get no row, because that is what UNKNOWN means and inventing a
     * row for it would turn silence into a claim.
     */
    private fun FuelStation.fuelRows(key: String): List<FuelStationFuelEntity> =
        (prices.keys + availability.keys).map { fuel ->
            val price = prices[fuel]
            FuelStationFuelEntity(
                stationKey = key,
                fuelType = fuel.name,
                priceAmount = price?.perUnit?.amount,
                priceCurrency = price?.perUnit?.currency,
                reportedAtMs = price?.reportedAtMs,
                availability = availability[fuel]?.name,
                // ⛔ Rides along or the disk turns Italy's attended price into a self one on the
                // next cold start. [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
                isSelf = price?.isSelf,
            )
        }

    private fun FuelStationCacheEntity.toDomain(fuels: List<FuelStationFuelEntity>): FuelStation {
        val prices = mutableMapOf<FuelType, StationPrice>()
        val availability = mutableMapOf<FuelType, FuelAvailability>()
        fuels.forEach { row ->
            // A fuel name that no longer parses was written by an older build against an enum that
            // has since changed. Dropping the row is the honest reading: we cannot say what it meant.
            val fuel = runCatching { FuelType.valueOf(row.fuelType) }.getOrNull() ?: return@forEach
            val amount = row.priceAmount
            val currency = row.priceCurrency
            if (amount != null && currency != null) {
                prices[fuel] = StationPrice(Money(amount, currency), row.reportedAtMs, row.isSelf)
            }
            row.availability
                ?.let { name -> runCatching { FuelAvailability.valueOf(name) }.getOrNull() }
                ?.let { availability[fuel] = it }
        }
        return FuelStation(
            id = id,
            name = name,
            address = address,
            town = town,
            latitude = latitude,
            longitude = longitude,
            prices = prices,
            availability = availability,
            schedule = schedule,
        )
    }

    private companion object {
        /**
         * Thirty days — the retention of the FORECOURT, which is geography and does not move. ⛔ Not
         * a statement about prices: those carry their own hour and are read exactly as a live price
         * is read.
         */
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1_000

        /** Discs kept, which bounds the stations too since each disc brings a capped answer. */
        const val MAX_DISCS = 60

        /**
         * ⛔ `source|id`: [FuelStation.id] is only unique WITHIN its feed, and three feeds share
         * this table. See [FuelStationCacheEntity.stationKey].
         *
         * The rule moved to `domain/fuel/fuelStationKey` when the watch store started keying follows
         * by the same identity — one spelling for both, or the day they disagree a followed station
         * quietly stops matching its own cached row. This stays as the cache's own doorway to it.
         */
        fun stationKey(source: String, id: String) = fuelStationKey(source, id)
    }
}
