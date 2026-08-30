package com.rndeveloper.paparcar.domain.detection.sentry

import com.rndeveloper.paparcar.domain.detection.ArmLabel
import com.rndeveloper.paparcar.domain.detection.DriveAuthorization

import com.rndeveloper.paparcar.domain.detection.ArmEvidence

import com.rndeveloper.paparcar.domain.detection.state.DriveProof
import com.rndeveloper.paparcar.domain.detection.state.DriveProofSource
import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [DET-SUPERSEDE-001] Pure supersede-vs-suppress decision for a trigger that arrives while a
 *  detection job is already running. */
class SessionSupersedeTest {

    private fun p(lat: Double, lon: Double = -3.7, acc: Float = 10f) =
        GpsPoint(latitude = lat, longitude = lon, accuracy = acc, timestamp = 0L, speed = 0f)

    @Test
    fun should_supersede_when_new_park_is_beyond_the_fence_from_the_running_anchor() {
        // ~222 m apart (0.002° lat); fence 80 m + acc 10 m = 90 m boundary → a different place →
        // the running session is a zombie relative to it, supersede (caso WA YUKI ~100 m del FP).
        assertTrue(
            shouldSupersedeRunningSession(
                newParkLocation = p(40.002),
                runningAnchor = p(40.0),
                newFenceRadiusMeters = 80f,
            ),
        )
    }

    @Test
    fun should_suppress_when_new_trigger_is_within_the_fence() {
        // ~33 m apart (0.0003° lat), below the 90 m boundary → same place → keep suppressing so a
        // running session's own stream can't reset its abort timer [DET-AR-REARM-001].
        assertFalse(
            shouldSupersedeRunningSession(
                newParkLocation = p(40.0003),
                runningAnchor = p(40.0),
                newFenceRadiusMeters = 80f,
            ),
        )
    }

    @Test
    fun should_never_supersede_when_the_running_anchor_is_unknown() {
        assertFalse(
            shouldSupersedeRunningSession(
                newParkLocation = p(40.002),
                runningAnchor = null,
                newFenceRadiusMeters = 80f,
            ),
        )
    }

    // ── What the successor inherits [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] ──────────

    @Test
    fun should_hand_the_measured_drive_to_the_successor_when_the_superseded_session_proved_one() {
        // Field 2026-08-25 19:59:05: 27,2 m/s ≈ 98 km/h proven across a 23-minute track.
        val proven = DriveProof(
            proven = DriveProofSource.TRACK_WINDOW,
            provenMaxSpeedMps = 27.2f,
            peakMps = 27.2f,
        )

        val inherited = inheritedArmEvidence(proven)

        assertEquals(ArmEvidence.InheritedDrive(27.2f, DriveProofSource.TRACK_WINDOW), inherited)
    }

    @Test
    fun should_keep_the_provenance_of_the_proof_that_was_inherited() {
        val hop = DriveProof(
            proven = DriveProofSource.SHORT_HOP,
            provenMaxSpeedMps = 8.3f,
            peakMps = 8.3f,
        )

        assertEquals(DriveProofSource.SHORT_HOP, inheritedArmEvidence(hop)?.source)
    }

    @Test
    fun should_inherit_nothing_when_the_superseded_session_never_proved_a_drive() {
        assertNull(inheritedArmEvidence(DriveProof()))
    }

    /**
     * The laundering case, and the reason only [DriveProof.proven] qualifies. A session can hold a
     * high peak and a full look-back ring without the track ever corroborating a drive — that is
     * exactly the indoor Doppler mirage of field 2026-07-27. Inheriting on the peak would let each
     * supersede hand the next session a drive nobody ever measured.
     */
    @Test
    fun should_inherit_nothing_from_an_unproven_peak() {
        val mirage = DriveProof(
            proven = null,
            provenMaxSpeedMps = 0f,
            peakMps = 45f,
            credibleFixCount = 1,
        )

        assertNull(inheritedArmEvidence(mirage))
    }

    /** An inherited drive is MEASURED, so no later verdict may retract it — unlike an arm's word. */
    @Test
    fun should_declare_an_inherited_drive_measured_rather_than_lent_on_trust() {
        val inherited = ArmEvidence.InheritedDrive(27.2f, DriveProofSource.TRACK_WINDOW)

        assertEquals(DriveAuthorization.Measured, inherited.driveAuthorization)
        assertEquals(DriveAuthorization.OnTrust, ArmEvidence.VerifiedBySpeed(90f, 5f).driveAuthorization)
        assertEquals(DriveAuthorization.None, ArmEvidence.BoardingAtCar.driveAuthorization)
    }

    /**
     * The two guards in `ConfirmParkingUseCase` read the SUCCESSOR's own peak, which on the last hop
     * of a trip is a manoeuvring speed. Without this the one arm carrying a measured drive would be
     * the one they mistake for a pedestrian re-park.
     */
    @Test
    fun should_let_an_inherited_drive_pass_the_guards_that_exempt_verified_arms() {
        assertTrue(ArmLabel.INHERITED_DRIVE.isVerifiedDeparture)
        // [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001] …asked of the ARM and of
        // the WORD it persists as, because the guards receive whichever of the two is at hand and
        // both must answer the same. They cannot disagree now: the arm delegates to its label.
        assertTrue(ArmEvidence.InheritedDrive(27.2f, DriveProofSource.TRACK_WINDOW).isVerifiedDeparture)
        assertTrue(
            ArmLabel.ofPersisted(
                ArmEvidence.InheritedDrive(27.2f, DriveProofSource.TRACK_WINDOW).persistLabel,
            )!!.isVerifiedDeparture,
        )
        // …and the arms that prove nothing still do not.
        assertFalse(ArmEvidence.BoardingAtCar.isVerifiedDeparture)
        assertFalse(ArmEvidence.Unverified.isVerifiedDeparture)
    }
}
