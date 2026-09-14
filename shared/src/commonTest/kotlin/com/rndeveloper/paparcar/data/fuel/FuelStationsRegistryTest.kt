package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which source answers, and — the part with teeth — when one hands over. [FUEL-STATIONS-LIST-001] */
class FuelStationsRegistryTest {

    private class FakeSource(
        private val country: String?,
        private val answer: Result<FuelStationsResult>,
    ) : FuelStationsSource {
        var calls = 0
        override fun covers(countryCode: String) =
            country != null && country.equals(countryCode, ignoreCase = true)
        override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
            calls++
            return answer
        }
    }

    private fun station(id: String, priced: Boolean = false) = FuelStation(
        id = id, name = id, address = null, town = null, latitude = 0.0, longitude = 0.0,
        prices = if (priced) mapOf(FuelType.GASOLINE to StationPrice(Money(1.9, "EUR"))) else emptyMap(),
    )

    private fun spain(withPrices: List<FuelStation>) =
        FakeSource("ES", Result.success(FuelStationsResult(withPrices, reportedAt = "hoy")))

    private fun osm(vararg ids: String) =
        FakeSource(null, Result.success(FuelStationsResult(ids.map { station(it) })))

    private fun query(country: String) =
        FuelStationsQuery(40.4168, -3.7038, countryCode = country, regionName = "Madrid")

    @Test
    fun `should_preferTheNationalFeed_whereThereIsOne`() = runTest {
        val es = spain(listOf(station("ES1", priced = true)))
        val fallback = osm("OSM1")
        val result = FuelStationsRegistry(listOf(es), fallback).stationsNear(query("ES")).getOrThrow()

        assertEquals(listOf("ES1"), result.stations.map { it.id })
        assertEquals("hoy", result.reportedAt)
        assertEquals(0, fallback.calls, "OSM must not be asked when a national feed answered")
    }

    @Test
    fun `should_useTheFallback_whereNoCountryFeedExists`() = runTest {
        val es = spain(listOf(station("ES1")))
        val result = FuelStationsRegistry(listOf(es), osm("OSM1"))
            .stationsNear(query("JP")).getOrThrow()

        assertEquals(listOf("OSM1"), result.stations.map { it.id })
        assertEquals(0, es.calls)
    }

    @Test
    fun `should_handOverToTheFallback_whenTheNationalFeedFails`() = runTest {
        // Down, rate-limited, or unable to place the user. "Here is where you can refuel, we just
        // cannot price it" beats an empty screen.
        val es = FakeSource("ES", Result.failure(IllegalStateException("503")))
        val result = FuelStationsRegistry(listOf(es), osm("OSM1"))
            .stationsNear(query("ES")).getOrThrow()

        assertEquals(listOf("OSM1"), result.stations.map { it.id })
    }

    @Test
    fun `should_keepAnEmptyNationalAnswer_insteadOfRetryingAgainstOsm`() = runTest {
        // ⛔ The subtle one. An empty list from a national feed is a REAL answer — nothing within the
        // radius. Retrying it against OSM would quietly turn a priced country into an unpriced one,
        // and the user would see the prices vanish with no explanation.
        val es = spain(emptyList())
        val fallback = osm("OSM1")
        val result = FuelStationsRegistry(listOf(es), fallback).stationsNear(query("ES")).getOrThrow()

        assertTrue(result.stations.isEmpty())
        assertEquals(0, fallback.calls, "an empty national answer is an answer, not a failure")
    }

    // ── An unknown country is not a country without prices ───────────────────────────────────────
    // [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]
    //
    // ⛔ These two run the SAME line of code and the difference between them IS the ticket: one is a
    // verdict we are entitled to, the other is a guess about a whole country. They sit adjacent so
    // that collapsing them back into one takes one of the two down with it.

    @Test
    fun `should_markTheFallbackAsASubstitute_whenTheCountryCouldNotBeNamed`() = runTest {
        // A mute reverse geocode leaves the country empty. This may well be Spain, with the
        // Ministry serving prices for that very street — so the lane must say "the official prices
        // did not load", never "this country publishes none".
        val es = spain(listOf(station("ES1", priced = true)))
        val result = FuelStationsRegistry(listOf(es), osm("OSM1"))
            .stationsNear(query("")).getOrThrow()

        assertEquals(listOf("OSM1"), result.stations.map { it.id })
        assertTrue(result.didFallBack, "an unnamed country gets a SUBSTITUTE, not an answer")
        assertEquals(0, es.calls, "nothing routes to a national feed we did not choose")
    }

    @Test
    fun `should_notMarkTheFallback_whereTheCountryIsKnownAndHasNoFeed`() = runTest {
        // Japan is named and genuinely uncovered: the fallback IS the answer, and the lane may say
        // that these prices are not published. Marking this one would cry wolf everywhere on earth.
        val es = spain(listOf(station("ES1")))
        val result = FuelStationsRegistry(listOf(es), osm("OSM1"))
            .stationsNear(query("JP")).getOrThrow()

        assertFalse(result.didFallBack, "a covered-by-nobody country is an answer, not a substitute")
    }

    @Test
    fun `should_failLoudly_whenBothTheFeedAndTheFallbackAreDown`() = runTest {
        val es = FakeSource("ES", Result.failure(IllegalStateException("503")))
        val down = FakeSource(null, Result.failure(IllegalStateException("no network")))

        assertTrue(FuelStationsRegistry(listOf(es), down).stationsNear(query("ES")).isFailure)
    }
}
