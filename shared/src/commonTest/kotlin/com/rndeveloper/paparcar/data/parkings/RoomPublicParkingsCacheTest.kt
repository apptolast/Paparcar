package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.data.datasource.local.room.OsmCoverageDao
import com.rndeveloper.paparcar.data.datasource.local.room.OsmCoverageEntity
import com.rndeveloper.paparcar.data.datasource.local.room.PublicParkingCacheDao
import com.rndeveloper.paparcar.data.datasource.local.room.PublicParkingCacheEntity
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The disk half of the car-park layer. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ The invariant under test is the one that separates a cache from a pile: **a fresh answer is the
 * truth about its whole disc.** A store that only ever adds cannot remove anything, so a facility
 * deleted upstream would be served for as long as the app lives — and nothing downstream could tell.
 */
class RoomPublicParkingsCacheTest {

    private val lat = 40.4168
    private val lon = -3.7038
    private val query = PublicParkingsQuery(lat, lon, countryCode = "ES", radiusMeters = 1_500)

    private var now = 1_000_000L
    private val parkingDao = FakePublicParkingCacheDao()
    private val coverageDao = FakeOsmCoverageDao()
    private val cache = RoomPublicParkingsCache(parkingDao, coverageDao) { now }

    private fun parking(id: String, metersNorth: Double) = PublicParking(
        id = id,
        name = "Car park $id",
        operator = null,
        kind = PublicParkingKind.UNDERGROUND,
        latitude = lat + metersNorth / 111_320.0,
        longitude = lon,
        fee = FeeInfo.PAID,
    )

    /**
     * ⛔ Never asked is NOT empty, and the whole coverage table exists for this difference.
     *
     * Returning an empty list here would tell the layer "there are no car parks around you", which
     * is a claim nobody made.
     */
    @Test
    fun `should_returnNull_when_nobodyHasAskedAboutTheArea`() = runTest {
        assertNull(cache.get(query))
    }

    /**
     * ⛔ And the mirror of it: an area we DID ask about, that had nothing, is a fact worth keeping.
     *
     * Without this, every return to a genuinely empty street would look like a cache miss and spend
     * another request on donated infrastructure to be told the same nothing.
     */
    @Test
    fun `should_rememberAnEmptyAreaAsEmpty_when_theAnswerCarriedNoParkings`() = runTest {
        cache.put(query, emptyList())

        val cached = assertNotNull(cache.get(query))
        assertTrue(cached.parkings.isEmpty())
    }

    @Test
    fun `should_serveWhatWasSaved_when_theSameAreaIsAskedAgain`() = runTest {
        cache.put(query, listOf(parking("a", 200.0), parking("b", 400.0)))

        val cached = assertNotNull(cache.get(query))
        assertEquals(listOf("a", "b"), cached.parkings.map { it.id })
    }

    /**
     * ⛔ The headline: a facility that stops coming back **disappears**.
     *
     * This is the case a row-only store gets wrong and cannot notice. A bounding-box read is the
     * union of everything ever written there, so without reconciling the disc, "b" would outlive its
     * removal upstream by thirty days.
     */
    @Test
    fun `should_dropAFacility_when_aFreshAnswerNoLongerCarriesIt`() = runTest {
        cache.put(query, listOf(parking("a", 200.0), parking("b", 400.0)))

        now += 60_000
        cache.put(query, listOf(parking("a", 200.0)))

        val cached = assertNotNull(cache.get(query))
        assertEquals(listOf("a"), cached.parkings.map { it.id })
    }

    /** The other two thirds of the same reconciliation: an arrival lands, a survivor stays. */
    @Test
    fun `should_addAndKeep_when_aFreshAnswerBringsBothOldAndNew`() = runTest {
        cache.put(query, listOf(parking("a", 200.0)))

        now += 60_000
        cache.put(query, listOf(parking("a", 200.0), parking("c", 600.0)))

        val cached = assertNotNull(cache.get(query))
        assertEquals(listOf("a", "c"), cached.parkings.map { it.id })
    }

    /**
     * The age shown is the OLDEST disc leaned on, never the newest row.
     *
     * A refresh that re-saw one facility must not make the whole area look freshly measured — the
     * weakest claim is the honest one to quote.
     */
    @Test
    fun `should_reportTheOldestAnsweringDisc_when_severalCover`() = runTest {
        cache.put(query, listOf(parking("a", 200.0)))
        val firstStamp = now

        now += 5 * 60_000
        // A wider disc on the same cell replaces this one's stamp, so cover the query from a
        // SEPARATE cell whose disc is younger and still contains it.
        cache.put(
            query.copy(latitude = lat + 100.0 / 111_320.0, radiusMeters = 3_000),
            listOf(parking("a", 200.0)),
        )

        val cached = assertNotNull(cache.get(query))
        assertEquals(firstStamp, cached.fetchedAt)
    }

