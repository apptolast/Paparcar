package io.apptolast.paparcar.domain.detection.stages

import io.apptolast.paparcar.domain.detection.state.DetectionSessionState
import io.apptolast.paparcar.domain.detection.state.EgressEvidence
import io.apptolast.paparcar.domain.detection.state.SessionTelemetry
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] **The guard that threw away the 25-08 park,
 * and the one line that decides whether it fires.**
 *
 * The field session: the user stopped to refuel two streets from home, got out (16 steps), got back
 * in — a TRUE `IN_VEHICLE ENTER` — and drove the last few metres. Superseding was correct. What was
 * not is that the replacement session started from zero, so its own stream saw 35 s at ≤13,7 km/h,
 * never reached driving speed, and the egress walk that followed read as feet-before-wheels:
 * `⊘ false-ENTER abort — 12 steps before driving speed`. The park was lost with 23 minutes of proven
 * driving sitting in the session that had just been cancelled.
 *
 * [FalseEnterAbortStage] reads `driveAuthorized`, so the whole fix reduces to giving the successor
 * that authorization on the evidence the predecessor MEASURED. These tests pin both directions: an
 * inherited drive skips the guard, and a session with nothing inherited still aborts — the guard is
 * disarmed by evidence, never by the supersede itself.
 */
class FalseEnterAbortStageInheritedDriveTest {

    private val config = ParkingDetectionConfig()
    private val stage = FalseEnterAbortStage()

    private val fix = GpsPoint(36.6084, -6.2781, accuracy = 4f, timestamp = 1_000L, speed = 0f)

    private fun stateWith(session: SessionTelemetry, steps: Int) = DetectionSessionState(
        session = session,
        egress = EgressEvidence(stepCount = steps),
    )

    private fun evaluate(state: DetectionSessionState) =
        stage.evaluate(state, fix, now = 2_000L, stoppedDurationMs = 40_000L, config = config)

    @Test
    fun should_not_abort_the_egress_walk_when_the_drive_was_inherited_from_a_superseded_session() {
        val state = stateWith(
            SessionTelemetry().seededOnInheritedDrive(),
            steps = config.falseEnterAbortSteps + 1,
        )

        assertIs<StageVerdict.Skip>(evaluate(state))
    }

    /**
     * The discriminating half: identical state, identical steps, no inheritance. Removing the seed
     * from the successor puts this test — and the field case — straight back where it was.
     */
    @Test
    fun should_still_abort_the_same_steps_when_there_was_no_drive_to_inherit() {
        val state = stateWith(SessionTelemetry(), steps = config.falseEnterAbortSteps + 1)

        val verdict = evaluate(state)

        assertIs<StageVerdict.Handled>(verdict)
        assertTrue(verdict.stopsIteration)
    }
}
