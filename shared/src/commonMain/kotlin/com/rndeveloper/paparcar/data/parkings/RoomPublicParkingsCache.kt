@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.data.cache.CoverageDisc
import com.rndeveloper.paparcar.data.cache.CoverageLayer
import com.rndeveloper.paparcar.data.cache.boundingBox
import com.rndeveloper.paparcar.data.cache.covers
import com.rndeveloper.paparcar.data.cache.coverageKey
import com.rndeveloper.paparcar.data.cache.effectiveRadiusMeters
import com.rndeveloper.paparcar.data.datasource.local.room.OsmCoverageDao
import com.rndeveloper.paparcar.data.datasource.local.room.OsmCoverageEntity
import com.rndeveloper.paparcar.data.datasource.local.room.PublicParkingCacheDao
import com.rndeveloper.paparcar.data.datasource.local.room.PublicParkingCacheEntity
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.time.Clock

/** What the disk can say about an area: the facilities, and when we last asked. */
data class CachedPublicParkings(
    val parkings: List<PublicParking>,
    /** The stamp of the OLDEST disc that answers, never the newest row. */
    val fetchedAt: Long,
)

/**
 * The public-car-park layer's disk, so a cold start or a dead radio does not leave the map empty.
 * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ This is **L2: today's answer wins whenever there is one**, and the disk is what there is
 * instead of nothing. Since
 * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001] it is also what the lane paints
 * **while** it asks — see `DiskBackedPublicParkingsSource.remembered`. What it never does is
 * override a live answer.
 *
 * The whole design rests on `osm_coverage`: rows are facilities, but the thing with an age is the
 * *question* — "everything of this kind within R metres of P, as of then". See [OsmCoverageEntity]
 * for the three failures that storing only rows produces.
 */
