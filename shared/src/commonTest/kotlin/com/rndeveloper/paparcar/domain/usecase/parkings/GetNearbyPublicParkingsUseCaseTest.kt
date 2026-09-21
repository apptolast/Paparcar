package com.rndeveloper.paparcar.domain.usecase.parkings

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.parkings.ParkingZoneGrid
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetNearbyPublicParkingsUseCaseTest {

    private class FakeSource(
        private val result: Result<List<PublicParking>>,
        private val rememberedAtMs: Long? = null,
        /** What the disk would already hold, and when it was last asked for. */
        private val held: Pair<List<PublicParking>, Long>? = null,
    ) : PublicParkingsSource {
        var seen: PublicParkingsQuery? = null
        var rememberedFor: PublicParkingsQuery? = null
        var asked = 0
        override fun covers(countryCode: String) = true
        override suspend fun parkingsNear(query: PublicParkingsQuery): Result<PublicParkingsResult> {
            seen = query
            asked++
            return result.map { PublicParkingsResult(it, rememberedAtMs) }
        }

        override suspend fun remembered(query: PublicParkingsQuery): PublicParkingsResult? {
            rememberedFor = query
            return held?.let { (parkings, at) -> PublicParkingsResult(parkings, at) }
        }
    }

    private fun parking(
        id: String,
        lat: Double = SOL_LAT,
        lon: Double = SOL_LON,
        name: String? = null,
        operator: String? = null,
        kind: PublicParkingKind = PublicParkingKind.SURFACE,
        capacity: Int? = null,
        fee: FeeInfo = FeeInfo.UNKNOWN,
    ) = PublicParking(id, name, operator, kind, lat, lon, capacity, fee)

    @Test
    fun `should_dropAnAnonymousPatchOfGround_becauseNobodyCanBeSentThere`() = runTest {
        // ⛔ MEASURED: after excluding the kerb and private lots, Cuenca still answered 76 elements
        // of which 2 had a name and none was off-street. A map of 74 anonymous polygons is noise.
        val source = FakeSource(
            Result.success(
                listOf(
                    parking("anonymous"),
                    parking("named", name = "Parking Estación"),
                    parking("run-by", operator = "Ayuntamiento"),
                    parking("underground", kind = PublicParkingKind.UNDERGROUND),
                    parking("priced", fee = FeeInfo.FREE),
                    parking("counted", capacity = 40),
                ),
            ),
        )

        val result = requireNotNull(
            GetNearbyPublicParkingsUseCase(source).invoke(SOL_LAT, SOL_LON).getOrNull(),
        )

        assertEquals(
            listOf("named", "run-by", "underground", "priced", "counted").sorted(),
            result.parkings.map { it.parking.id }.sorted(),
        )
    }

    @Test
    fun `should_orderByDistance_becauseTheNearestOneIsWhatADriverActsOn`() = runTest {
        val source = FakeSource(
            Result.success(
                listOf(
                    parking("far", lat = SOL_LAT + 0.01, name = "Lejos"),
                    parking("near", lat = SOL_LAT + 0.001, name = "Cerca"),
                ),
            ),
        )

        val result = requireNotNull(
            GetNearbyPublicParkingsUseCase(source).invoke(SOL_LAT, SOL_LON).getOrNull(),
        )

        assertEquals(listOf("near", "far"), result.parkings.map { it.parking.id })
        assertTrue(result.parkings.first().distanceMeters < result.parkings.last().distanceMeters)
    }

    /**
     * ⛔ The ask carries NO country, and that is the fix rather than an omission.
     * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
     *
     * There is no registry over this port — OSM answers for everywhere — so the geocode in front of
     * every ask was latency nobody read. Worse, the country lands in the memory cache key, so a
     * geocoder that woke up between two asks made the SAME zone miss the cache and be fetched from
     * Overpass twice. A constant key is what makes panning inside a zone free.
     */
    @Test
    fun `should_askWithNoCountry_soTheSameZoneAlwaysProducesTheSameKey`() = runTest {
        val source = FakeSource(Result.success(listOf(parking("p", name = "Parking"))))
        val useCase = GetNearbyPublicParkingsUseCase(source)

        useCase.invoke(SOL_LAT, SOL_LON)
        val first = source.seen
        // A few hundred metres away — same zone, so it must be a byte-identical question.
        useCase.invoke(SOL_LAT + 0.002, SOL_LON + 0.002)

        assertEquals("", first?.countryCode)
        assertNull(first?.regionName)
        assertEquals(first, source.seen)
        assertEquals(2, source.asked)
    }

    @Test
    fun `should_askWithinTheDestinationRadius_notTheRefuelRadius`() = runTest {
        // You drive to a forecourt that is on the way; you park at your destination — so this stays
        // well under the 5 km the fuel lane uses, even after widening.
        // [PARKINGS-A-CAPPED-ANSWER-MUST-NOT-DROP-THE-NEAREST-001]
        val source = FakeSource(Result.success(emptyList()))

        GetNearbyPublicParkingsUseCase(source).invoke(SOL_LAT, SOL_LON)

        // ⛔ The request now goes out for the ZONE, so the radius on the wire is the zone's reach,
        // not the display radius. What must NOT move is what the driver is shown — see the test
        // below. [PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001]
        assertEquals(ParkingZoneGrid.ZONE_RADIUS_METERS, source.seen?.radiusMeters)
        assertTrue(
            PublicParkingsQuery.DEFAULT_RADIUS_METERS < FuelStationsQuery.DEFAULT_RADIUS_METERS,
            "a car park at the refuel radius is not an option, it is a different trip",
        )
    }

    @Test
    fun `should_propagateAFailure_ratherThanSayingNoneNearby`() = runTest {
        // "None near you" and "we could not ask" are different sentences.
        val source = FakeSource(Result.failure(IllegalStateException("Overpass answered 504")))

        val result = GetNearbyPublicParkingsUseCase(source).invoke(SOL_LAT, SOL_LON)

        assertTrue(result.isFailure)
    }

    /**
     * ⛔ The age survives the filtering. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * This use case drops the anonymous patches of ground, and it would be easy to rebuild the
     * answer around the surviving list and lose the stamp on the way — the exact defect
     * [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001] found twice: a field that travels with no
     * reader. Which car parks are worth showing and when the app last managed to ask are unrelated
     * questions, and answering the first must not silently answer the second.
     */
    @Test
    fun `should_carryTheRememberedStamp_when_theAnswerCameOffTheDisk`() = runTest {
        val source = FakeSource(
            result = Result.success(listOf(parking("anonymous"), parking("named", name = "Parking"))),
            rememberedAtMs = 1_725_000_000_000,
        )

        val result = requireNotNull(
            GetNearbyPublicParkingsUseCase(source).invoke(SOL_LAT, SOL_LON).getOrNull(),
        )

        assertEquals(listOf("named"), result.parkings.map { it.parking.id })
        assertEquals(1_725_000_000_000, result.rememberedAtMs)
    }

    /** And a live answer says so by saying nothing — null is not "unknown", it is "just fetched". */
    @Test
    fun `should_leaveTheStampNull_when_theAnswerCameOffTheWire`() = runTest {
        val source = FakeSource(Result.success(listOf(parking("named", name = "Parking"))))

        val result = requireNotNull(
            GetNearbyPublicParkingsUseCase(source).invoke(SOL_LAT, SOL_LON).getOrNull(),
        )

        assertNull(result.rememberedAtMs)
    }

    // ── What the app can already say, before the wire ────────────────────────
    // [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]

    /**
     * ⛔ The remembered list is filtered, measured and cut EXACTLY like the live one. Two copies of
     * "which car parks are worth showing" is how the list that flashes first ends up disagreeing
     * with the one that replaces it — which reads as the map losing car parks.
     */
    @Test
    fun `should_filterAndMeasureTheRememberedList_exactlyLikeTheLiveOne`() = runTest {
        val source = FakeSource(
            result = Result.success(emptyList()),
            held = listOf(
                parking("anonymous"),
                parking("far", lat = SOL_LAT + 0.01, name = "Lejos"),
                parking("near", lat = SOL_LAT + 0.001, name = "Cerca"),
            ) to 1_725_000_000_000,
        )

        val known = requireNotNull(GetNearbyPublicParkingsUseCase(source).remembered(SOL_LAT, SOL_LON))

        assertEquals(listOf("near", "far"), known.parkings.map { it.parking.id })
        assertEquals(1_725_000_000_000, known.rememberedAtMs)
        assertTrue(known.parkings.first().distanceMeters < known.parkings.last().distanceMeters)
    }

    /** ⛔ It never asks anybody: the whole point is that it costs nothing while the live ask flies. */
    @Test
    fun `should_askNobody_when_sayingWhatItAlreadyKnows`() = runTest {
        val source = FakeSource(
            result = Result.success(listOf(parking("p", name = "Parking"))),
            held = listOf(parking("p", name = "Parking")) to 1_725_000_000_000,
        )

        GetNearbyPublicParkingsUseCase(source).remembered(SOL_LAT, SOL_LON)

        assertEquals(0, source.asked)
        // And it looks up the SAME zone the live ask will use, or it would be answering about
        // somewhere else.
        assertEquals(ParkingZoneGrid.ZONE_RADIUS_METERS, source.rememberedFor?.radiusMeters)
    }

    /** Never been here: null, not an empty list. "Nobody asked" is not "there are none". */
    @Test
    fun `should_sayNothing_when_nobodyEverAskedAboutThisArea`() = runTest {
        val source = FakeSource(Result.success(listOf(parking("p", name = "Parking"))))

        assertNull(GetNearbyPublicParkingsUseCase(source).remembered(SOL_LAT, SOL_LON))
    }

    private companion object {
        const val SOL_LAT = 40.4168
        const val SOL_LON = -3.7038
    }
}
