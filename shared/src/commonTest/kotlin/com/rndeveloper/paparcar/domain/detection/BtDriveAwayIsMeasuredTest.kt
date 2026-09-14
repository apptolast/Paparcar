package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] The measurement that turns a boarding into a departure.
 */
class BtDriveAwayIsMeasuredTest {

    private val config = ParkingDetectionConfig()
    private val connectedAt = 1_000_000_000_000L
    private val fenceRadius = 60f

    private val pin = GpsPoint(latitude = 40.4000, longitude = -3.7000, accuracy = 8f, timestamp = connectedAt - 3_600_000L, speed = 0f)

    /** A fix [metresNorth] from the pin, [afterMs] after boarding. ~111_320 m per degree of latitude. */
    private fun fixNorthOfPin(metresNorth: Double, afterMs: Long, accuracy: Float = 10f) = GpsPoint(
        latitude = pin.latitude + metresNorth / 111_320.0,
        longitude = pin.longitude,
        accuracy = accuracy,
        timestamp = connectedAt + afterMs,
        speed = 0f,
    )

    private fun measured(fix: GpsPoint, nowMs: Long = fix.timestamp) = btDriveAwayIsMeasured(
        pin = pin,
        current = fix,
        connectedAtMs = connectedAt,
        fenceRadiusMeters = fenceRadius,
        nowMs = nowMs,
        config = config,
    )

    @Test
    fun should_confirm_when_the_car_is_further_than_anyone_could_have_walked() {
        // 60 s after boarding: a pedestrian could cover 2.5 m/s × 60 s = 150 m, plus the 60 m fence
        // and 10 m of accuracy = 220 m of doubt. 900 m is a car.
        assertTrue(measured(fixNorthOfPin(metresNorth = 900.0, afterMs = 60_000L)))
    }

    @Test
    fun should_refuse_when_the_displacement_is_still_walkable() {
        // 200 m in 60 s is brisk, and the ticket's whole point is that brisk is not a car.
        assertFalse(measured(fixNorthOfPin(metresNorth = 200.0, afterMs = 60_000L)))
    }

    @Test
    fun should_refuse_when_the_user_boarded_and_did_not_drive() {
        // Sitting in the car for ten minutes — radio on, looking for something. The phone never
        // leaves the plaza, so nothing is ever measured and the watch stands down in silence.
        assertFalse(measured(fixNorthOfPin(metresNorth = 5.0, afterMs = 10 * 60_000L)))
    }

    @Test
    fun should_refuse_a_stale_fix_however_far_away_it_claims_to_be() {
        // ⛔ The shape that would fake a drive-away: a cached last-known from somewhere else,
        // timestamped just after the CONNECT. The fused provider serves these routinely as its
        // first emission — the fix is 900 m away and the arithmetic below would pass it.
        val cached = fixNorthOfPin(metresNorth = 900.0, afterMs = 60_000L)
        val nowMuchLater = cached.timestamp + config.freshFixMaxAgeMs + 1L

        assertFalse(measured(cached, nowMs = nowMuchLater))
        assertTrue(measured(cached), "the same fix judged while still fresh IS a drive")
    }

    @Test
    fun should_refuse_when_the_fix_predates_the_boarding() {
        // Negative elapsed: nothing can be measured about a departure by a fix taken before the
        // user got in. `isBeyondPedestrianReach` already fails closed on this; asserted here so the
        // BT lane's own contract states it.
        assertFalse(measured(fixNorthOfPin(metresNorth = 900.0, afterMs = -30_000L)))
    }

    @Test
    fun should_grant_the_fence_and_the_accuracy_to_the_pedestrian() {
        // A wide fence with a sloppy fix must not read as a drive at t≈0. Radius 135 m (van) plus
        // 90 m of accuracy is 225 m of doubt before a single second of walking is credited.
        val wideFence = 135f
        val sloppy = fixNorthOfPin(metresNorth = 200.0, afterMs = 1_000L, accuracy = 90f)

        assertFalse(
            btDriveAwayIsMeasured(pin, sloppy, connectedAt, wideFence, sloppy.timestamp, config),
            "inside fence + accuracy is compatible with never having left",
        )
    }
}
