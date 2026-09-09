package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.model.FuelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fixture shaped on the live Overpass answer for Madrid (09-09-2026): 25 stations, 20 nodes and
 * 5 ways, every way carrying `center`. [FUEL-STATIONS-LIST-001]
 */
class OsmFuelStationParserTest {

    private val fixture = """
        {"elements":[
          {"type":"node","id":1,"lat":40.4168,"lon":-3.7038,
           "tags":{"amenity":"fuel","name":"Repsol","brand":"Repsol","operator":"Repsol",
                   "fuel:diesel":"yes","fuel:octane_95":"yes","fuel:octane_98":"yes",
                   "opening_hours":"24/7","addr:street":"Calle Mayor","addr:housenumber":"12",
                   "addr:city":"Madrid"}},
          {"type":"way","id":2,"center":{"lat":40.4200,"lon":-3.7100},
           "tags":{"amenity":"fuel","name":"Cepsa","brand":"Cepsa","fuel:lpg":"yes"}},
          {"type":"node","id":3,"lat":40.4100,"lon":-3.7000,
           "tags":{"amenity":"fuel","name":"Sin marca","compressed_air":"yes"}}
        ]}
    """.trimIndent()

    @Test
    fun `should_readAWaysCenter_notOnlyNodes`() {
        // A fifth of the forecourts measured are ways; dropping them would lose 20% of the map.
        val stations = OsmFuelStationParser.parse(fixture)
        assertEquals(3, stations.size)
        val cepsa = stations.single { it.id == "osm/way/2" }
        assertEquals(40.42, cepsa.latitude, absoluteTolerance = 1e-6)
        assertEquals(-3.71, cepsa.longitude, absoluteTolerance = 1e-6)
    }

    @Test
    fun `should_nameByBrandFirst_becauseThatIsWhatADriverScansFor`() {
        val stations = OsmFuelStationParser.parse(fixture)
        assertEquals("Repsol", stations.first().name)
        assertEquals("Calle Mayor 12", stations.first().address)
        assertEquals("Madrid", stations.first().town)
        assertEquals("24/7", stations.first().schedule)
    }

    @Test
    fun `should_neverCarryAPrice_becauseOsmHasNone`() {
        // 567.615 stations worldwide and zero prices. Saying it in the type is this source's job.
        assertTrue(OsmFuelStationParser.parse(fixture).all { it.prices.isEmpty() })
    }

    @Test
    fun `should_readAFuelTagAsSold`() {
        val stations = OsmFuelStationParser.parse(fixture)
        val repsol = stations.first()
        assertEquals(FuelAvailability.SOLD, repsol.availabilityOf(FuelType.DIESEL))
        assertEquals(FuelAvailability.SOLD, repsol.availabilityOf(FuelType.GASOLINE))
        assertEquals(FuelAvailability.SOLD, stations[1].availabilityOf(FuelType.LPG_CNG))
    }

    @Test
    fun `should_treatAMissingFuelTag_asUnknown_neverAsNotSold`() {
        // ⛔ The measurement that forces this: only ~44% of stations carry fuel:* tags (11 of 25 in
        // Madrid). Reading silence as "no diesel" would hide most stations in the world from most
        // drivers in the world — and unlike Spain's feed, OSM never states a negative.
        val unbranded = OsmFuelStationParser.parse(fixture).single { it.id == "osm/node/3" }

        assertEquals(FuelAvailability.UNKNOWN, unbranded.availabilityOf(FuelType.DIESEL))
        assertTrue(!unbranded.isKnownNotToSell(FuelType.DIESEL), "OSM cannot say a station lacks a pump")

        // Even the well-tagged Repsol says nothing about LPG, and that is not a denial either.
        val repsol = OsmFuelStationParser.parse(fixture).first()
        assertEquals(FuelAvailability.UNKNOWN, repsol.availabilityOf(FuelType.LPG_CNG))
    }

    @Test
    fun `should_buildAnAroundQueryForFuelOnly`() {
        val query = OsmFuelStationParser.buildQuery(40.4168, -3.7038, radiusMeters = 3_000, timeoutSeconds = 20)

        assertTrue(query.contains("[amenity=fuel]"), query)
        assertTrue(query.contains("around:3000,40.4168,-3.7038"), query)
        // `out center`, not `out bb`: a forecourt is a destination, so its middle is the useful point.
        assertTrue(query.contains("out center"), query)
    }
}
