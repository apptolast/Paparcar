package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.fakes.FakeGeocoderDataSource
import com.rndeveloper.paparcar.fakes.FakeLocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.fakes.FakePlacesDataSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // ── "I asked, there is nothing" ≠ "I could not ask" [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001] ──
    //
    // ⛔ These two used to be the same observation — one emission, `placeInfo == null` — and that is
    // the whole bug: every persisting consumer read the dead mirror as a settled answer and never
    // asked again. They are kept adjacent on purpose, because what matters is that they DIFFER.

    @Test
    fun `should emit a second time carrying poiChecked when no place is found`() = runTest {
        places.placeResult = Result.success(null)

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals(2, emissions.size, "the no-place ANSWER ended the flow in silence")
        assertNull(emissions.last().placeInfo)
        assertTrue(emissions.last().poiChecked, "an answered lookup must say so")
    }

    @Test
    fun `should emit only once and never claim poiChecked when the places call fails`() = runTest {
        places.placeResult = Result.failure(RuntimeException("Network error"))

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions.none { it.poiChecked }, "a failed lookup claimed the POI was checked")
    }

    @Test
    fun `should mark poiChecked when a place is found`() = runTest {
        places.placeResult = Result.success(PlaceInfo("Supersol", PlaceCategory.SUPERMARKET))

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertTrue(emissions.last().poiChecked)
        assertEquals(false, emissions.first().poiChecked, "phase 1 answered a question it never asked")
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
    fun `should cache nothing when the places call fails`() = runTest {
        geocoder.addressResult = Result.success(AddressInfo("Calle Mayor", "Madrid", null, "ES"))
        places.placeResult = Result.failure(RuntimeException("Overpass down"))

        repo.getAddressAndPlace(40.416775, -3.703790).toList()

        // The seal is the ONLY write point — a failed Phase 2 leaves the cell
        // untouched so the next visit retries both phases.
        assertEquals(emptyList(), local.puts)
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

    // ── Who a cached "no place here" is good enough for ───────────────────────
    // [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]

    @Test
    fun `should serve a cached no-place answer to a caller that only paints it`() = runTest {
        local.cached = AddressAndPlace(
            address = AddressInfo("Calle Mayor", "Madrid", null, "ES"),
            placeInfo = null,
            poiChecked = true,
        )

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        // The map's own lanes keep the full cache: re-asking Overpass on every pan settle, over
        // servers measured answering 504, would be paid by everyone to protect nothing.
        assertEquals(1, emissions.size)
        assertEquals(emptyList(), places.asked, "a painting caller went to the network anyway")
    }

    @Test
    fun `should re-measure a cached no-place answer when the caller will store it`() = runTest {
        local.cached = AddressAndPlace(
            address = AddressInfo("Calle Mayor", "Madrid", null, "ES"),
            placeInfo = null,
            poiChecked = true,
        )
        geocoder.addressResult = Result.success(AddressInfo("Calle Mayor", "Madrid", null, "ES"))
        places.placeResult = Result.success(PlaceInfo("Supersol", PlaceCategory.SUPERMARKET))

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790, persisting = true).toList()

        // ⛔ The cell was sealed from ONE point of an 11 × 8 m square — plausibly the user's own
        // position, metres from the pin and seconds earlier. 14 m against a 40 m naming radius is
        // what flips the verdict, so the pin measures for itself.
        assertEquals(listOf(40.416775 to -3.703790), places.asked)
        assertEquals("Supersol", emissions.last().placeInfo?.name)
    }

    @Test
    fun `should serve a cached NAME even to a caller that will store it`() = runTest {
        local.cached = AddressAndPlace(
            address = AddressInfo("Calle Mayor", "Madrid", null, "ES"),
            placeInfo = PlaceInfo("Mercadona", PlaceCategory.SUPERMARKET),
            poiChecked = true,
        )

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790, persisting = true).toList()

        // Being 14 m off about "Mercadona is here" costs nothing; only the NEGATIVE is fragile.
        assertEquals(1, emissions.size)
        assertEquals("Mercadona", emissions.first().placeInfo?.name)
        assertEquals(emptyList(), places.asked, "a cached name must not cost an Overpass round trip")
    }

    // ── Offline near-miss: the cache answers by neighbourhood [GEO-CACHE-ANSWERS-NEARBY-001] ──

    @Test
    fun `should emit nearest cached street as approximate when phase 1 fails`() = runTest {
        geocoder.addressResult = Result.failure(RuntimeException("offline"))
        places.placeResult = Result.failure(RuntimeException("offline"))
        val neighbour = AddressAndPlace(
            address = AddressInfo("Calle Mayor", "Madrid", null, "ES"),
            placeInfo = null,
            approximate = true,
        )
        local.nearestResult = neighbour

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals("Calle Mayor", emissions.first().address.street)
        assertEquals(true, emissions.first().approximate)
        // Borrowed answers never write back — the seal still requires a real Phase-1 answer.
        assertEquals(emptyList(), local.puts)
    }

    @Test
    fun `should borrow the street of a neighbour cell but never its place`() = runTest {
        geocoder.addressResult = Result.failure(RuntimeException("offline"))
        places.placeResult = Result.failure(RuntimeException("offline"))
        local.nearestResult = AddressAndPlace(
            address = AddressInfo("Calle Mayor", "Madrid", null, "ES"),
            placeInfo = PlaceInfo("Mercadona", PlaceCategory.SUPERMARKET),
            approximate = true,
        )

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        // The neighbour may sit up to 250 m away. A street that far is still an honest "you are
        // around here"; a PLACE that far is the claim POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001
        // forbids — only Phase 2, which measures, may name one.
        assertEquals("Calle Mayor", emissions.first().address.street)
        assertNull(emissions.first().placeInfo)
        assertTrue(emissions.all { it.placeInfo == null })
    }

    @Test
    fun `should emit empty non-approximate address when phase 1 fails and no neighbour exists`() = runTest {
        geocoder.addressResult = Result.failure(RuntimeException("offline"))
        places.placeResult = Result.failure(RuntimeException("offline"))
        local.nearestResult = null

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertNull(emissions.first().address.street)
        assertEquals(false, emissions.first().approximate)
    }

    @Test
    fun `should not ask for a neighbour when phase 1 answers`() = runTest {
        geocoder.addressResult = Result.success(AddressInfo("Gran Vía", "Madrid", null, "ES"))
        places.placeResult = Result.success(null)
        local.nearestResult = AddressAndPlace(
            address = AddressInfo("Calle Falsa", "Madrid", null, "ES"),
            placeInfo = null,
            approximate = true,
        )

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals("Gran Vía", emissions.first().address.street)
        assertEquals(false, emissions.first().approximate)
    }

    @Test
    fun `should keep approximate on the second emission when a real POI arrives over a borrowed address`() = runTest {
        geocoder.addressResult = Result.failure(RuntimeException("GMS mute"))
        places.placeResult = Result.success(PlaceInfo("Mercadona", PlaceCategory.SUPERMARKET))
        local.nearestResult = AddressAndPlace(
            address = AddressInfo("Calle Mayor", "Madrid", null, "ES"),
            placeInfo = null,
            approximate = true,
        )

        val emissions = repo.getAddressAndPlace(40.416775, -3.703790).toList()

        assertEquals(true, emissions.last().approximate)
        assertEquals("Mercadona", emissions.last().placeInfo?.name)
        assertEquals(emptyList(), local.puts)
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
