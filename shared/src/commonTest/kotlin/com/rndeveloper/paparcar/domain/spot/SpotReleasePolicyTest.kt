package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.Shape
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.WithholdReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
 * [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001]
 */
class SpotReleasePolicyTest {

    // Somewhere in Madrid; every "not in a zone" case parks here and every zone below is built
    // around it or measurably away from it.
    private val park = GpsPoint(
        latitude = 40.416775,
        longitude = -3.703790,
        accuracy = 8f,
        timestamp = 1_700_000_000_000L,
        speed = 0f,
    )

    private fun zone(
        id: String = "z-1",
        name: String = "Garaje",
        lat: Double = park.latitude,
        lon: Double = park.longitude,
        radiusMeters: Float = 50f,
        isPrivate: Boolean = true,
    ) = Zone(
        id = id,
        userId = "u-1",
        name = name,
        lat = lat,
        lon = lon,
        iconKey = "HOME",
        createdAt = 0L,
        radiusMeters = radiusMeters,
        isPrivate = isPrivate,
    )

    // ── The shape of what goes out ────────────────────────────────────────────

    @Test
    fun should_publishExactWithSessionConfidence_when_sessionIsNotAZone() {
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = null, detectionReliability = 0.9f, privateZones = emptyList())
        assertEquals(Shape.Exact(0.9f), shape)
    }

    @Test
    fun should_defaultToFullConfidence_when_legacyCallerHasNoSession() {
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = null, detectionReliability = null, privateZones = emptyList())
        assertEquals(Shape.Exact(1f), shape)
    }

    @Test
    fun should_publishZoneWithItsRadius_when_zoneIsWithinTheCeiling() {
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = 60f, detectionReliability = 0.5f, privateZones = emptyList())
        assertEquals(Shape.Zone(radiusMeters = 60f, confidence = 0.5f), shape)
    }

    @Test
    fun should_capConfidence_when_userConfirmedZoneCarriesFullReliability() {
        // The "Sí" proves the park, not the spot: a zone born from a user answer arrives here with
        // reliability 1.0 and must NOT pass it to the community. [DET-USER-YES-IS-NOT-A-COORDINATE-001]
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = 60f, detectionReliability = 1f, privateZones = emptyList())
        assertIs<Shape.Zone>(shape)
        assertEquals(SpotReleasePolicy.ZONE_MAX_CONFIDENCE, shape.confidence)
    }

    @Test
    fun should_publishZone_when_radiusSitsExactlyOnTheCeiling() {
        val shape = SpotReleasePolicy.shapeFor(
            park,
            zoneRadiusMeters = SpotReleasePolicy.ZONE_PUBLISH_MAX_RADIUS_METERS,
            detectionReliability = 0.5f,
            privateZones = emptyList(),
        )
        assertIs<Shape.Zone>(shape)
    }

    @Test
    fun should_withhold_when_zoneIsWiderThanTheCeiling() {
        // The measured gap-anchor zones (r=250 m, doubt up to 495 m) land here: sending a seeker
        // to scan a neighbourhood is the ghost pin's damage on foot.
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = 250f, detectionReliability = 0.5f, privateZones = emptyList())
        assertEquals(Shape.Withheld(WithholdReason.AREA_TOO_WIDE), shape)
    }

    // ── …and whether anything goes out at all ─────────────────────────────────
    // [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001]

    @Test
    fun should_withholdForThePrivateZone_when_theParkSitsInsideOne() {
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = null, detectionReliability = 1f, privateZones = listOf(zone()))
        assertEquals(Shape.Withheld(WithholdReason.PRIVATE_ZONE), shape)
    }

    @Test
    fun should_nameThePrivateZone_ratherThanTheCeiling_when_bothWouldWithhold() {
        // Both silence the publication; only one of them is the user's own decision, and it is the
        // one the dialog and the history detail have to be able to explain.
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = 250f, detectionReliability = 0.5f, privateZones = listOf(zone()))
        assertEquals(Shape.Withheld(WithholdReason.PRIVATE_ZONE), shape)
    }

    @Test
    fun should_publish_when_theOnlyZoneAroundIsNotPrivate() {
        // `privateZones` is the ALREADY-FILTERED list, so a caller handing over a public zone is
        // handing over a zone that must not gate anything. Guards the filter at the callers' edge.
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = null, detectionReliability = 1f, privateZones = emptyList())
        assertIs<Shape.Exact>(shape)
    }

    @Test
    fun should_publish_when_theParkIsOutsideEveryPrivateZone() {
        // ~1.4 km north of the park — well beyond the 50 m zone drawn there.
        val faraway = zone(lat = park.latitude + 0.0125)
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = null, detectionReliability = 1f, privateZones = listOf(faraway))
        assertIs<Shape.Exact>(shape)
    }

    @Test
    fun should_matchTheContainingZone_when_severalArePrivate() {
        val elsewhere = zone(id = "z-far", name = "Trabajo", lat = park.latitude + 0.0125)
        val here = zone(id = "z-here", name = "Garaje")
        assertEquals("z-here", SpotReleasePolicy.privateZoneAt(park, listOf(elsewhere, here))?.id)
    }

    @Test
    fun should_findNoZone_when_thereAreNone() {
        assertNull(SpotReleasePolicy.privateZoneAt(park, emptyList()))
    }

    @Test
    fun should_containThePark_when_itSitsExactlyOnTheZoneEdge() {
        // Inclusive boundary, same as the stamp in ConfirmParkingUseCase always used (`<=`): a car
        // on the garage threshold is in the garage.
        val onEdge = zone(radiusMeters = 0f)
        assertEquals("z-1", SpotReleasePolicy.privateZoneAt(park, listOf(onEdge))?.id)
    }
}
