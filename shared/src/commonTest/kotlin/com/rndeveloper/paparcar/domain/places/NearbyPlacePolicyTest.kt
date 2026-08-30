package com.rndeveloper.paparcar.domain.places

import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.util.BoundingBox
import com.rndeveloper.paparcar.domain.util.METERS_PER_DEGREE_LAT
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The naming rule: nearest wins, edges are what get measured, and far places stay unnamed.
 * [POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001]
 */
class NearbyPlacePolicyTest {

    private val originLat = 40.416775
    private val originLon = -3.703790

    /** A node [meters] due north of the origin — latitude only, so the distance is exact. */
    private fun nodeAt(
        meters: Double,
        name: String,
        category: PlaceCategory,
    ) = PlaceCandidate(
        name = name,
        category = category,
        lat = originLat + meters / METERS_PER_DEGREE_LAT,
        lon = originLon,
    )

    @Test
    fun `should name the nearest place when a higher priority one sits further away`() {
        val picked = NearbyPlacePolicy.pick(
            listOf(
                nodeAt(39.0, "Repsol", PlaceCategory.FUEL),
                nodeAt(3.0, "Café Central", PlaceCategory.CAFE),
            ),
            originLat,
            originLon,
        )

        // Before this policy the priority list decided first and FUEL (index 0) beat CAFE (index 6),
        // so the pin at the café door was labelled with a petrol station most of a block away.
        assertEquals("Café Central", picked?.name)
    }

    @Test
    fun `should let category decide between two places sharing a doorway`() {
        val picked = NearbyPlacePolicy.pick(
            listOf(
                nodeAt(8.0, "Mercadona", PlaceCategory.SUPERMARKET),
                nodeAt(4.0, "Bar Manolo", PlaceCategory.CAFE),
            ),
            originLat,
            originLon,
        )

        // 4 m apart is the same portal, and there the priority list still does its original job.
        assertEquals("Mercadona", picked?.name)
    }

    @Test
    fun `should not name a place that the query reaches but the naming radius rejects`() {
        // 60 m: inside the 80 m asked of Overpass, so the old code named it — and outside the 40 m
        // this policy is willing to claim. This is the discriminating distance. A node beyond 80 m
        // never reaches us at all (`around:80` filters it server-side), so the user's "100 metros"
        // is really this band plus the priority bug below, not a candidate arriving from 100 m.
        assertNull(
            NearbyPlacePolicy.pick(
                listOf(nodeAt(60.0, "Hospital Clínico", PlaceCategory.HOSPITAL)),
                originLat,
                originLon,
            ),
        )
        // And the policy does not lean on the query for correctness: further out stays unnamed too.
        assertNull(
            NearbyPlacePolicy.pick(
                listOf(nodeAt(100.0, "Hospital Clínico", PlaceCategory.HOSPITAL)),
                originLat,
                originLon,
            ),
        )
    }

    @Test
    fun `should name a large place while standing inside its footprint`() {
        // A hypermarket whose polygon spans ~200 m of latitude: its centroid is ~100 m from the
        // pin, but the pin is INSIDE the building, so the honest distance is zero.
        val halfSpanDegrees = 100.0 / METERS_PER_DEGREE_LAT
        val centreLat = originLat + halfSpanDegrees
        val hypermarket = PlaceCandidate(
            name = "Carrefour",
            category = PlaceCategory.SUPERMARKET,
            lat = centreLat,
            lon = originLon,
            bounds = BoundingBox(
                minLat = centreLat - halfSpanDegrees,
                maxLat = centreLat + halfSpanDegrees,
                minLon = originLon - halfSpanDegrees,
                maxLon = originLon + halfSpanDegrees,
            ),
        )

        assertEquals(0.0, NearbyPlacePolicy.distanceMeters(hypermarket, originLat, originLon))
        assertEquals("Carrefour", NearbyPlacePolicy.pick(listOf(hypermarket), originLat, originLon)?.name)
    }

    @Test
    fun `should measure a bounded place to its edge and not to its centre`() {
        // Same 200 m-tall polygon, but the pin now sits 20 m south of its southern edge.
        val halfSpanDegrees = 100.0 / METERS_PER_DEGREE_LAT
        val edgeLat = originLat + 20.0 / METERS_PER_DEGREE_LAT
        val mall = PlaceCandidate(
            name = "Centro Comercial",
            category = PlaceCategory.MALL,
            lat = edgeLat + halfSpanDegrees,
            lon = originLon,
            bounds = BoundingBox(
                minLat = edgeLat,
                maxLat = edgeLat + 2 * halfSpanDegrees,
                minLon = originLon - halfSpanDegrees,
                maxLon = originLon + halfSpanDegrees,
            ),
        )

        val meters = NearbyPlacePolicy.distanceMeters(mall, originLat, originLon)
        assertTrue(abs(meters - 20.0) < 0.5, "expected ~20 m to the edge, measured $meters")
        // Measured to the centroid it would be ~120 m and the mall would be dropped.
        assertEquals("Centro Comercial", NearbyPlacePolicy.pick(listOf(mall), originLat, originLon)?.name)
    }

    @Test
    fun `should keep a node candidate exact when it carries no footprint`() {
        val pharmacy = nodeAt(25.0, "Farmacia", PlaceCategory.PHARMACY)

        val meters = NearbyPlacePolicy.distanceMeters(pharmacy, originLat, originLon)

        assertTrue(abs(meters - 25.0) < 0.5, "expected ~25 m, measured $meters")
    }

    @Test
    fun `should name nothing when there are no candidates at all`() {
        assertNull(NearbyPlacePolicy.pick(emptyList(), originLat, originLon))
    }

    @Test
    fun `should ignore a far high priority place while naming a near one of unknown category`() {
        val picked = NearbyPlacePolicy.pick(
            listOf(
                nodeAt(38.0, "Repsol", PlaceCategory.FUEL),
                nodeAt(2.0, "Peluquería Ana", PlaceCategory.OTHER),
            ),
            originLat,
            originLon,
        )

        // OTHER is last in the priority list; distance still outranks it.
        assertEquals("Peluquería Ana", picked?.name)
    }

    @Test
    fun `should name the nearest of two equally reachable places of the same category`() {
        val picked = NearbyPlacePolicy.pick(
            listOf(
                nodeAt(30.0, "Farmacia Lejos", PlaceCategory.PHARMACY),
                nodeAt(6.0, "Farmacia Cerca", PlaceCategory.PHARMACY),
            ),
            originLat,
            originLon,
        )

        assertEquals("Farmacia Cerca", picked?.name)
    }

    @Test
    fun `should keep the naming radius below the query radius`() {
        // The 80 m query exists to let big polygons enter the list and then be measured by their
        // edge — it is not a naming radius. If these ever met, the old bug would be back.
        assertTrue(
            NearbyPlacePolicy.NAMING_RADIUS_METERS < NearbyPlacePolicy.QUERY_RADIUS_METERS,
            "naming radius must stay stricter than what we ask Overpass for",
        )
    }
}
