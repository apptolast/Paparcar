package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.fakes.FakeGeocoderDataSource
import io.apptolast.paparcar.fakes.FakeLocalAddressAndPlaceDataSource
import io.apptolast.paparcar.fakes.FakePlacesDataSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddressAndPlaceRepositoryImplTest {

    private val geocoder = FakeGeocoderDataSource()
    private val places = FakePlacesDataSource()
    private val repo = AddressAndPlaceRepositoryImpl(FakeLocalAddressAndPlaceDataSource(), geocoder, places)

    @Test
    fun `should emit address-only in first emission`() = runTest {
        val address = AddressInfo(street = "Calle Mayor", city = "Madrid", region = null, country = "ES")
        geocoder.addressResult = Result.success(address)

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals(address, emissions.first().address)
        assertNull(emissions.first().placeInfo)
    }

    @Test
    fun `should use empty address when geocoder fails`() = runTest {
        geocoder.addressResult = Result.failure(RuntimeException("Geocoder error"))

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertNull(emissions.first().address.street)
        assertNull(emissions.first().address.city)
    }

    @Test
    fun `should emit second time with placeInfo when place found`() = runTest {
        val place = PlaceInfo(name = "El Corte Inglés", category = PlaceCategory.MALL)
        places.placeResult = Result.success(place)

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals(2, emissions.size)
        assertEquals(place, emissions.last().placeInfo)
    }

    @Test
    fun `should emit only once when no place found`() = runTest {
        places.placeResult = Result.success(null)

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals(1, emissions.size)
        assertNull(emissions.first().placeInfo)
    }

    @Test
    fun `should emit only once when places call fails`() = runTest {
        places.placeResult = Result.failure(RuntimeException("Network error"))

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals(1, emissions.size)
    }

    @Test
    fun `should preserve address in second emission`() = runTest {
        val address = AddressInfo(street = "Gran Vía", city = "Madrid", region = null, country = "ES")
        geocoder.addressResult = Result.success(address)
        places.placeResult = Result.success(PlaceInfo("Cines Callao", PlaceCategory.OTHER))

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals(address, emissions.last().address)
    }
}