class RoomPublicParkingsCache(
    private val parkingDao: PublicParkingCacheDao,
    private val coverageDao: OsmCoverageDao,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /** What the disk holds for [query], or null when nothing recorded having asked about it. */
    suspend fun get(query: PublicParkingsQuery): CachedPublicParkings? {
        val now = clock()
        val answering = coverageDao.getLive(CoverageLayer.PUBLIC_PARKINGS, now - RETENTION_MS)
            .map { it.toDisc() }
            .filter { it.covers(query.latitude, query.longitude, query.radiusMeters) }
        // ⛔ No covering disc is NOT an empty answer: it is "nobody asked", and the difference is
        // the whole point of the coverage table. An empty area that WAS asked about answers below,
        // with zero rows, which is a fact rather than a silence.
        if (answering.isEmpty()) return null

        val box = boundingBox(query.latitude, query.longitude, query.radiusMeters)
        val parkings = parkingDao
            .getInBoundingBox(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)
            .filter { it.isWithin(query) }
            .sortedBy { haversineMeters(query.latitude, query.longitude, it.latitude, it.longitude) }
            .map { it.toDomain() }

        return CachedPublicParkings(
            parkings = parkings,
            // The oldest, because that is the age of the weakest claim being leaned on.
            fetchedAt = answering.minOf { it.fetchedAt },
        )
    }

    /**
     * Records a fresh answer as the truth about its disc: what is inside and did not come back is
     * **deleted**, what came back is upserted, and the disc is stamped.
     *
     * The deletion is the half that makes this a cache rather than a pile. Without it a
     * bounding-box read is the union of everything ever written and a car park removed upstream
     * would be served forever.
     */
    suspend fun put(query: PublicParkingsQuery, parkings: List<PublicParking>) {
        val now = clock()
        val radius = effectiveRadiusMeters(
            requestedRadiusMeters = query.radiusMeters,
            centreLatitude = query.latitude,
            centreLongitude = query.longitude,
            resultCoordinates = parkings.map { it.latitude to it.longitude },
            // One source in this lane, so the cap is knowable here. The fuel lane has three and
            // carries the flag on its result instead.
            isTruncated = parkings.size >= OsmPublicParkingParser.MAX_RESULTS,
        )
        val disc = CoverageDisc(query.latitude, query.longitude, radius, now)

        val box = boundingBox(query.latitude, query.longitude, radius)
        val fresh = parkings.map { it.id }.toSet()
        val gone = parkingDao
            .getInBoundingBox(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)
            .filter { disc.covers(it.latitude, it.longitude, queryRadiusMeters = 0) }
            .map { it.id }
            .filterNot { it in fresh }
        if (gone.isNotEmpty()) parkingDao.deleteByIds(gone)

        parkingDao.upsertAll(parkings.map { it.toEntity(now) })
        coverageDao.upsert(
            OsmCoverageEntity(
                discKey = coverageKey(
                    CoverageLayer.PUBLIC_PARKINGS, query.latitude, query.longitude, query.radiusMeters
                ),
                layer = CoverageLayer.PUBLIC_PARKINGS,
                latitude = query.latitude,
                longitude = query.longitude,
                radiusMeters = radius,
                fetchedAt = now,
            )
        )
        evict(now)
    }

    /**
     * Retention, and it prunes **discs** — then whatever no live disc covers any more.
     *
     * ⛔ Capping facilities directly is the tempting version and it lies: it would delete rows while
     * the disc that promised them stayed behind, so the next cold start would report a hole as a
     * measured "nothing here". A row exists to serve a disc; when no disc wants it, it goes.
     */
    private suspend fun evict(now: Long) {
        val expiry = now - RETENTION_MS
        // Counted BEFORE the prune, because the sweep below is only owed when a disc actually left.
        val before = coverageDao.getLive(CoverageLayer.PUBLIC_PARKINGS, expiry).size

        coverageDao.evictExpired(expiry)
        coverageDao.evictBeyond(CoverageLayer.PUBLIC_PARKINGS, MAX_DISCS)
        parkingDao.evictExpired(expiry)

        val live = coverageDao.getLive(CoverageLayer.PUBLIC_PARKINGS, expiry).map { it.toDisc() }
        // ⛔ **A row is only orphaned when a disc DISAPPEARS.** Re-asking an area replaces its disc
        // in place (`coverageKey` is stable) and `put` has already reconciled what is inside it, so
        // the common write — panning a city that is already cached — orphans nothing and this sweep
        // would find nothing.
        //
        // It is skipped rather than kept "just in case" because it is the most expensive thing on
        // the write path — `getAll()` is the WHOLE table, up to 60 discs × the parser's 1.000-row
        // cap, cross-checked against every live disc in Kotlin — and `put` is awaited **before** the
        // fresh answer reaches the lane, so every millisecond here is a millisecond the user waits.
        //
        // ⚠️ What this leaves behind, stated rather than hidden: a disc that aged out between two
        // writes is gone from both counts, so a row it backed can linger. That row is never SERVED —
        // [get] answers only through a live disc that contains the query — so the residue is disk
        // space, bounded by the row's own 30-day expiry and swept the next time a count does drop.
        if (live.size == before) return

        val orphans = parkingDao.getAll()
            .filterNot { row -> live.any { it.covers(row.latitude, row.longitude, 0) } }
            .map { it.id }
        if (orphans.isNotEmpty()) parkingDao.deleteByIds(orphans)
    }

    private fun PublicParkingCacheEntity.isWithin(query: PublicParkingsQuery): Boolean =
        haversineMeters(query.latitude, query.longitude, latitude, longitude) <= query.radiusMeters

    private fun OsmCoverageEntity.toDisc() =
        CoverageDisc(latitude, longitude, radiusMeters, fetchedAt)

    private fun PublicParking.toEntity(now: Long) = PublicParkingCacheEntity(
        id = id,
        name = name,
        operator = operator,
        kind = kind.name,
        latitude = latitude,
        longitude = longitude,
        capacity = capacity,
        fee = fee.name,
        maxHeightMeters = maxHeightMeters,
        openingHours = openingHours,
        cachedAt = now,
    )

    private fun PublicParkingCacheEntity.toDomain() = PublicParking(
        id = id,
        name = name,
        operator = operator,
        // A name that no longer parses means the enum shrank under a row written by an older build.
        // UNKNOWN is the honest reading and already a first-class answer everywhere else.
        kind = runCatching { PublicParkingKind.valueOf(kind) }.getOrDefault(PublicParkingKind.UNKNOWN),
        latitude = latitude,
        longitude = longitude,
        capacity = capacity,
        fee = runCatching { FeeInfo.valueOf(fee) }.getOrDefault(FeeInfo.UNKNOWN),
        maxHeightMeters = maxHeightMeters,
        openingHours = openingHours,
    )

    private companion object {
        /**
         * Thirty days, the same as `geocoder_cache` and for the same reason: this is geography. A
         * car park does not move, and nothing in [PublicParking] goes stale inside a month — the one
         * perishable number, live occupancy, is deliberately not in it.
         */
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1_000

        /**
         * Discs kept, which bounds the facilities too since each brings at most `MAX_RESULTS` rows.
         * Sixty covers a long history of places actually visited without letting a map panned across
         * a country accumulate for ever.
         */
        const val MAX_DISCS = 60
    }
}
