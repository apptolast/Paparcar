package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.Shape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] */
class SpotReleasePolicyTest {

    @Test
    fun should_publishExactWithSessionConfidence_when_sessionIsNotAZone() {
        val shape = SpotReleasePolicy.shapeFor(zoneRadiusMeters = null, detectionReliability = 0.9f)
        assertEquals(Shape.Exact(0.9f), shape)
    }

    @Test
    fun should_defaultToFullConfidence_when_legacyCallerHasNoSession() {
        val shape = SpotReleasePolicy.shapeFor(zoneRadiusMeters = null, detectionReliability = null)
        assertEquals(Shape.Exact(1f), shape)
    }

    @Test
    fun should_publishZoneWithItsRadius_when_zoneIsWithinTheCeiling() {
        val shape = SpotReleasePolicy.shapeFor(zoneRadiusMeters = 60f, detectionReliability = 0.5f)
        assertEquals(Shape.Zone(radiusMeters = 60f, confidence = 0.5f), shape)
    }

    @Test
    fun should_capConfidence_when_userConfirmedZoneCarriesFullReliability() {
        // The "Sí" proves the park, not the spot: a zone born from a user answer arrives here with
        // reliability 1.0 and must NOT pass it to the community. [DET-USER-YES-IS-NOT-A-COORDINATE-001]
        val shape = SpotReleasePolicy.shapeFor(zoneRadiusMeters = 60f, detectionReliability = 1f)
        assertIs<Shape.Zone>(shape)
        assertEquals(SpotReleasePolicy.ZONE_MAX_CONFIDENCE, shape.confidence)
    }

    @Test
    fun should_publishZone_when_radiusSitsExactlyOnTheCeiling() {
        val shape = SpotReleasePolicy.shapeFor(
            zoneRadiusMeters = SpotReleasePolicy.ZONE_PUBLISH_MAX_RADIUS_METERS,
            detectionReliability = 0.5f,
        )
        assertIs<Shape.Zone>(shape)
    }

    @Test
    fun should_withhold_when_zoneIsWiderThanTheCeiling() {
        // The measured gap-anchor zones (r=250 m, doubt up to 495 m) land here: sending a seeker
        // to scan a neighbourhood is the ghost pin's damage on foot.
        val shape = SpotReleasePolicy.shapeFor(zoneRadiusMeters = 250f, detectionReliability = 0.5f)
        assertEquals(Shape.Withheld, shape)
    }
}
