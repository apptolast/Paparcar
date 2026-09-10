package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.data.datasource.local.room.FuelStationCacheDao
import com.rndeveloper.paparcar.data.datasource.local.room.FuelStationCacheEntity
import com.rndeveloper.paparcar.data.datasource.local.room.FuelStationFuelEntity
import com.rndeveloper.paparcar.data.datasource.local.room.OsmCoverageDao
import com.rndeveloper.paparcar.data.datasource.local.room.OsmCoverageEntity
import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fuel lane's disk. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ Two invariants that the car-park lane does not have to carry, both because this table is shared
 * by THREE feeds: a station is identified by **source + id**, and a fresh answer REPLACES a
 * station's fuel rows rather than merging into them.
 */
class RoomFuelStationsCacheTest {

    private val lat = 40.4168
    private val lon = -3.7038
    private val query = FuelStationsQuery(lat, lon, countryCode = "ES", radiusMeters = 5_000)

    private var now = 1_000_000L
    private val dao = FakeFuelStationCacheDao()
    private val coverageDao = FakeCoverageDao()
    private val cache = RoomFuelStationsCache(dao, coverageDao) { now }

    private fun station(
        id: String,
        metersNorth: Double = 200.0,
        diesel: Double? = null,
        reportedAtMs: Long? = null,
        availability: Map<FuelType, FuelAvailability> = emptyMap(),
    ) = FuelStation(
        id = id,
        name = "Station $id",
        address = "Calle $id",
        town = "Madrid",
        latitude = lat + metersNorth / 111_320.0,
        longitude = lon,
        prices = diesel?.let { mapOf(FuelType.DIESEL to StationPrice(Money(it, "EUR"), reportedAtMs)) }
            ?: emptyMap(),
        availability = availability,
    )

    private fun answer(
        stations: List<FuelStation>,
        source: String = "es-minetur",
        isTruncated: Boolean = false,
    ) = FuelStationsResult(stations = stations, source = source, isTruncated = isTruncated)

    @Test
    fun `should_returnNull_when_nobodyHasAskedAboutTheArea`() = runTest {
        assertNull(cache.get(query))
    }

    @Test
    fun `should_serveStationsWithTheirPrices_when_theSameAreaIsAskedAgain`() = runTest {
        cache.put(query, answer(listOf(station("a", diesel = 1.539, reportedAtMs = 999))))

        val cached = assertNotNull(cache.get(query))
        val station = cached.stations.single()
        assertEquals("a", station.id)
        assertEquals(1.539, station.prices[FuelType.DIESEL]?.perUnit?.amount)
        assertEquals("EUR", station.prices[FuelType.DIESEL]?.perUnit?.currency)
        // ⛔ The hour survives the round trip. A price without it is a number somebody invented.
        assertEquals(999, station.prices[FuelType.DIESEL]?.reportedAtMs)
    }

    /**
     * ⛔ The collision this table would have had with a bare id as its key.
     *
     * `FuelStation.id` is only stable WITHIN its source, and Spain's `IDEESS` numbers are small
     * enough to land on an OSM element id. Keyed by the bare id, the second write would overwrite
     * the first and one forecourt would silently become another.
     */
    @Test
    fun `should_keepBothStations_when_twoSourcesUseTheSameId`() = runTest {
        val far = query.copy(latitude = 48.85, longitude = 2.35)

        cache.put(query, answer(listOf(station("1234", diesel = 1.5)), source = "es-minetur"))
        cache.put(
            far,
            answer(
                listOf(
                    FuelStation(
                        id = "1234",
                        name = "Station OSM",
                        address = null,
                        town = null,
                        latitude = 48.85,
                        longitude = 2.35,
                        prices = emptyMap(),
                    ),
                ),
                source = "osm",
            ),
        )

        assertEquals(2, dao.stations.size, "one id from two feeds is two forecourts, not one")
        assertEquals("Station 1234", assertNotNull(cache.get(query)).stations.single().name)
        assertEquals("Station OSM", assertNotNull(cache.get(far)).stations.single().name)
    }

