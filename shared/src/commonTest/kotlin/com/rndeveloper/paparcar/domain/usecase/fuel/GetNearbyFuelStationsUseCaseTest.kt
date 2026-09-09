package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.SearchResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetNearbyFuelStationsUseCaseTest {

    private class FakeGeocoder(private val address: AddressInfo?) : GeocoderDataSource {
        override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> =
            address?.let { Result.success(it) } ?: Result.failure(IllegalStateException("offline"))
        override suspend fun searchByName(query: String, maxResults: Int): Result<List<SearchResult>> =
            Result.success(emptyList())
    }

    private class FakeSource(private val result: FuelStationsResult) : FuelStationsSource {
        var seen: FuelStationsQuery? = null
        override fun covers(countryCode: String) = true
        override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
            seen = query
            return Result.success(result)
        }
    }

    private fun station(
        id: String,
        lat: Double = 40.4168,
        lon: Double = -3.7038,
        prices: Map<FuelType, Money> = emptyMap(),
        availability: Map<FuelType, FuelAvailability> = emptyMap(),
    ) = FuelStation(id, id, null, null, lat, lon, prices, availability)

    private val madrid = AddressInfo(
        street = null, city = "Madrid", region = "Madrid", country = "España", countryCode = "ES",
    )

    @Test
    fun `should_routeTheRequest_withTheCountryAndRegionTheGeocoderGave`() = runTest {
        val source = FakeSource(FuelStationsResult(emptyList()))
        GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(40.4168, -3.7038, FuelType.DIESEL)

        assertEquals("ES", source.seen?.countryCode)
        assertEquals("Madrid", source.seen?.regionName)
    }

    @Test
    fun `should_priceOnlyTheFuelTheCarBurns`() = runTest {
        val source = FakeSource(
            FuelStationsResult(
                listOf(
                    station(
                        "A",
                        prices = mapOf(
                            FuelType.DIESEL to Money(1.879, "EUR"),
                            FuelType.GASOLINE to Money(1.945, "EUR"),
                        ),
                    ),
                ),
                reportedAt = "09/09/2026 08:30:00",
            ),
        )

        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(40.4168, -3.7038, FuelType.DIESEL).getOrThrow()

        assertEquals(1.879, result.stations.single().price?.amount)
        assertEquals("09/09/2026 08:30:00", result.reportedAt)
        assertTrue(result.hasPrices)
    }

    @Test
    fun `should_hideAStation_onlyWhenTheSourceStatesItLacksThePump`() = runTest {
        // ⛔ The asymmetry that the whole availability model exists for: Spain SAYS a station has no
        // LPG (empty string), OSM merely never mentions it. Only the first may be hidden.
        val source = FakeSource(
            FuelStationsResult(
                listOf(
                    station("SAYS_NO", availability = mapOf(FuelType.LPG_CNG to FuelAvailability.NOT_SOLD)),
                    station("SAYS_NOTHING"),
                    station("SAYS_YES", availability = mapOf(FuelType.LPG_CNG to FuelAvailability.SOLD)),
                ),
            ),
        )

        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(40.4168, -3.7038, FuelType.LPG_CNG).getOrThrow()

        assertEquals(listOf("SAYS_NOTHING", "SAYS_YES"), result.stations.map { it.station.id })
    }

    @Test
    fun `should_stampTheDistanceOfEachStation`() = runTest {
        // ~1,1 km due north: only latitude moves, so the number is checkable by hand.
        val source = FakeSource(FuelStationsResult(listOf(station("A", lat = 40.4268))))
        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(40.4168, -3.7038, FuelType.DIESEL).getOrThrow()

        assertEquals(1_112.0, result.stations.single().distanceMeters, absoluteTolerance = 20.0)
    }

    @Test
    fun `should_stillAnswer_whenTheGeocoderCannotPlaceTheUser`() = runTest {
        // A failed geocode is not a failed request: OSM covers the world and needs no region, so the
        // user still gets somewhere to refuel instead of an error.
        val source = FakeSource(FuelStationsResult(listOf(station("OSM1"))))
        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(null))
            .invoke(40.4168, -3.7038, FuelType.DIESEL).getOrThrow()

        assertEquals("", source.seen?.countryCode)
        assertNull(source.seen?.regionName)
        assertEquals(1, result.stations.size)
    }

    @Test
    fun `should_reportNoPrices_whenTheSourceCarriesNone`() = runTest {
        // The OSM case: real stations, no prices, no timestamp. The UI needs to know so it says
        // "here is where" instead of pretending the pumps are free.
        val source = FakeSource(FuelStationsResult(listOf(station("OSM1"))))
        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(40.4168, -3.7038, FuelType.DIESEL).getOrThrow()

        assertTrue(!result.hasPrices)
        assertNull(result.reportedAt)
        assertNull(result.stations.single().price)
    }
}
