package io.apptolast.paparcar.domain.coordinator

import io.apptolast.paparcar.domain.detection.DetectionPhase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the coarse UI mapping [ConfirmationPhase.toDetectionPhase]. Only the HIGH-confidence
 * [ConfirmationPhase.Candidate] must surface the "Parking…" treatment; every earlier phase
 * (including [ConfirmationPhase.LowReached]/[ConfirmationPhase.Notified], which fire on any brief
 * slowdown) stays [DetectionPhase.Driving], so the chip/banner don't read "Parking…" for most of a
 * normal trip. [DET-PHASE-001]
 */
class ConfirmationPhaseMappingTest {

    @Test
    fun should_map_only_candidate_to_parking_when_confirmation_phase_advances() {
        assertEquals(DetectionPhase.Driving, ConfirmationPhase.Idle.toDetectionPhase())
        assertEquals(DetectionPhase.Driving, ConfirmationPhase.LowReached(firstReachedAt = 1_000L).toDetectionPhase())
        assertEquals(DetectionPhase.Driving, ConfirmationPhase.Notified(shownAt = 2_000L).toDetectionPhase())
        assertEquals(
            DetectionPhase.Candidate,
            ConfirmationPhase.Candidate(highReachedAt = 3_000L, hadVehicleExit = true, shownAt = 2_000L).toDetectionPhase(),
        )
    }
}
