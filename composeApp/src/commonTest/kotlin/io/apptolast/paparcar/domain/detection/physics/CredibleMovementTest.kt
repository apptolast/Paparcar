package io.apptolast.paparcar.domain.detection.physics

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [LOC-002] The gate that decides whether a fix's reported speed is worth believing.
 *
 * The field incident these tests stand for: 2026-07-04, brisk walking away from a parked car
 * produced 2.5–3.6 m/s Doppler readings that wiped the true anchor, and the park re-anchored 55 m
 * away where the user next stood still. A receiver reports its speed just as confidently when it has
 * no idea where it is.
 */
class CredibleMovementTest {

    private fun fix(speed: Float, accuracy: Float) =
        GpsPoint(latitude = 36.6, longitude = -6.2, accuracy = accuracy, timestamp = 0L, speed = speed)

    @Test
    fun should_believe_the_speed_when_the_fix_is_accurate() {
        assertTrue(isCredibleFixAccuracy(fix(speed = 12f, accuracy = 8f), maxAccuracyMeters = 50f))
    }

    @Test
    fun should_refuse_the_speed_when_the_fix_does_not_know_where_it_is() {
        // The Redmi's starved stream: 75 km/h reported at 120 m of accuracy.
        assertFalse(isCredibleFixAccuracy(fix(speed = 20f, accuracy = 120f), maxAccuracyMeters = 50f))
    }

    /** The gate is inclusive: a fix exactly at the envelope is still admissible. */
    @Test
    fun should_admit_the_fix_when_accuracy_sits_exactly_at_the_envelope() {
        assertTrue(isCredibleFixAccuracy(fix(speed = 12f, accuracy = 50f), maxAccuracyMeters = 50f))
    }

    @Test
    fun should_call_it_driving_when_both_speed_and_accuracy_clear_their_bars() {
        assertTrue(isCredibleMovingFix(fix(speed = 12f, accuracy = 8f), speedBarMps = 5f, maxAccuracyMeters = 50f))
    }

    /**
     * The whole point: fast **and** blind is not driving. Without the accuracy half, a hallucinated
     * Doppler reading flips `hasEverReachedDrivingSpeed` and unlocks every confirm path.
     */
    @Test
    fun should_not_call_it_driving_when_the_speed_is_fast_but_blind() {
        assertFalse(isCredibleMovingFix(fix(speed = 20f, accuracy = 120f), speedBarMps = 5f, maxAccuracyMeters = 50f))
    }

    @Test
    fun should_not_call_it_driving_when_the_fix_is_accurate_but_slow() {
        assertFalse(isCredibleMovingFix(fix(speed = 1.2f, accuracy = 5f), speedBarMps = 5f, maxAccuracyMeters = 50f))
    }

    /**
     * The speed bar is inclusive (`>=`), and that matters: the hold's *driving resumed* test is
     * deliberately **exclusive** and therefore does NOT route through this function. If the two ever
     * get merged on resemblance, this test is where the difference is written down.
     */
    @Test
    fun should_admit_the_fix_when_speed_sits_exactly_at_the_bar() {
        assertTrue(isCredibleMovingFix(fix(speed = 5f, accuracy = 5f), speedBarMps = 5f, maxAccuracyMeters = 50f))
    }

    /**
     * Two bars, one function — the project runs a loose bar for "movement worth reacting to" and a
     * strict one for "real driving", and once egress steps are in hand only the strict one may clear
     * an anchor [ANCHOR-LOCK-001]. Same fix, opposite verdicts.
     */
    @Test
    fun should_split_the_same_fix_when_the_two_bars_disagree() {
        val brisk = fix(speed = 3.0f, accuracy = 5f)
        assertTrue(isCredibleMovingFix(brisk, speedBarMps = 2.5f, maxAccuracyMeters = 50f))
        assertFalse(isCredibleMovingFix(brisk, speedBarMps = 5.0f, maxAccuracyMeters = 50f))
    }
}
