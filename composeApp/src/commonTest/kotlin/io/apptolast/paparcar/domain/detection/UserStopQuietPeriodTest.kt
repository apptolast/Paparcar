package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [DET-STOP-BUTTON-001] The quiet period the user opens by tapping "Parar detección". */
class UserStopQuietPeriodTest {

    private val config = ParkingDetectionConfig()
    private val stoppedAt = 1_000_000L

    private val automaticTriggers = listOf(
        DetectionTrigger.GEOFENCE_EXIT,
        DetectionTrigger.AR_VEHICLE_ENTER,
        DetectionTrigger.SIGNIFICANT_MOTION,
    )

    @Test
    fun should_suppress_every_automatic_trigger_when_the_quiet_period_is_running() {
        val now = stoppedAt + config.userStopQuietPeriodMs - 1
        automaticTriggers.forEach { trigger ->
            assertTrue(
                isArmSuppressedByUserStop(trigger, stoppedAt, now, config),
                "$trigger must not arm while the user's quiet period lasts",
            )
        }
    }

    @Test
    fun should_never_suppress_the_manual_trigger_when_the_quiet_period_is_running() {
        // "Estoy conduciendo" is the same user retracting the stop — blocking it would trap them.
        assertFalse(
            isArmSuppressedByUserStop(
                DetectionTrigger.MANUAL,
                stoppedAt,
                stoppedAt + 1_000L,
                config,
            ),
        )
    }

    @Test
    fun should_arm_again_when_the_quiet_period_has_lapsed() {
        val now = stoppedAt + config.userStopQuietPeriodMs
        automaticTriggers.forEach { trigger ->
            assertFalse(
                isArmSuppressedByUserStop(trigger, stoppedAt, now, config),
                "$trigger must arm normally once the quiet period is over",
            )
        }
    }

    @Test
    fun should_arm_normally_when_there_is_no_user_stop_on_record() {
        assertFalse(
            isArmSuppressedByUserStop(DetectionTrigger.GEOFENCE_EXIT, null, stoppedAt, config),
        )
        assertEquals(0L, userStopQuietPeriodRemainingMs(null, stoppedAt, config))
    }

    @Test
    fun should_arm_normally_when_the_stamp_lies_in_the_future() {
        // A clock jump backwards (timezone/NTP correction) must never mute detection indefinitely.
        val now = stoppedAt - 60_000L
        assertFalse(
            isArmSuppressedByUserStop(DetectionTrigger.AR_VEHICLE_ENTER, stoppedAt, now, config),
        )
        assertEquals(0L, userStopQuietPeriodRemainingMs(stoppedAt, now, config))
    }

    @Test
    fun should_report_the_remaining_quiet_time_when_the_period_is_running() {
        val elapsed = 60_000L
        assertEquals(
            config.userStopQuietPeriodMs - elapsed,
            userStopQuietPeriodRemainingMs(stoppedAt, stoppedAt + elapsed, config),
        )
    }

    @Test
    fun should_never_suppress_anything_when_the_quiet_period_is_disabled() {
        val disabled = ParkingDetectionConfig(userStopQuietPeriodMs = 0L)
        automaticTriggers.forEach { trigger ->
            assertFalse(
                isArmSuppressedByUserStop(trigger, stoppedAt, stoppedAt, disabled),
                "$trigger must arm when the quiet period is configured off",
            )
        }
    }
}
