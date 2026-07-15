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
    private val local = FakeLocalAddressAndPlaceDataSource()
    private val repo = AddressAndPlaceRepositoryImpl(local, geocoder, places)

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

    // ── Deadline + cache purity [GEOCODE-DEADLINE-001] ────────────────────────

    @Test
    fun `should fall back and cache nothing when phase 1 hangs`() = runTest {
        geocoder.addressDelayMs = 60_000 // GmsCore listener that never calls back
        places.placeResult = Result.success(null)

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertNull(emissions.first().address.street)
        assertEquals(emptyList(), local.puts)
    }

    @Test
    fun `should cache nothing when phase 1 fails`() = runTest {
        geocoder.addressResult = Result.failure(RuntimeException("Geocoder error"))
        places.placeResult = Result.success(null)

        repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals(emptyList(), local.puts)
    }

    @Test
    fun `should not seal the cell when the places call fails`() = runTest {
        geocoder.addressResult = Result.success(AddressInfo("Calle Mayor", "Madrid", null, "ES"))
        places.placeResult = Result.failure(RuntimeException("Overpass down"))

        repo.getAddressAndPlace(40.416775, -3.703790).toList()

        // Address is cached unsealed (Phase 2 must retry next visit); nothing sealed.
        assertEquals(1, local.puts.size)
        assertEquals(false, local.puts.single().second)
    }

    @Test
    fun `should seal the cell when places answers no-POI`() = runTest {
        geocoder.addressResult = Result.success(AddressInfo("Calle Mayor", "Madrid", null, "ES"))
        places.placeResult = Result.success(null)

        repo.getAddressAndPlace(40.416775, -3.703790).toList()

        // success(null) is a real answer — the cell seals with placeInfo=null.
        val sealed = local.puts.last()
        assertEquals(true, sealed.second)
        assertNull(sealed.first.placeInfo)
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
