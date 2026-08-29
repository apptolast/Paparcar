package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] The independence rule shared by the arm lane and the
 * worker lane: one fix must never both FIRE a departure event and CONFIRM it.
 */
class DepartureSpeedProofTest {

    private val config = ParkingDetectionConfig()
    private val eventMs = 1_000_000L

    private fun classify(speedKmh: Float?, accuracyM: Float?, fixMs: Long?) = classifyDepartureSpeed(
        config = config,
        speedKmh = speedKmh,
        accuracyM = accuracyM,
        fixTimestampMs = fixMs,
        eventTimestampMs = eventMs,
    )

    @Test
    fun should_be_independent_when_credible_speed_postdates_the_event_by_the_gap() {
        assertEquals(
            DepartureSpeedVerdict.Independent,
            classify(60f, 5f, eventMs + config.departureProofMinGapMs),
        )
    }

    @Test
    fun should_be_echo_when_credible_speed_is_contemporaneous_with_the_event() {
        // The 2026-08-22 20:50 indoor mirage: 36 km/h at acc 5.5 m, sampled 20 ms after the exit
        // it had itself provoked.
        assertEquals(DepartureSpeedVerdict.Echo, classify(36f, 5.5f, eventMs + 20L))
    }

    @Test
    fun should_be_echo_when_credible_speed_falls_one_ms_short_of_the_gap() {
        assertEquals(
            DepartureSpeedVerdict.Echo,
            classify(60f, 5f, eventMs + config.departureProofMinGapMs - 1L),
        )
    }

    @Test
    fun should_be_echo_when_the_sample_has_no_timestamp() {
        // Fail closed — independence it cannot demonstrate is independence it does not have.
        assertEquals(DepartureSpeedVerdict.Echo, classify(60f, 5f, null))
    }

    @Test
    fun should_be_echo_when_the_sample_predates_the_event() {
        // A cached fix from before the trigger is the weakest form of the same error.
        assertEquals(DepartureSpeedVerdict.Echo, classify(60f, 5f, eventMs - 60_000L))
    }

    @Test
    fun should_be_notDriving_when_speed_is_below_the_departure_threshold() {
        assertEquals(
            DepartureSpeedVerdict.NotDriving,
            classify(config.minimumDepartureSpeedKmh - 1f, 5f, eventMs + config.departureProofMinGapMs),
        )
    }

    @Test
    fun should_be_notDriving_when_the_fix_accuracy_is_too_degraded_to_trust_its_speed() {
        // Independence does not rescue a fix whose speed was never believable.
        assertEquals(
            DepartureSpeedVerdict.NotDriving,
            classify(60f, config.minGpsAccuracyForDriving + 50f, eventMs + config.departureProofMinGapMs),
        )
    }

    @Test
    fun should_be_notDriving_when_there_is_no_speed_sample_at_all() {
        assertEquals(DepartureSpeedVerdict.NotDriving, classify(null, null, eventMs + 60_000L))
    }
}
