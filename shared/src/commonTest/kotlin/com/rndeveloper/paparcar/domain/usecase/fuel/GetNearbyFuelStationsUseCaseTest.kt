package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.fuel.StationPrice
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
        prices: Map<FuelType, StationPrice> = emptyMap(),
        availability: Map<FuelType, FuelAvailability> = emptyMap(),
    ) = FuelStation(id, id, null, null, lat, lon, prices, availability)

    // The REAL shape Android's geocoder emits for Sol, read from a device: `region` is the
    // autonomous community and the province rides apart. Routing on the region left the whole
    // country unpriced — this fixture is the regression pin.
    // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
    private val madrid = AddressInfo(
        street = null, city = "Madrid", region = "Comunidad de Madrid", country = "España",
        countryCode = "ES", province = "Madrid",
    )

    @Test
    fun `should_routeTheRequest_withTheProvince_notTheAutonomousCommunity`() = runTest {
        val source = FakeSource(FuelStationsResult(emptyList()))
        GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(40.4168, -3.7038, FuelType.DIESEL)

        assertEquals("ES", source.seen?.countryCode)
        assertEquals("Madrid", source.seen?.regionName)
    }

    @Test
    fun `should_fallBackToTheRegion_whenTheGeocoderEmitsNoProvince`() = runTest {
        // Single-province communities: the geocoder may carry no subAdminArea, and there the
        // community name IS the province name, so the region keeps them routable.
        val cantabria = AddressInfo(
            street = null, city = "Santander", region = "Cantabria", country = "España",
            countryCode = "ES", province = null,
        )
        val source = FakeSource(FuelStationsResult(emptyList()))
        GetNearbyFuelStationsUseCase(source, FakeGeocoder(cantabria))
            .invoke(43.4623, -3.8098, FuelType.DIESEL)

        assertEquals("Cantabria", source.seen?.regionName)
    }

    @Test
    fun `should_priceOnlyTheFuelTheCarBurns`() = runTest {
        val source = FakeSource(
            FuelStationsResult(
                listOf(
                    station(
                        "A",
                        prices = mapOf(
                            FuelType.DIESEL to StationPrice(Money(1.879, "EUR")),
                            FuelType.GASOLINE to StationPrice(Money(1.945, "EUR")),
                        ),
                    ),
                ),
                reportedAt = "09/09/2026 08:30:00",
            ),
        )

        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(40.4168, -3.7038, FuelType.DIESEL).getOrThrow()

        assertEquals(1.879, result.stations.single().price?.perUnit?.amount)
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
    fun `should_answerUnpriced_andHideNothing_when_noFuelWasDeclared`() = runTest {
        // The map layer's fuel-less ask [FUEL-STATIONS-MAP-AND-PEEK-001]: with no fuel there is
        // nothing to price and nothing that may be hidden — even a station with a DECLARED negative
        // stays, because the negative is about a fuel nobody asked for.
        val source = FakeSource(
            FuelStationsResult(
                listOf(
                    station("SAYS_NO_LPG", availability = mapOf(FuelType.LPG_CNG to FuelAvailability.NOT_SOLD)),
                    station("PRICED", prices = mapOf(FuelType.DIESEL to StationPrice(Money(1.879, "EUR")))),
                ),
            ),
        )

        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(40.4168, -3.7038, fuel = null).getOrThrow()

        assertEquals(2, result.stations.size)
        assertTrue(result.stations.all { it.price == null })
        assertNull(result.fuel)
    }

    @Test
    fun `should_carryTheLivePricesFlagThrough_when_theSourceGuaranteesRecency`() = runTest {
        // Germany's MTS-K: no stamp anywhere, a legal recency guarantee instead. The flag rides
        // untouched, like `didFallBack` — only the answering source knows it.
        // [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
        val source = FakeSource(
            FuelStationsResult(
                listOf(station("DE1", prices = mapOf(FuelType.DIESEL to StationPrice(Money(2.399, "EUR"))))),
                pricesAreLive = true,
            ),
        )
        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(52.521, 13.438, FuelType.DIESEL).getOrThrow()

        assertTrue(result.pricesAreLive)
        assertNull(result.reportedAt)
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

    // ── A hybrid fills up with petrol ────────────────────────────────────────
    // [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]

    @Test
    fun `should_quoteThePetrolPrice_when_theCarIsAHybrid`() = runTest {
        // The Berlin fixture, measured: Tankerkönig quotes the two products, and nothing else. A
        // car asking with HYBRID used to match no key at all and come back unpriced in silence.
        val source = FakeSource(
            FuelStationsResult(
                listOf(
                    station(
                        "DE1",
                        prices = mapOf(
                            FuelType.GASOLINE to StationPrice(Money(2.309, "EUR")),
                            FuelType.DIESEL to StationPrice(Money(2.399, "EUR")),
                        ),
                    ),
                ),
            ),
        )
        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(52.520, 13.405, FuelType.HYBRID).getOrThrow()

        assertEquals(2.309, result.stations.single().price?.perUnit?.amount)
        // And the answer names the PRODUCT, so whatever labels the figure cannot say «Híbrido».
        assertEquals(FuelType.GASOLINE, result.fuel)
    }

    @Test
    fun `should_quoteThePetrolPrice_when_theCarIsAPlugInHybrid`() = runTest {
        val source = FakeSource(
            FuelStationsResult(
                listOf(station("DE2", prices = mapOf(FuelType.GASOLINE to StationPrice(Money(2.259, "EUR"))))),
            ),
        )
        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(52.520, 13.405, FuelType.PLUG_IN_HYBRID).getOrThrow()

        assertEquals(2.259, result.stations.single().price?.perUnit?.amount)
    }

    @Test
    fun `should_hideTheForecourt_when_aHybridsPetrolIsDeclaredNotSold`() = runTest {
        // The other half of the same key. A station that STATES it sells no petrol is no use to a
        // hybrid — but `isKnownNotToSell(HYBRID)` could never be true, so it stayed on the list.
        val source = FakeSource(
            FuelStationsResult(
                listOf(
                    station("NO_PETROL", availability = mapOf(FuelType.GASOLINE to FuelAvailability.NOT_SOLD)),
                    station("PETROL", prices = mapOf(FuelType.GASOLINE to StationPrice(Money(1.859, "EUR")))),
                ),
            ),
        )
        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(40.4168, -3.7038, FuelType.HYBRID).getOrThrow()

        assertEquals(listOf("PETROL"), result.stations.map { it.station.id })
    }

    @Test
    fun `should_priceNothing_when_theCarIsPurelyElectric`() = runTest {
        // ⛔ The guard on the other side: mapping an EV onto petrol to make the lookup succeed would
        // put a litre price beside a car with no tank.
        val source = FakeSource(
            FuelStationsResult(
                listOf(station("DE3", prices = mapOf(FuelType.GASOLINE to StationPrice(Money(2.309, "EUR"))))),
            ),
        )
        val result = GetNearbyFuelStationsUseCase(source, FakeGeocoder(madrid))
            .invoke(52.520, 13.405, FuelType.ELECTRIC).getOrThrow()

        assertNull(result.stations.single().price)
        // Still listed: where to stop is a fair question even when nothing can be priced.
        assertEquals(1, result.stations.size)
    }
}
