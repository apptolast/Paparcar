package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The reduction reports when it kills an open
 * question.
 *
 * `stopEnded()` has always reset the conversation on measured driving, so past that line
 * `ResponseTimeoutStage` has nothing to fire and the question is dead INTERNALLY. What nobody could
 * observe is the moment it happens — and because nobody could, the tray card and the Home row went
 * on asking "did you park?" for the rest of the 15-minute window while the car drove. These cases
 * pin the report, not the reset: the reset was already covered, the silence was the bug.
 *
 * The walking case is the one that matters most. Retracting on a pedestrian fix would throw away a
 * REAL parking — walking away from the car is evidence you parked, not evidence you are driving —
 * so "did not retract" is as much a requirement here as "did retract" is above it.
 */
class StopTrackingPromptRetractionTest {

    private val config = ParkingDetectionConfig()

    /** A stop with an open question: the prompt was posted, the user has not answered. */
    private fun asked(at: Long = 0L) = DetectionSessionState(
        confirmation = ConfirmationLifecycle().notified(shownAt = at),
    )

    /** Above `minimumTripSpeedMps` (5 m/s) with a credible fix — unambiguous car movement. */
    private fun drivingFix(at: Long = 1_000L) =
        GpsPoint(36.6119, -6.2805, accuracy = 8f, timestamp = at, speed = 12f)

    /** Moving, but in the pedestrian band: above the stop bar (1 m/s), below `clearBestStopSpeedMps`
     *  (2.5 m/s). This is the egress walk. */
    private fun walkingFix(at: Long = 1_000L) =
        GpsPoint(36.6119, -6.2805, accuracy = 8f, timestamp = at, speed = 1.4f)

    @Test
    fun should_report_a_retraction_when_measured_driving_resumes_under_an_open_prompt() {
        val tracked = asked().updateStopTracking(drivingFix(), now = 1_000L, config = config)

        assertTrue(tracked.promptRetracted, "a driving fix must retract the question it invalidates")
        assertEquals(
            ConfirmationPhase.Idle,
            tracked.state.confirmation.phase,
            "the conversation restarts on driving — the report must agree with the reset",
        )
        assertTrue(
            tracked.notes.any { "RETRACTED" in it.text },
            "a retraction the trace cannot see reads as a prompt that was never resolved",
        )
    }

    @Test
    fun should_not_retract_when_the_user_walks_away_from_the_car() {
        val tracked = asked().updateStopTracking(walkingFix(), now = 1_000L, config = config)

        assertFalse(
            tracked.promptRetracted,
            "walking away is evidence the car is parked — retracting here would lose a real parking",
        )
        assertTrue(
            tracked.state.confirmation.promptShownAt != null,
            "the response timeout must keep ticking through the egress walk",
        )
    }

    @Test
    fun should_not_report_a_retraction_when_no_question_was_open() {
        val tracked = DetectionSessionState().updateStopTracking(
            drivingFix(), now = 1_000L, config = config,
        )

        assertFalse(
            tracked.promptRetracted,
            "there is nothing to take off screen, and a dismiss would fire on every driving fix",
        )
    }

    @Test
    fun should_not_report_a_retraction_while_the_car_is_stopped() {
        val stoppedFix = GpsPoint(36.6119, -6.2805, accuracy = 8f, timestamp = 1_000L, speed = 0f)

        val tracked = asked().updateStopTracking(stoppedFix, now = 1_000L, config = config)

        assertFalse(tracked.promptRetracted, "a stop is the state the question was asked about")
    }
}
