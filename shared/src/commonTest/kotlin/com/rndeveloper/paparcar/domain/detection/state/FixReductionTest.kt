package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.detection.physics.DriveProofBounds
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-FIX-REDUCTION-TO-ITS-REDUCER-001] The reduction's trace, now that it is data.
 *
 * Every line this reducer produces describes a CROSSING — "→ true", "PROVEN by", "witnessed" — so
 * the property worth pinning is that each is said on the fix that crosses and never again. While
 * the block lived inside a retryable `updateAndGet` lambda that property was not merely untested,
 * it was untestable: the lines went to `PaparcarLogger` from inside a lambda the CAS may re-run, so
 * "how many times was this announced" had no answer a test could read. Moving the reduction out is
 * what makes the question askable, and this file is the reason the move was worth making rather
 * than a tidy-up.
 *
 * It also guards the note text itself. The strings were MOVED, not retyped, and nothing else in the
 * suite reads `parkdiag` — a transcription slip in a diagnostic line stays invisible until someone
 * is reading a trace to explain a lost trip.
 */
class FixReductionTest {

    private val config = ParkingDetectionConfig()

    /** The same shape `DriveProofTest` uses, so both files read the one set of numbers. */
    private val bounds = DriveProofBounds(
        windowMinMs = config.driveProofWindowMinMs,
        windowMaxMs = config.driveProofWindowMaxMs,
        hopMarginMeters = config.credibleDriveHopMarginMeters,
        minDistanceMeters = config.minimumTripDistanceMeters,
        maxRateMps = config.sustainedDepartureMaxRateMps,
        progressFraction = 0.5f,
        retentionSlackMs = 30_000L,
        maxRetainedFixes = 40,
    )

    /** Resting position, and the origin every case below is measured from. */
    private fun parked(at: Long = 0L) =
        GpsPoint(36.6119, -6.2805, accuracy = 8f, timestamp = at, speed = 0f)

    /** ~1.1 km north of [parked], at motorway speed: crosses both bars at once. */
    private fun drivingFarAway(at: Long, speed: Float = 20f) =
        GpsPoint(36.6219, -6.2805, accuracy = 8f, timestamp = at, speed = speed)

    private fun DetectionSessionState.reduce(fix: GpsPoint, nowMs: Long = fix.timestamp) = reduceFix(
        fix = fix,
        nowMs = nowMs,
        elapsedSinceArmMs = nowMs,
        departureAnchor = null,
        departureFenceRadiusMeters = 0f,
        bounds = bounds,
        config = config,
    )

    @Test
    fun should_announce_each_crossing_on_the_fix_that_crosses_it_and_never_again() {
        // The first fix only establishes the origin — there is nothing to measure movement from
        // yet, so it must cross nothing and say nothing.
        val armed = DetectionSessionState().reduce(parked())
        assertTrue(
            armed.notes.isEmpty(),
            "the fix that becomes the origin crosses nothing — was ${armed.notes.map { it.text }}",
        )

        val crossing = armed.state.reduce(drivingFarAway(at = 30_000L))
        assertTrue(crossing.state.session.driveAuthorized, "driving speed was reached")
        assertTrue(crossing.state.session.hasEverMoved, "…and the car is far from where it started")
        assertTrue(
            crossing.notes.any { it.text.contains("hasEverReachedDrivingSpeed → true") },
            "the speed crossing must be in the trace — was ${crossing.notes.map { it.text }}",
        )
        assertTrue(
            crossing.notes.any { it.text.contains("hasEverMoved → true") },
            "the movement crossing must be in the trace — was ${crossing.notes.map { it.text }}",
        )

        // The same conditions on the next fix are no longer crossings. This is the assertion the
        // old shape could not make, and the one a CAS retry used to be able to break.
        val after = crossing.state.reduce(drivingFarAway(at = 60_000L))
        assertTrue(after.state.session.driveAuthorized, "the flags stay set…")
        assertTrue(after.state.session.hasEverMoved)
        assertTrue(
            after.notes.none { it.text.contains("→ true") },
            "…and precisely because they were already set, nothing is announced again — " +
                "was ${after.notes.map { it.text }}",
        )
    }

    @Test
    fun should_stay_silent_when_the_fix_crosses_nothing() {
        val armed = DetectionSessionState().reduce(parked())
        val stillParked = armed.state.reduce(parked(at = 30_000L))

        assertEquals(
            emptyList(),
            stillParked.notes.map { it.text },
            "a car that has not moved has nothing to say",
        )
    }

    /**
     * A fix whose accuracy is not credible cannot cross the speed bar, however fast it claims to be
     * going [DET-SOLID-001]. Asserted here because the gate lives inside the reduction now: a
     * single degraded fix (walking, acc 80–200 m) used to flip the flag and unlock every confirm
     * path below it.
     */
    @Test
    fun should_refuse_a_crossing_claimed_by_a_fix_too_vague_to_be_believed() {
        val armed = DetectionSessionState().reduce(parked())
        val vague = drivingFarAway(at = 30_000L).copy(accuracy = config.minGpsAccuracyForDriving + 50f)

        val reduced = armed.state.reduce(vague)

        assertTrue(reduced.notes.isEmpty(), "was ${reduced.notes.map { it.text }}")
        assertTrue(!reduced.state.session.driveAuthorized, "a vague fix proves no driving speed")
        assertTrue(!reduced.state.session.hasEverMoved, "…and no movement either")
    }
}