    /**
     * ⛔ A fuel that stops coming back stops being quoted.
     *
     * Upserting alone would leave the old diesel row in place, and the station would go on offering
     * a pump the fresh answer no longer mentions — the same "a store that only adds cannot remove"
     * failure as the disc reconciliation, one level down.
     */
    @Test
    fun `should_dropAPrice_when_aFreshAnswerNoLongerCarriesThatFuel`() = runTest {
        cache.put(query, answer(listOf(station("a", diesel = 1.539))))

        now += 60_000
        cache.put(query, answer(listOf(station("a", diesel = null))))

        val station = assertNotNull(cache.get(query)).stations.single()
        assertTrue(station.prices.isEmpty(), "the retired diesel price must be gone")
        assertTrue(dao.fuels.none { it.fuelType == FuelType.DIESEL.name && it.priceAmount != null })
    }

    /** And the station itself disappears when the disc no longer carries it. */
    @Test
    fun `should_dropAStation_when_aFreshAnswerNoLongerCarriesIt`() = runTest {
        cache.put(query, answer(listOf(station("a"), station("b", metersNorth = 400.0))))

        now += 60_000
        cache.put(query, answer(listOf(station("a"))))

        assertEquals(listOf("a"), assertNotNull(cache.get(query)).stations.map { it.id })
        assertTrue(dao.fuels.none { it.stationKey.endsWith("|b") }, "its fuel rows must follow it")
    }

    /**
     * ⛔ Silence is not "does not sell it".
     *
     * OSM tags `fuel:diesel` on 44% of stations; reading the quiet 56% as a negative would hide most
     * of the world's forecourts from most of its drivers. An absent row must come back as UNKNOWN.
     */
    @Test
    fun `should_readAnAbsentFuelAsUnknown_when_nobodySaidAnything`() = runTest {
        cache.put(
            query,
            answer(listOf(station("a", availability = mapOf(FuelType.GASOLINE to FuelAvailability.NOT_SOLD)))),
        )

        val station = assertNotNull(cache.get(query)).stations.single()
        assertEquals(FuelAvailability.NOT_SOLD, station.availabilityOf(FuelType.GASOLINE))
        assertEquals(FuelAvailability.UNKNOWN, station.availabilityOf(FuelType.DIESEL))
    }

    @Test
    fun `should_rememberAnEmptyAreaAsEmpty_when_theAnswerCarriedNoStations`() = runTest {
        cache.put(query, answer(emptyList()))

        assertTrue(assertNotNull(cache.get(query)).stations.isEmpty())
    }
}

private class FakeFuelStationCacheDao : FuelStationCacheDao {
    val stations = mutableMapOf<String, FuelStationCacheEntity>()
    val fuels = mutableListOf<FuelStationFuelEntity>()

    override suspend fun getInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<FuelStationCacheEntity> = stations.values.filter {
        it.latitude in minLat..maxLat && it.longitude in minLon..maxLon
    }

    override suspend fun getFuelsFor(stationKeys: List<String>): List<FuelStationFuelEntity> =
        fuels.filter { it.stationKey in stationKeys }

    override suspend fun upsertStations(rows: List<FuelStationCacheEntity>) {
        rows.forEach { stations[it.stationKey] = it }
    }

    override suspend fun upsertFuels(rows: List<FuelStationFuelEntity>) {
        rows.forEach { row ->
            fuels.removeAll { it.stationKey == row.stationKey && it.fuelType == row.fuelType }
            fuels += row
        }
    }

    override suspend fun deleteFuelsFor(stationKeys: List<String>) {
        fuels.removeAll { it.stationKey in stationKeys }
    }

    override suspend fun deleteStations(stationKeys: List<String>) {
        stationKeys.forEach { stations.remove(it) }
    }

    override suspend fun evictExpiredStations(expiryMs: Long) {
        stations.values.filter { it.cachedAt < expiryMs }.forEach { stations.remove(it.stationKey) }
    }

    override suspend fun getAllStations(): List<FuelStationCacheEntity> = stations.values.toList()

    override suspend fun deleteOrphanedFuels() {
        fuels.removeAll { it.stationKey !in stations.keys }
    }

    override suspend fun deleteAllStations() = stations.clear()

    override suspend fun deleteAllFuels() = fuels.clear()
}

private class FakeCoverageDao : OsmCoverageDao {
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
        discs.values.filter { it.layer == layer }.sortedByDescending { it.fetchedAt }.drop(keep)
            .forEach { discs.remove(it.discKey) }
    }

    override suspend fun deleteAll() = discs.clear()
}
