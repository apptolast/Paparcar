package com.rndeveloper.paparcar.data.places

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The road-network wire format, extracted from the Android data source so iOS could not grow a
 * drifting copy — and tested for the first time on the way. [IOS-A-PARITY-TAIL-001]
 */
class OverpassRoadParserTest {

    @Test
    fun should_askOnlyForDrivableWaysWithTheirTags_when_buildingTheQuery() {
        val query = OverpassRoadParser.buildQuery(
            minLat = 40.41,
            minLon = -3.71,
            maxLat = 40.42,
            maxLon = -3.70,
            timeoutSeconds = 20,
        )

        // bbox order is (south, west, north, east) — swapping it silently queries the wrong place.
        assertTrue(query.contains("(40.41,-3.71,40.42,-3.7)"), query)
        assertTrue(query.contains("out tags geom;"), "without `tags` the highway class never arrives")
        assertTrue(query.contains("motorway"))
        assertFalse(query.contains("footway"), "a trail must never snap onto a pedestrian way")
        assertFalse(query.contains("cycleway"))
    }

    @Test
    fun should_handicapServiceWays_when_readingTheAnswer() {
        val roads = OverpassRoadParser.parse(
            """{"elements":[
                {"type":"way","tags":{"highway":"residential"},
                 "geometry":[{"lat":40.41,"lon":-3.71},{"lat":40.42,"lon":-3.70}]},
                {"type":"way","tags":{"highway":"service"},
                 "geometry":[{"lat":40.41,"lon":-3.71},{"lat":40.42,"lon":-3.70}]}
            ]}""",
        )

        assertEquals(2, roads.size)
        assertFalse(roads[0].isMinor, "a residential street is a real road")
        assertTrue(roads[1].isMinor, "a service way is matchable but must not win a straight stretch")
    }

    @Test
    fun should_dropAWay_when_itCarriesFewerThanTwoVertices() {
        val roads = OverpassRoadParser.parse(
            """{"elements":[
                {"type":"way","tags":{"highway":"primary"},"geometry":[{"lat":40.41,"lon":-3.71}]},
                {"type":"way","tags":{"highway":"primary"}}
            ]}""",
        )

        assertTrue(roads.isEmpty(), "a segment needs two ends; a single vertex cannot be snapped to")
    }

    @Test
    fun `should ask only for drivable ways with tags and geometry`() {
        val query = OverpassRoadParser.buildQuery(40.41, -3.71, 40.42, -3.70)

        // bbox order is (south, west, north, east) — swapping it returns an empty world silently.
        assertTrue(query.contains("(40.41,-3.71,40.42,-3.7)"), query)
        // `out tags geom` is load-bearing: without tags the highway class dies before
        // RoadWay.isMinor and the matcher loses the minor-way handicap. [ROUTE-QUALITY-001]
        assertTrue(query.contains("out tags geom;"), query)
        // Pedestrian classes must not be matchable — a trail snapped onto a footway is a bogus route.
        assertFalse(query.contains("footway"), query)
        assertFalse(query.contains("cycleway"), query)
        assertTrue(query.contains("residential"), query)
        assertTrue(query.contains("[out:json][timeout:${OverpassRoadParser.QUERY_TIMEOUT_S}];"), query)
    }

    @Test
    fun `should read geometry and flag service ways as minor`() {
        val roads = OverpassRoadParser.parseRoads(RESPONSE)

        assertEquals(2, roads.size)
        val main = roads.first { !it.isMinor }
        assertEquals(3, main.points.size)
        assertEquals(40.4168, main.points.first().latitude)
        assertEquals(-3.7038, main.points.first().longitude)

        // The parking aisle stays matchable but handicapped. [ROUTE-QUALITY-001]
        val aisle = roads.first { it.isMinor }
        assertEquals(2, aisle.points.size)
    }

    @Test
    fun `should skip ways without geometry or with a single vertex`() {
        val body = """
            {"elements":[
              {"type":"way","tags":{"highway":"residential"}},
              {"type":"way","geometry":[{"lat":40.4,"lon":-3.7}],"tags":{"highway":"residential"}}
            ]}
        """.trimIndent()

        assertEquals(emptyList(), OverpassRoadParser.parseRoads(body))
    }

    private companion object {
        val RESPONSE = """
            {"elements":[
              {"type":"way",
               "geometry":[{"lat":40.4168,"lon":-3.7038},{"lat":40.4169,"lon":-3.7036},{"lat":40.4171,"lon":-3.7034}],
               "tags":{"highway":"residential","name":"Calle Mayor"}},
              {"type":"way",
               "geometry":[{"lat":40.4172,"lon":-3.7040},{"lat":40.4173,"lon":-3.7041}],
               "tags":{"highway":"service","service":"parking_aisle"}}
            ]}
        """.trimIndent()
    }
}
