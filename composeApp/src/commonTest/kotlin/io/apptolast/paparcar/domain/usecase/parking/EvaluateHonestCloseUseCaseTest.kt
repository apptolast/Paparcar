package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [DET-HONEST-CLOSE-001] Pure-decision coverage for the honest-close ladder. The two field aborts
 * that motivated the ticket are pinned as direct inputs: the Camelias hop (driven → zone) and the
 * D2 return (walked → silent). The gate that separates them is the hardware step budget, never
 * distance alone.
 */
class EvaluateHonestCloseUseCaseTest {

    private val useCase = EvaluateHonestCloseUseCase(ParkingDetectionConfig())

    private fun pinAt(lat: Double, lon: Double, acc: Float = 12f) = UserParking(
        id = "stale",
        vehicleId = "v-1",
        location = GpsPoint(lat, lon, accuracy = acc, timestamp = 0L, speed = 0f),
        geofenceId = "stale-fence",
        isActive = true,
    )

    private fun fixAt(lat: Double, lon: Double, acc: Float) =
        GpsPoint(lat, lon, accuracy = acc, timestamp = 0L, speed = 0f)

    @Test
    fun should_open_approximate_zone_when_short_hop_is_driven_but_anchor_is_not_pin_grade() {
        // Camelias hop (field 2026-07-14): Melgarejo pin → ~318 m to Camelias, only 23 steps since
        // the seal (the drive counted none; 23 is the walk around the new spot). 23 ≪ 170 (=40 %
        // of 318/0.75) → driven. The new spot's urban accuracy (60 m > pin-grade) → ZONE, not pin.
        val decision = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 60f),
            stepsSinceStalePin = 23L,
        )
        val zone = assertIs<HonestCloseDecision.ApproximateZone>(decision)
        assertTrue(zone.radiusMeters >= 60f, "the zone must read as an area, not a dot")
    }

    @Test
    fun should_stay_silent_when_the_walk_explains_the_distance() {
        // D2 return (field 2026-07-15): the user WALKED ~1.1 km from the still-parked car; the
        // stale exit was delivered at rest at the destination. The hardware counter recorded the
        // whole walk (~1099 steps ≥ 589 = 40 % of 1104/0.75) → the car never moved → silence.
        val decision = useCase(
            stalePin = pinAt(36.6054, -6.2727),
            abortFix = fixAt(36.6088, -6.2843, acc = 3f),
            stepsSinceStalePin = 1099L,
        )
        assertEquals(HonestCloseDecision.KeepSilent, decision, "a walk must never release the pin")
    }

    @Test
    fun should_drop_approximate_pin_when_driven_and_a_pin_grade_fix_is_in_hand() {
        // Trip proven (15 steps ≪ ~300 m) AND a pin-grade fix (acc 8 ≤ 50) → rung 1: a soft point.
        val decision = useCase(
            stalePin = pinAt(36.6000, -6.2500),
            abortFix = fixAt(36.6027, -6.2500, acc = 8f),
            stepsSinceStalePin = 15L,
        )
        val pin = assertIs<HonestCloseDecision.ApproximatePin>(decision)
        assertEquals(8f, pin.location.accuracy)
    }

    @Test
    fun should_stay_silent_when_the_counter_is_mute() {
        // Same driven geometry as the hop, but a MUTE counter cannot prove a ride OR rule out a
        // long walk → conservative silence; the safety net's mute-counter proofs are the backstop.
        val decision = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 60f),
            stepsSinceStalePin = null,
        )
        assertEquals(HonestCloseDecision.KeepSilent, decision)
    }

    @Test
    fun should_stay_silent_when_the_displacement_is_gps_wobble_beside_the_car() {
        // ~30 m from the stale pin — within both accuracy envelopes plus the trip floor. A re-arm
        // jitter beside the parked car, not a drive-away.
        val decision = useCase(
            stalePin = pinAt(36.6000, -6.2500, acc = 12f),
            abortFix = fixAt(36.60027, -6.2500, acc = 10f),
            stepsSinceStalePin = 3L,
        )
        assertEquals(HonestCloseDecision.KeepSilent, decision)
    }

    @Test
    fun should_stay_silent_when_there_is_no_stale_pin_to_reason_about() {
        val decision = useCase(
            stalePin = null,
            abortFix = fixAt(36.5974, -6.2505, acc = 8f),
            stepsSinceStalePin = 5L,
        )
        assertEquals(HonestCloseDecision.KeepSilent, decision)
    }
}
