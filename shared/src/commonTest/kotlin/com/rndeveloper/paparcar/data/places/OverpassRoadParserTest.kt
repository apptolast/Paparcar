package com.rndeveloper.paparcar.data.places

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The road wire format, which had no test while it lived inside an androidMain transport.
 * [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001]
 *
 * `isMinor` is not bookkeeping: it is what stops a parking aisle running parallel to the street from
 * stealing the drawn line on a straight stretch. [ROUTE-QUALITY-001]
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
}
