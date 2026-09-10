package com.rndeveloper.paparcar.domain.usecase.parkings

import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetNearbyPublicParkingsUseCaseTest {

    private class FakeGeocoder(private val address: AddressInfo?) : GeocoderDataSource {
        override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> =
            address?.let { Result.success(it) } ?: Result.failure(IllegalStateException("offline"))
        override suspend fun searchByName(query: String, maxResults: Int): Result<List<SearchResult>> =
            Result.success(emptyList())
    }

    private class FakeSource(
        private val result: Result<List<PublicParking>>,
    ) : PublicParkingsSource {
        var seen: PublicParkingsQuery? = null
        var asked = 0
        override fun covers(countryCode: String) = true
        override suspend fun parkingsNear(query: PublicParkingsQuery): Result<List<PublicParking>> {
            seen = query
            asked++
            return result
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

    private val madrid = AddressInfo(
        street = null, city = "Madrid", region = "Comunidad de Madrid", country = "España",
        countryCode = "ES", province = "Madrid",
    )

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
            GetNearbyPublicParkingsUseCase(source, FakeGeocoder(madrid))
                .invoke(SOL_LAT, SOL_LON).getOrNull(),
        )

        assertEquals(
            listOf("named", "run-by", "underground", "priced", "counted").sorted(),
            result.map { it.parking.id }.sorted(),
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
            GetNearbyPublicParkingsUseCase(source, FakeGeocoder(madrid))
                .invoke(SOL_LAT, SOL_LON).getOrNull(),
        )

        assertEquals(listOf("near", "far"), result.map { it.parking.id })
        assertTrue(result.first().distanceMeters < result.last().distanceMeters)
    }

    @Test
    fun `should_stillAnswer_whenTheGeocoderFails_becauseOsmNeedsNoCountry`() = runTest {
        val source = FakeSource(Result.success(listOf(parking("p", name = "Parking"))))

        val result = GetNearbyPublicParkingsUseCase(source, FakeGeocoder(null))
            .invoke(SOL_LAT, SOL_LON)

        assertEquals(1, result.getOrNull()?.size)
        assertEquals("", source.seen?.countryCode)
        assertEquals(1, source.asked)
    }

    @Test
    fun `should_askWithinTheDestinationRadius_notTheRefuelRadius`() = runTest {
        // You drive to a forecourt that is on the way; you park at your destination. 5 km of car
        // parks is not a list, and 300 elements came back within 1 km of central Málaga.
        val source = FakeSource(Result.success(emptyList()))

        GetNearbyPublicParkingsUseCase(source, FakeGeocoder(madrid)).invoke(SOL_LAT, SOL_LON)

        assertEquals(1_500, source.seen?.radiusMeters)
    }

    @Test
    fun `should_propagateAFailure_ratherThanSayingNoneNearby`() = runTest {
        // "None near you" and "we could not ask" are different sentences.
        val source = FakeSource(Result.failure(IllegalStateException("Overpass answered 504")))

        val result = GetNearbyPublicParkingsUseCase(source, FakeGeocoder(madrid))
            .invoke(SOL_LAT, SOL_LON)

        assertTrue(result.isFailure)
    }

    private companion object {
        const val SOL_LAT = 40.4168
        const val SOL_LON = -3.7038
    }
}
