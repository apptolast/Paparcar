package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.fakes.FakeGeocoderDataSource
import io.apptolast.paparcar.fakes.FakePlacesDataSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetLocationInfoUseCaseTest {

    private val geocoder = FakeGeocoderDataSource()
    private val places = FakePlacesDataSource()
    private val useCase = GetLocationInfoUseCase(geocoder, places)

    // ── First emission — address ───────────────────────────────────────────────

    @Test
    fun `should_emitAddressInFirstEmission`() = runTest {
        val address = AddressInfo(street = "Calle Mayor", city = "Madrid", region = null, country = "ES")
        geocoder.addressResult = Result.success(address)

        val emissions = useCase(40.416775, -3.703790).toList()

        assertEquals(address, emissions.first().address)
        assertNull(emissions.first().placeInfo)
    }

    @Test
    fun `should_useEmptyAddress_when_geocoderFails`() = runTest {
        geocoder.addressResult = Result.failure(RuntimeException("Geocoder error"))

        val emissions = useCase(40.416775, -3.703790).toList()

        val firstEmission = emissions.first()
        assertNull(firstEmission.address.street)
        assertNull(firstEmission.address.city)
    }

    // ── Second emission — place ───────────────────────────────────────────────

    @Test
    fun `should_emitSecondTime_with_placeInfo_when_placeFound`() = runTest {
        val place = PlaceInfo(name = "El Corte Inglés", category = PlaceCategory.MALL)
        places.placeResult = Result.success(place)

        val emissions = useCase(40.416775, -3.703790).toList()

        assertEquals(2, emissions.size)
        assertEquals(place, emissions.last().placeInfo)
    }

    @Test
    fun `should_emitOnlyOnce_when_noPlaceFound`() = runTest {
        places.placeResult = Result.success(null)

        val emissions = useCase(40.416775, -3.703790).toList()

        assertEquals(1, emissions.size)
        assertNull(emissions.first().placeInfo)
    }

    @Test
    fun `should_emitOnlyOnce_when_placesFails`() = runTest {
        places.placeResult = Result.failure(RuntimeException("Network error"))

        val emissions = useCase(40.416775, -3.703790).toList()

        assertEquals(1, emissions.size)
    }

    @Test
    fun `should_preserveAddress_in_secondEmission`() = runTest {
        val address = AddressInfo(street = "Gran Vía", city = "Madrid", region = null, country = "ES")
        geocoder.addressResult = Result.success(address)
        places.placeResult = Result.success(PlaceInfo("Cines Callao", PlaceCategory.OTHER))

        val emissions = useCase(40.416775, -3.703790).toList()

        assertEquals(address, emissions.last().address)
    }
}
