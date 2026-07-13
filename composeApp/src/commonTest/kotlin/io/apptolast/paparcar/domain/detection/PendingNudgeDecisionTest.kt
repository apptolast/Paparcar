package io.apptolast.paparcar.domain.detection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [DET-NEVER-SILENT-001] Which stale (process-death-orphaned) pending deserves a nudge. */
class PendingNudgeDecisionTest {

    @Test
    fun geofence_exit_always_nudges_even_without_measured_driving() {
        // A departure from a known spot is a real trip whose park we owe the user (leg chino→casa).
        assertTrue(shouldNudgeForStalePending(DetectionTrigger.GEOFENCE_EXIT.name, sawDriving = false))
    }

    @Test
    fun manual_i_am_driving_always_nudges() {
        assertTrue(shouldNudgeForStalePending(DetectionTrigger.MANUAL.name, sawDriving = false))
    }

    @Test
    fun ar_enter_nudges_only_when_the_trip_actually_drove() {
        // A bare boarding is falsifiable (bus/taxi) → no nudge; a boarding that reached the
        // park-evaluation phase drove for real → nudge.
        assertFalse(shouldNudgeForStalePending(DetectionTrigger.AR_VEHICLE_ENTER.name, sawDriving = false))
        assertTrue(shouldNudgeForStalePending(DetectionTrigger.AR_VEHICLE_ENTER.name, sawDriving = true))
    }
}
