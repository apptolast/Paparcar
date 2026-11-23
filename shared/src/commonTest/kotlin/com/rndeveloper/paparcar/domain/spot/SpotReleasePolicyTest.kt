package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.RefuelStopAnswer
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.Shape
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.WithholdReason
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.refuelStopAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = null, detectionReliability = 0.9f, placeInfo = null,
            dwellMs = null,
            privateZones = emptyList())
        assertEquals(Shape.Exact(0.9f), shape)
    }

    @Test
    fun should_defaultToFullConfidence_when_legacyCallerHasNoSession() {
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = null, detectionReliability = null, placeInfo = null,
            dwellMs = null,
            privateZones = emptyList())
        assertEquals(Shape.Exact(1f), shape)
    }

    @Test
    fun should_publishZoneWithItsRadius_when_zoneIsWithinTheCeiling() {
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = 60f, detectionReliability = 0.5f, placeInfo = null,
            dwellMs = null,
            privateZones = emptyList())
        assertEquals(Shape.Zone(radiusMeters = 60f, confidence = 0.5f), shape)
    }

    @Test
    fun should_capConfidence_when_userConfirmedZoneCarriesFullReliability() {
        // The "Sí" proves the park, not the spot: a zone born from a user answer arrives here with
        // reliability 1.0 and must NOT pass it to the community. [DET-USER-YES-IS-NOT-A-COORDINATE-001]
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = 60f, detectionReliability = 1f, placeInfo = null,
            dwellMs = null,
            privateZones = emptyList())
        assertIs<Shape.Zone>(shape)
        assertEquals(SpotReleasePolicy.ZONE_MAX_CONFIDENCE, shape.confidence)
    }

    @Test
    fun should_publishZone_when_radiusSitsExactlyOnTheCeiling() {
        val shape = SpotReleasePolicy.shapeFor(
            park,
            zoneRadiusMeters = SpotReleasePolicy.ZONE_PUBLISH_MAX_RADIUS_METERS,
            detectionReliability = 0.5f,
            placeInfo = null,
            dwellMs = null,
            privateZones = emptyList(),
        )
        assertIs<Shape.Zone>(shape)
    }

    @Test
    fun should_withhold_when_zoneIsWiderThanTheCeiling() {
        // The measured gap-anchor zones (r=250 m, doubt up to 495 m) land here: sending a seeker
        // to scan a neighbourhood is the ghost pin's damage on foot.
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = 250f, detectionReliability = 0.5f, placeInfo = null,
            dwellMs = null,
            privateZones = emptyList())
        assertEquals(Shape.Withheld(WithholdReason.AREA_TOO_WIDE), shape)
    }

    // ── …and whether anything goes out at all ─────────────────────────────────
    // [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001]

    @Test
    fun should_withholdForThePrivateZone_when_theParkSitsInsideOne() {
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = null, detectionReliability = 1f, placeInfo = null,
            dwellMs = null,
            privateZones = listOf(zone()))
        assertEquals(Shape.Withheld(WithholdReason.PRIVATE_ZONE), shape)
    }

    @Test
    fun should_nameThePrivateZone_ratherThanTheCeiling_when_bothWouldWithhold() {
        // Both silence the publication; only one of them is the user's own decision, and it is the
        // one the dialog and the history detail have to be able to explain.
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = 250f, detectionReliability = 0.5f, placeInfo = null,
            dwellMs = null,
            privateZones = listOf(zone()))
        assertEquals(Shape.Withheld(WithholdReason.PRIVATE_ZONE), shape)
    }

    @Test
    fun should_publish_when_theOnlyZoneAroundIsNotPrivate() {
        // `privateZones` is the ALREADY-FILTERED list, so a caller handing over a public zone is
        // handing over a zone that must not gate anything. Guards the filter at the callers' edge.
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = null, detectionReliability = 1f, placeInfo = null,
            dwellMs = null,
            privateZones = emptyList())
        assertIs<Shape.Exact>(shape)
    }

    @Test
    fun should_publish_when_theParkIsOutsideEveryPrivateZone() {
        // ~1.4 km north of the park — well beyond the 50 m zone drawn there.
        val faraway = zone(lat = park.latitude + 0.0125)
        val shape = SpotReleasePolicy.shapeFor(park, zoneRadiusMeters = null, detectionReliability = 1f, placeInfo = null,
            dwellMs = null,
            privateZones = listOf(faraway))
        assertIs<Shape.Exact>(shape)
    }

    @Test
    fun should_matchTheContainingZone_when_severalArePrivate() {
        val elsewhere = zone(id = "z-far", name = "Trabajo", lat = park.latitude + 0.0125)
        val here = zone(id = "z-here", name = "Garaje")
        assertEquals("z-here", SpotReleasePolicy.privateZoneAt(park, listOf(elsewhere, here))?.id)
    }

    // ── A forecourt is not a plaza [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] ─

    private val pump = PlaceInfo("Repsol Bravo Murillo", PlaceCategory.FUEL)
    private val shop = PlaceInfo("Mercadona", PlaceCategory.SUPERMARKET)
    private val tankfulMs = 4L * 60L * 1000L

    @Test
    fun should_withholdTheSpot_when_aShortStopSatAtAPump() {
        val shape = SpotReleasePolicy.shapeFor(
            park, zoneRadiusMeters = null, detectionReliability = 1f,
            placeInfo = pump, dwellMs = tankfulMs, privateZones = emptyList(),
        )
        assertEquals(Shape.Withheld(WithholdReason.REFUEL_STOP), shape)
    }

    @Test
    fun should_publishNormally_when_theStopAtAPumpLastedLongerThanATankful() {
        // Parked NEXT to a station for the afternoon. The 40 m naming radius cannot tell that from
        // standing on the forecourt; the clock can, and the long stay is a park.
        val shape = SpotReleasePolicy.shapeFor(
            park, zoneRadiusMeters = null, detectionReliability = 1f,
            placeInfo = pump,
            dwellMs = SpotReleasePolicy.REFUEL_STOP_MAX_DWELL_MS + 1,
            privateZones = emptyList(),
        )
        assertEquals(Shape.Exact(1f), shape)
    }

    @Test
    fun should_publishNormally_when_theShortStopWasAtSomewhereElse() {
        val shape = SpotReleasePolicy.shapeFor(
            park, zoneRadiusMeters = null, detectionReliability = 1f,
            placeInfo = shop, dwellMs = tankfulMs, privateZones = emptyList(),
        )
        assertEquals(Shape.Exact(1f), shape)
    }

    @Test
    fun should_claimNothing_when_thereIsNoPoiOrNoClock() {
        // Both absences are the safe side: an unenriched session publishes exactly as it did
        // before the gate existed, instead of withholding every park the enrichment missed.
        assertFalse(SpotReleasePolicy.isRefuelStop(placeInfo = null, dwellMs = tankfulMs))
        assertFalse(SpotReleasePolicy.isRefuelStop(placeInfo = pump, dwellMs = null))
        assertTrue(SpotReleasePolicy.isRefuelStop(placeInfo = pump, dwellMs = tankfulMs))
    }

    @Test
    fun should_nameThePrivateZone_when_theGarageAlsoLooksLikeAPump() {
        // Both withhold, and the user's own will is the truer account of why nothing went out.
        val shape = SpotReleasePolicy.shapeFor(
            park, zoneRadiusMeters = null, detectionReliability = 1f,
            placeInfo = pump, dwellMs = tankfulMs, privateZones = listOf(zone()),
        )
        assertEquals(Shape.Withheld(WithholdReason.PRIVATE_ZONE), shape)
    }

    @Test
    fun should_nameTheRefuel_when_theStopIsAlsoAnOverWideArea() {
        // "We were not sure where you were" would be the wrong account of a place we are sure
        // about: it was a forecourt. The sharper reason wins.
        val shape = SpotReleasePolicy.shapeFor(
            park,
            zoneRadiusMeters = SpotReleasePolicy.ZONE_PUBLISH_MAX_RADIUS_METERS + 1,
            detectionReliability = 1f,
            placeInfo = pump, dwellMs = tankfulMs, privateZones = emptyList(),
        )
        assertEquals(Shape.Withheld(WithholdReason.REFUEL_STOP), shape)
    }

    @Test
    fun should_leaveTheQuestionBehind_when_andOnlyWhen_theRefuelWithheldIt() {
        // The gate and the history question are ONE verdict: the bridge is what stops a door from
        // withholding for a pump and forgetting to ask about it.
        assertEquals(
            RefuelStopAnswer.PENDING,
            Shape.Withheld(WithholdReason.REFUEL_STOP).refuelStopAnswer(),
        )
        assertNull(Shape.Withheld(WithholdReason.PRIVATE_ZONE).refuelStopAnswer())
        assertNull(Shape.Withheld(WithholdReason.AREA_TOO_WIDE).refuelStopAnswer())
        assertNull(Shape.Exact(1f).refuelStopAnswer())
        assertNull(Shape.Zone(60f, 0.5f).refuelStopAnswer())
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
