package io.apptolast.paparcar.domain.detection.state

import io.apptolast.paparcar.domain.detection.physics.DriveProofBounds
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-SHORT-HOP-PROOF-001] The SHORT-HOP profile of the drive verifier, absorbed from
 * `EvaluateShortHopDriveProofUseCase` in P2.4 — it was a PREDICATE feeding exactly one verdict, so
 * per [DET-VERDICT-NOT-PREDICATE-001] it belongs inside that verdict rather than as an injected
 * class of its own.
 *
 * **Every assertion below is the one it had before the move.** A merge that quietly sheds coverage
 * is the failure mode this file exists to rule out — the same reason its own last two tests were
 * ported here from `EvaluateMeasuredDepartureUseCaseTest` when THAT unit was absorbed.
 */
class DriveProofShortHopTest {

    private val config = ParkingDetectionConfig()

    /** The window shape the coordinator builds; the short-hop profile never reads it, but `onFix`
     *  needs one and an empty ring makes the track proof unreachable — so a hop proves a hop. */
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

    /** The pin the car left. */
    private val pin = GpsPoint(36.6119, -6.2805, accuracy = 8f, timestamp = 0L, speed = 0f)

    /** ~900 m north of [pin] — the field hop (2026-08-14 22:56). 0.0081° lat ≈ 900 m. The default
     *  speed is a real driving one: [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001] made the run that carries
     *  the proof measure driving fix by fix, so a fixture standing still can no longer stand in for
     *  a hop. */
    private fun fixAway(
        accuracy: Float = 10f,
        degreesNorth: Double = 0.0081,
        speedMps: Float = 8.3f,
    ) = GpsPoint(pin.latitude + degreesNorth, pin.longitude, accuracy, 0L, speedMps)

    /** Feeds ONE fix into a proof that already carries `run - 1` qualifying fixes, so this fix is
     *  the one that reaches the bar — exactly what the old `invoke(…, consecutiveQualifyingFixes)`
     *  expressed. */
    private fun proof(
        anchor: GpsPoint? = pin,
        fix: GpsPoint = fixAway(),
        fenceRadius: Float = 80f,
        elapsedMs: Long = 3 * 60_000L,
        run: Int = config.shortHopProofFixes,
    ) = DriveProof(shortHopRun = run - 1).onFix(
        fix = fix,
        nowMs = 0L,
        credibleSpeedFix = true,
        departureAnchor = anchor,
        departureFenceRadiusMeters = fenceRadius,
        elapsedSinceArmMs = elapsedMs,
        bounds = bounds,
        config = config,
    ).proven == DriveProofSource.SHORT_HOP

    @Test
    fun `should prove the drive when the car ended far from its pin faster than legs allow`() {
        // Field 2026-08-14 22:56 (Oppo): verified exit, ~900 m in ~3 min, peak 30 km/h — the
        // speed-window proof never fired (`drive 3/303`) and the park was lost.
        assertTrue(proof(), "900 m in 3 min from the pin is measured driving [DET-SHORT-HOP-PROOF-001]")
    }

    @Test
    fun `should not prove anything without a pin to measure from`() {
        assertFalse(proof(anchor = null), "no origin pin → no displacement reference")
    }

    @Test
    fun `should not prove the drive when the distance is walkable in the elapsed time`() {
        // Same 900 m but the session has been running 16 min — legs cover that comfortably, so the
        // displacement proves nothing on its own.
        assertFalse(proof(elapsedMs = 16 * 60_000L))
    }

    @Test
    fun `should not prove the drive from a single qualifying fix`() {
        // A cache teleport can produce one far fix; it cannot produce a run of them.
        assertFalse(proof(run = 1))
    }

    @Test
    fun `should not prove the drive from a run of fixes that merely SIT far from the pin`() {
        // Field 2026-08-16 23:52 (Oppo, session 1786917152243). The user WALKED ~990 m from the
        // previous pin; a sentry-wake then armed the session with all that distance already banked,
        // so `elapsedSinceArmMs` started at zero and the pedestrian bound certified the walk as
        // unwalkable. Three consecutive stationary fixes at that distance passed every geometric
        // clause and handed the session a drive proof, which unlocked `maxSpeedMps` off one
        // cold-start Doppler spike and let 220 walking steps pin the beach.
        // [DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001]
        val standingStill = fixAway(accuracy = 11.5f, speedMps = 0.6f)

        assertFalse(
            proof(fix = standingStill),
            "distance from the pin says where the car ENDED UP, not that this session watched it get there",
        )
    }

    @Test
    fun `should prove the drive when the far fixes are actually moving`() {
        // The counterpart the guard must not touch: same geometry, but the run measures driving.
        assertTrue(proof(fix = fixAway(speedMps = 7.1f)), "a real hop reports real speed")
    }

    @Test
    fun `should not prove the drive from a degraded fix`() {
        val degraded = fixAway(accuracy = config.minGpsAccuracyForDriving + 1f)
        assertFalse(proof(fix = degraded), "a fix too imprecise to trust measures nothing")
    }

    @Test
    fun `should not prove the drive from indoor drift next to the pin`() {
        // The Doppler-mirage class (field 2026-07-27): the phone never left its own pin, whatever
        // speed the chipset claimed. ~55 m of drift.
        val drift = fixAway(degreesNorth = 0.0005)
        assertFalse(proof(fix = drift), "drift within metres of the pin can never prove a drive")
    }

    @Test
    fun `should not prove the drive just below the displacement floor`() {
        // ~330 m — real movement, but under the floor: the proof stays conservative (asymmetric
        // failure), and the speed-based proof still covers ordinary drives.
        val justUnder = fixAway(degreesNorth = 0.003)
        assertFalse(proof(fix = justUnder))
    }

    @Test
    fun `should expose the per-fix test so the run can be kept one fix at a time`() {
        // The geometric test feeds the run counter one fix at a time; the run length is what turns
        // it into a proof. Internal rather than public: directly testable, no injected ceremony.
        assertTrue(
            DriveProof().shortHopQualifies(
                fix = fixAway(),
                departureAnchor = pin,
                fenceRadiusMeters = 80f,
                elapsedSinceArmMs = 3 * 60_000L,
                config = config,
            ),
        )
    }

    // ── Absorbed from EvaluateMeasuredDepartureUseCaseTest ──────────────────────────────────
    // [DET-VERDICT-NOT-PREDICATE-001] That use case was deleted once the speed requirement made it a
    // strict subset of this profile. Its two assertions with no equivalent here were ported rather
    // than dropped: merging units must not quietly shed coverage. Same rule applied again in P2.4.

    @Test
    fun `should not prove the drive while still inside the fence it left`() {
        // The user could have been anywhere inside the fence when the clock started, so the radius
        // counts in favour of "walkable". A huge fence swallows the whole displacement.
        assertFalse(
            proof(fenceRadius = 2_000f),
            "displacement that does not clear the fence plus both envelopes proves nothing",
        )
    }

    @Test
    fun `should not prove the drive from a negative elapsed time`() {
        // Clock skew / a fix stamped before the arm: fail closed rather than divide by a bad window.
        assertFalse(proof(elapsedMs = -1L))
    }
}
