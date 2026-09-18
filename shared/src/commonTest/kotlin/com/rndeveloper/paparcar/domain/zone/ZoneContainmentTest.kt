package com.rndeveloper.paparcar.domain.zone

import com.rndeveloper.paparcar.domain.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001] Which zone a parked car is standing in. */
class ZoneContainmentTest {

    private val lat = 40.4168
    private val lon = -3.7038

    private fun zone(id: String, radius: Float, metresNorth: Double = 0.0, private: Boolean = false) = Zone(
        id = id,
        userId = "u",
        name = id,
        lat = lat + metresNorth / 111_320.0,
        lon = lon,
        iconKey = "home",
        createdAt = 0L,
        radiusMeters = radius,
        isPrivate = private,
    )

    @Test
    fun should_find_the_zone_whose_radius_covers_the_car() {
        val zones = listOf(zone("far", radius = 50f, metresNorth = 500.0), zone("here", radius = 80f))

        assertEquals("here", zones.zoneContaining(lat, lon)?.id)
    }

    @Test
    fun should_find_nothing_when_the_car_is_outside_every_radius() {
        val zones = listOf(zone("near-miss", radius = 40f, metresNorth = 60.0))

        assertNull(zones.zoneContaining(lat, lon), "60 m away from a 40 m zone is outside it")
    }

    @Test
    fun should_pick_the_smallest_zone_when_they_overlap() {
        // "Garaje de casa" (40 m) sits inside "Barrio" (600 m). The car is in both, and the useful
        // answer — the one the marker will wear — is the specific place, not the district.
        val zones = listOf(zone("barrio", radius = 600f), zone("garaje", radius = 40f))

        assertEquals("garaje", zones.zoneContaining(lat, lon)?.id)
    }

    @Test
    fun should_answer_the_same_zone_every_time_when_two_share_a_radius() {
        // A marker that alternated between two zones would re-rasterise its bitmap forever, so the
        // tie breaks on id rather than on list order.
        val zones = listOf(zone("b", radius = 100f), zone("a", radius = 100f))

        assertEquals("a", zones.zoneContaining(lat, lon)?.id)
        assertEquals("a", zones.reversed().zoneContaining(lat, lon)?.id)
    }

    @Test
    fun should_carry_the_privacy_of_the_zone_it_found() {
        val zones = listOf(zone("garage", radius = 40f, private = true))

        assertEquals(true, zones.zoneContaining(lat, lon)?.isPrivate)
    }
}