    /**
     * ⛔ Retention prunes DISCS, and a facility no live disc covers goes with them.
     *
     * The tempting version caps facilities directly, and it lies: rows vanish while the disc that
     * promised them stays behind, so the next cold start reports a hole as a measured "nothing
     * here".
     */
    @Test
    fun `should_deleteFacilities_when_noLiveDiscCoversThemAnyMore`() = runTest {
        cache.put(query, listOf(parking("a", 200.0)))

        // Well past the thirty-day retention, asking somewhere else entirely.
        now += 31L * 24 * 60 * 60 * 1_000
        val faraway = query.copy(latitude = 41.9, longitude = 2.8)
        cache.put(faraway, emptyList())

        assertTrue(parkingDao.rows.isEmpty(), "expected the orphaned facility to be gone")
        assertNull(cache.get(query))
    }

    /**
     * The other way a disc disappears: the **count cap**, with nothing expired.
     * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
     *
     * ⛔ This is the witness the orphan sweep was missing. The test above it passes through row
     * expiry — every row was 31 days old — so it never proved the sweep ran, and the sweep is now
     * skipped when no disc left. Here nothing is old enough to expire and the cap is what drops a
     * disc, which is the only path that can orphan a row that is still young.
     */
    @Test
    fun `should_deleteFacilities_when_theDiscCapDropsTheirDisc`() = runTest {
        // The oldest disc, far from everything else, with a facility only it covers.
        cache.put(query, listOf(parking("orphan-to-be", 200.0)))

        // Sixty younger discs elsewhere, each its own cell: the cap keeps the newest sixty.
        repeat(MAX_DISCS) { i ->
            now += 1_000
            cache.put(query.copy(latitude = 10.0 + i, longitude = 10.0 + i), emptyList())
        }

        assertTrue(
            parkingDao.rows.none { it.key == "orphan-to-be" },
            "the capped disc's facility must go with it, not linger uncovered",
        )
        assertNull(cache.get(query))
    }

    /**
     * ⛔ The common write must not read the whole table. `put` is awaited **before** the fresh
     * answer reaches the lane, so the orphan sweep is time the user spends looking at a spinner —
     * and re-asking a zone orphans nothing, because its disc is replaced in place.
     */
    @Test
    fun `should_notScanEveryRow_when_reAskingAZoneThatIsAlreadyCached`() = runTest {
        cache.put(query, listOf(parking("a", 200.0)))
        val scansAfterTheFirstWrite = parkingDao.getAllCalls

        now += 60_000
        cache.put(query, listOf(parking("a", 200.0), parking("b", 400.0)))

        assertEquals(scansAfterTheFirstWrite, parkingDao.getAllCalls)
    }

    private companion object {
        /** Mirrors `RoomPublicParkingsCache.MAX_DISCS`, which is private to the class under test. */
        const val MAX_DISCS = 60
    }
}

internal class FakePublicParkingCacheDao : PublicParkingCacheDao {
    val rows = mutableMapOf<String, PublicParkingCacheEntity>()

    /** How many times the orphan sweep read the WHOLE table — the write path's dearest line. */
    var getAllCalls = 0

    override suspend fun getInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<PublicParkingCacheEntity> = rows.values.filter {
        it.latitude in minLat..maxLat && it.longitude in minLon..maxLon
    }

    override suspend fun upsertAll(rows: List<PublicParkingCacheEntity>) {
        rows.forEach { this.rows[it.id] = it }
    }

    override suspend fun deleteByIds(ids: List<String>) {
        ids.forEach { rows.remove(it) }
    }

    override suspend fun evictExpired(expiryMs: Long) {
        rows.values.filter { it.cachedAt < expiryMs }.forEach { rows.remove(it.id) }
    }

    override suspend fun getAll(): List<PublicParkingCacheEntity> {
        getAllCalls++
        return rows.values.toList()
    }

    override suspend fun deleteAll() = rows.clear()
}

internal class FakeOsmCoverageDao : OsmCoverageDao {
    val discs = mutableMapOf<String, OsmCoverageEntity>()

    override suspend fun getLive(layer: String, minFetchedAt: Long): List<OsmCoverageEntity> =
        discs.values.filter { it.layer == layer && it.fetchedAt >= minFetchedAt }

    override suspend fun upsert(disc: OsmCoverageEntity) {
        discs[disc.discKey] = disc
    }

    override suspend fun evictExpired(expiryMs: Long) {
        discs.values.filter { it.fetchedAt < expiryMs }.forEach { discs.remove(it.discKey) }
    }

    override suspend fun evictBeyond(layer: String, keep: Int) {
        discs.values
            .filter { it.layer == layer }
            .sortedByDescending { it.fetchedAt }
            .drop(keep)
            .forEach { discs.remove(it.discKey) }
    }

    override suspend fun deleteAll() = discs.clear()
}
