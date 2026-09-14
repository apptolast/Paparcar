package com.rndeveloper.paparcar.domain.parkings

import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The grid, and the one promise it has to keep.
 * [PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001]
 */
class ParkingZoneGridTest {

    private val solLat = 40.4168
    private val solLon = -3.7038

    @Test
    fun should_giveTheSameZone_when_askedTwiceAboutTheSamePoint() {
        assertEquals(ParkingZoneGrid.zoneOf(solLat, solLon), ParkingZoneGrid.zoneOf(solLat, solLon))
    }

    @Test
    fun should_giveTheSameZone_when_theCameraPansAFewHundredMetres() {
        // The whole point: a short pan must not produce a new query. At 750 m the OLD rule already
        // re-asked; the zone rule does not.
        val panned = ParkingZoneGrid.zoneOf(solLat + 0.006, solLon + 0.004) // ~670 m NE
        assertEquals(ParkingZoneGrid.zoneOf(solLat, solLon), panned)
    }

    @Test
    fun should_giveADifferentZone_when_theCameraLeavesIt() {
        // Far enough that no cell can hold both: 3 km cells, so 10 km apart is four cells away.
        assertFalse(ParkingZoneGrid.sameZone(solLat, solLon, solLat + 0.09, solLon))
    }

    /**
     * ⛔ **The promise.** A driver standing anywhere in the zone — corner included — must still be
     * covered out to the display radius by a disc drawn from the zone's CENTRE. If this fails, the
     * lane goes quietly short exactly for the people furthest from the centre, which is the hardest
     * kind of bug to notice: it looks like "there are no car parks here".
     */
    @Test
    fun should_reachTheDisplayRadius_forEverybodyInTheZone_includingTheCorners() {
        val zone = ParkingZoneGrid.zoneOf(solLat, solLon)

        // Sweep the cell densely enough to land on its corners, in both axes.
        val step = 0.0005
        var worstShortfall = 0.0
        var probes = 0
        var lat = zone.centreLatitude - 0.02
        while (lat <= zone.centreLatitude + 0.02) {
            var lon = zone.centreLongitude - 0.03
            while (lon <= zone.centreLongitude + 0.03) {
                if (ParkingZoneGrid.zoneOf(lat, lon) == zone) {
                    probes++
                    val fromCentre = haversineMeters(zone.centreLatitude, zone.centreLongitude, lat, lon)
                    val reachLeft = ParkingZoneGrid.ZONE_RADIUS_METERS - fromCentre
                    val shortfall = PublicParkingsQuery.DEFAULT_RADIUS_METERS - reachLeft
                    if (shortfall > worstShortfall) worstShortfall = shortfall
                }
                lon += step
            }
            lat += step
        }

        assertTrue(probes > 1_000, "the sweep must actually cover the cell; it probed $probes points")
        assertTrue(
            worstShortfall <= 0.0,
            "somebody in this zone is served ${worstShortfall.toInt()} m short of the display radius",
        )
    }

    @Test
    fun should_keepZonesRoughlySquare_when_theLatitudeChanges() {
        // ⛔ A degree of longitude is 85 km at Málaga and 62 km at Amsterdam. A grid in bare degrees
        // would make northern zones far narrower than southern ones, and the reach above is sized
        // off ONE cell size — so the cell has to stay put in metres, not in degrees.
        listOf(36.72 to -4.42, 40.42 to -3.70, 52.37 to 4.90).forEach { (lat, lon) ->
            val zone = ParkingZoneGrid.zoneOf(lat, lon)
            val widest = widestSpanOf(zone)
            assertTrue(
                widest in 2_500.0..3_500.0,
                "a zone at $lat is $widest m across; the grid is supposed to be 3 km everywhere",
            )
        }
    }

    /**
     * ⛔ Two neighbours must agree on the grid. The longitude step is derived from the SNAPPED
     * latitude and not from each point's own, precisely so that this holds — a step that varied
     * continuously would give two people on the same street slightly different grids, every cache
     * would miss, and nothing would look broken.
     */
    @Test
    fun should_agreeOnTheGrid_when_twoPointsShareABand() {
        val north = ParkingZoneGrid.zoneOf(solLat + 0.004, solLon)
        val south = ParkingZoneGrid.zoneOf(solLat - 0.004, solLon)
        if (north.centreLatitude == south.centreLatitude) {
            assertEquals(north.centreLongitude, south.centreLongitude)
        }
        // And across the whole band, every point snaps onto the same ladder of longitudes.
        val centres = (0..40).map { ParkingZoneGrid.zoneOf(solLat, solLon + it * 0.001).centreLongitude }
        val spacings = centres.distinct().sorted().zipWithNext { a, b -> b - a }
        assertTrue(
            spacings.all { kotlin.math.abs(it - spacings.first()) < 1e-9 },
            "the ladder must be evenly spaced, got $spacings",
        )
    }

    @Test
    fun should_notBlowUp_when_givenAnAbsurdLatitude() {
        // A broken fix must produce a nonsense-free zone rather than a cell wider than a continent.
        val polar = ParkingZoneGrid.zoneOf(89.9, 12.0)
        assertTrue(polar.centreLongitude.isFinite())
        assertTrue(widestSpanOf(polar) < 10_000.0, "a polar zone must still be bounded")
    }

    /** How wide the zone is in metres at its widest, which for a lat/lon cell is its lower edge. */
    private fun widestSpanOf(zone: ParkingZoneGrid.Zone): Double {
        val step = probeLonStep(zone)
        return haversineMeters(
            zone.centreLatitude, zone.centreLongitude - step / 2,
            zone.centreLatitude, zone.centreLongitude + step / 2,
        )
    }

    /** Recovers the grid's longitude step by walking east until the zone changes. */
    private fun probeLonStep(zone: ParkingZoneGrid.Zone): Double {
        var lon = zone.centreLongitude
        while (ParkingZoneGrid.zoneOf(zone.centreLatitude, lon) == zone) lon += 0.0001
        return (lon - zone.centreLongitude) * 2
    }
}
