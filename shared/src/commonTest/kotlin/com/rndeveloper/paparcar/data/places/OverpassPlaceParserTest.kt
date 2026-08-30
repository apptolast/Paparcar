package com.rndeveloper.paparcar.data.places

import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.places.NearbyPlacePolicy
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Overpass wire format, which had no test on either platform before this ticket. */
class OverpassPlaceParserTest {

    @Test
    fun `should ask for bounding boxes so places can be measured by their edge`() {
        val query = OverpassPlaceParser.buildQuery(40.416775, -3.703790, timeoutSeconds = 8)

        assertTrue(query.contains("out bb ${NearbyPlacePolicy.MAX_RESULTS};"), query)
        // `out center` is what made a supermarket's own building count as distance.
        assertTrue(!query.contains("out center"), "the centroid output must not come back: $query")
        assertTrue(query.contains("around:${NearbyPlacePolicy.QUERY_RADIUS_METERS},40.416775,-3.70379"), query)
        assertTrue(query.contains("[out:json][timeout:8];"), query)
    }

    @Test
    fun `should read a node position and a way bounding box from one response`() {
        val candidates = OverpassPlaceParser.parseCandidates(RESPONSE)

        assertEquals(2, candidates.size)
        val cafe = candidates.first { it.name == "Café Central" }
        assertEquals(PlaceCategory.CAFE, cafe.category)
        assertNull(cafe.bounds, "a node has no footprint")
        assertEquals(40.4168, cafe.lat)

        val market = candidates.first { it.name == "Mercadona" }
        assertEquals(PlaceCategory.SUPERMARKET, market.category)
        val bounds = assertNotNull(market.bounds)
        assertEquals(40.4170, bounds.minLat)
        assertEquals(40.4176, bounds.maxLat)
        assertEquals(-3.7042, bounds.minLon)
        assertEquals(-3.7038, bounds.maxLon)
        // With no lat/lon of its own, a bounded element falls back to its box centre.
        assertTrue(abs(market.lat - 40.4173) < 1e-9, "expected the box centre, got ${market.lat}")
    }

    @Test
    fun `should skip elements without a name or without any position`() {
        val body = """
            {"elements":[
              {"type":"node","lat":40.4,"lon":-3.7,"tags":{"amenity":"cafe"}},
              {"type":"way","tags":{"name":"Sin sitio","amenity":"cafe"}}
            ]}
        """.trimIndent()

        assertEquals(emptyList(), OverpassPlaceParser.parseCandidates(body))
    }

    @Test
    fun `should still read a centre when a mirror answers the old way`() {
        // Defensive: a mirror that ignored `bb` and replied like `out center` degrades to point
        // distances instead of losing the place entirely.
        val body = """
            {"elements":[
              {"type":"way","center":{"lat":40.4168,"lon":-3.7038},
               "tags":{"name":"Hospital Clínico","amenity":"hospital"}}
            ]}
        """.trimIndent()

        val candidate = OverpassPlaceParser.parseCandidates(body).single()

        assertEquals(40.4168, candidate.lat)
        assertNull(candidate.bounds)
    }

    @Test
    fun `should tolerate unknown fields and an empty response`() {
        assertEquals(emptyList(), OverpassPlaceParser.parseCandidates("""{"elements":[]}"""))
        assertEquals(
            emptyList(),
            OverpassPlaceParser.parseCandidates("""{"version":0.6,"generator":"Overpass","elements":[]}"""),
        )
    }

    @Test
    fun `should resolve every tag family it claims to support`() {
        assertEquals(PlaceCategory.FUEL, OverpassPlaceParser.resolveCategory(mapOf("amenity" to "fuel")))
        assertEquals(PlaceCategory.SUPERMARKET, OverpassPlaceParser.resolveCategory(mapOf("shop" to "convenience")))
        assertEquals(PlaceCategory.MALL, OverpassPlaceParser.resolveCategory(mapOf("shop" to "department_store")))
        assertEquals(PlaceCategory.HOTEL, OverpassPlaceParser.resolveCategory(mapOf("tourism" to "hostel")))
        assertEquals(PlaceCategory.GYM, OverpassPlaceParser.resolveCategory(mapOf("leisure" to "fitness_centre")))
        assertEquals(PlaceCategory.OTHER, OverpassPlaceParser.resolveCategory(mapOf("amenity" to "bench")))
    }

    private companion object {
        val RESPONSE = """
            {
              "version": 0.6,
              "generator": "Overpass API",
              "elements": [
                {
                  "type": "node",
                  "id": 1,
                  "lat": 40.4168,
                  "lon": -3.7038,
                  "tags": { "name": "Café Central", "amenity": "cafe" }
                },
                {
                  "type": "way",
                  "id": 2,
                  "bounds": {
                    "minlat": 40.4170, "minlon": -3.7042,
                    "maxlat": 40.4176, "maxlon": -3.7038
                  },
                  "tags": { "name": "Mercadona", "shop": "supermarket" }
                }
              ]
            }
        """.trimIndent()
    }
}
