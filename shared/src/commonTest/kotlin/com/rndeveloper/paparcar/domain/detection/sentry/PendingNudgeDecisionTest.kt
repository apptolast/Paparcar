package com.rndeveloper.paparcar.domain.detection.sentry

import com.rndeveloper.paparcar.domain.detection.DetectionTrigger

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
    fun arrival_handoff_always_nudges_because_the_departure_was_already_committed() {
        // [DET-HANDOFF-NOT-MANUAL-001] Not because a drive was witnessed — nothing was — but
        // because by the time this arm exists the safety net has already published the spot,
        // released the session and removed the geofence. A pending that dies here leaves the user
        // with no car and no watcher, exactly as a real departure would.
        assertTrue(shouldNudgeForStalePending(DetectionTrigger.ARRIVAL_HANDOFF.name, sawDriving = false))
    }

    @Test
    fun ar_enter_nudges_only_when_the_trip_actually_drove() {
        // A bare boarding is falsifiable (bus/taxi) → no nudge; a boarding that reached the
        // park-evaluation phase drove for real → nudge.
        assertFalse(shouldNudgeForStalePending(DetectionTrigger.AR_VEHICLE_ENTER.name, sawDriving = false))
        assertTrue(shouldNudgeForStalePending(DetectionTrigger.AR_VEHICLE_ENTER.name, sawDriving = true))
    }
}
