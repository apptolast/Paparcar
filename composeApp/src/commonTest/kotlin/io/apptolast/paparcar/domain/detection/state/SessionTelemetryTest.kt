package io.apptolast.paparcar.domain.detection.state

import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.VehicleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [09 §5] The transitions of the first sub-state.
 *
 * Everything here was reachable only through the coordinator's 700-line collect loop until P2.1, so
 * the interesting claims — that the seed and its evidence label move TOGETHER, and that the
 * "keep driving" wipe preserves exactly the right set — had no test of their own. They do now.
 */
class SessionTelemetryTest {

    private fun fix(speed: Float = 0f, at: Long = 1_000L) =
        GpsPoint(36.7, -4.4, accuracy = 8f, timestamp = at, speed = speed)

    // ── The authorization and its evidence move together ──────────────────────

    /**
     * [DET-G-04] Armed mid-trip: authorized, but the flag that says it was never MEASURED is set in
     * the same transition. Without it a dismissed departure could not tell a lent seed from an
     * earned one.
     */
    @Test
    fun should_authorize_on_trust_when_the_arm_says_the_drive_already_happened() {
        val s = SessionTelemetry().seededOnArmTrust()
        assertTrue(s.driveAuthorized)
        assertTrue(s.authorizedOnArmTrustOnly)
    }

    /**
     * [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] The sibling seed: authorized because the
     * session this one replaced MEASURED the drive, on the same trip. Not on trust — the flag that
     * would make it retractable stays down.
     */
    @Test
    fun should_authorize_without_trust_when_the_drive_was_inherited_from_a_superseded_session() {
        val s = SessionTelemetry().seededOnInheritedDrive()
        assertTrue(s.driveAuthorized)
        assertFalse(s.authorizedOnArmTrustOnly)
    }

    /**
     * …and the difference between the two seeds is not decorative: `authorizedOnArmTrustOnly` is the
     * ONLY thing `notifyDepartureDismissed` consults before retracting, so a seed that sets it is a
     * seed a later verdict can take back. An inherited drive must not be one — the worker
     * adjudicates an EXIT and may take back what an EXIT lent, but it says nothing about a track
     * that was already observed.
     */
    @Test
    fun should_make_only_the_trusted_seed_retractable() {
        assertTrue(SessionTelemetry().seededOnArmTrust().authorizedOnArmTrustOnly)
        assertFalse(SessionTelemetry().seededOnInheritedDrive().authorizedOnArmTrustOnly)
    }

    /**
     * [DET-G-05] A departure the worker MEASURED. Three things move at once: the seed is granted,
     * it stops being retractable, and the evidence says `verified_late`. Splitting them is how the
     * session becomes readable as "authorized but self_observed" — a state that should not exist.
     */
    @Test
    fun should_move_seed_trust_and_evidence_together_when_a_departure_is_confirmed() {
        val s = SessionTelemetry().seededOnArmTrust().departureConfirmed()
        assertTrue(s.driveAuthorized)
        assertFalse(s.authorizedOnArmTrustOnly)
        assertEquals(ArmEvidence.LABEL_VERIFIED_LATE, s.armEvidence)
    }

    /** …and it grants the seed even to a session that never had one. */
    @Test
    fun should_grant_the_seed_when_a_departure_is_confirmed_on_an_unauthorized_session() {
        val s = SessionTelemetry().departureConfirmed()
        assertTrue(s.driveAuthorized)
        assertFalse(s.authorizedOnArmTrustOnly)
    }

    /**
     * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] The refuted departure takes back what the arm
     * lent — seed, trust flag and evidence label in one move, so every anti-walking guard re-arms
     * at the same instant the label stops claiming a verified exit.
     */
    @Test
    fun should_retract_seed_and_evidence_together_when_a_departure_is_dismissed() {
        val s = SessionTelemetry().armed(ArmEvidence.LABEL_VERIFIED_ENTER).seededOnArmTrust()
            .departureDismissed()
        assertFalse(s.driveAuthorized)
        assertFalse(s.authorizedOnArmTrustOnly)
        assertEquals(ArmEvidence.LABEL_SELF_OBSERVED, s.armEvidence)
    }

    /** [DET-SOLID-001] The enter-arm step veto: same pairing, different cause. */
    @Test
    fun should_degrade_evidence_and_unseed_together_on_the_enter_arm_step_veto() {
        val s = SessionTelemetry().armed(ArmEvidence.LABEL_VERIFIED_ENTER).seededOnArmTrust()
            .enterArmStepVeto()
        assertFalse(s.driveAuthorized)
        assertEquals(ArmEvidence.LABEL_SELF_OBSERVED, s.armEvidence)
    }

    // ── The per-fix bookkeeping ───────────────────────────────────────────────

    /** Origin and the first-fix clock are captured once and never overwritten. */
    @Test
    fun should_capture_the_origin_once_and_never_move_it() {
        val first = fix(at = 10L)
        val s = SessionTelemetry()
            .onFix(first, nowMs = 100L, reachedDrivingSpeed = false, moved = false, driveProven = false)
            .onFix(fix(at = 20L), nowMs = 200L, reachedDrivingSpeed = false, moved = false, driveProven = false)
        assertEquals(first, s.origin)
        assertEquals(100L, s.firstFixAtMs)
    }

    /** The authorization is monotone within a session: a slow fix never takes it back. */
    @Test
    fun should_never_let_a_slow_fix_take_the_authorization_back() {
        val s = SessionTelemetry()
            .onFix(fix(speed = 20f), 100L, reachedDrivingSpeed = true, moved = true, driveProven = false)
            .onFix(fix(speed = 0f), 200L, reachedDrivingSpeed = false, moved = false, driveProven = false)
        assertTrue(s.driveAuthorized)
        assertTrue(s.hasEverMoved)
    }

    /**
     * A trusted seed becomes permanent when the TRACK proves a drive — and **only** then. A lone
     * in-band fix must not settle it: a single mirage sample is exactly what granted the seed in
     * the first place, so it cannot also be what makes it unretractable.
     */
    @Test
    fun should_settle_a_trusted_seed_only_when_the_track_proves_the_drive() {
        val lentSeed = SessionTelemetry().seededOnArmTrust()

        val afterFastFix = lentSeed
            .onFix(fix(speed = 20f), 100L, reachedDrivingSpeed = true, moved = true, driveProven = false)
        assertTrue(afterFastFix.authorizedOnArmTrustOnly, "a fast fix alone must not settle the seed")

        val afterProof = afterFastFix
            .onFix(fix(speed = 20f), 200L, reachedDrivingSpeed = true, moved = true, driveProven = true)
        assertFalse(afterProof.authorizedOnArmTrustOnly)
    }

    /** The `loc#N` counter is its own transition so the number logged before the judgement stays put. */
    @Test
    fun should_count_fixes_independently_of_the_per_fix_judgement() {
        val s = SessionTelemetry().countFix().countFix()
        assertEquals(2, s.fixCount)
        assertNull(s.origin, "counting a fix must not judge it")
    }

    // ── What survives a wipe ──────────────────────────────────────────────────

    /**
     * "Keep driving" wipes the heuristics and keeps the session. The set is the whole point: this
     * used to be a hand-copied field list at the call site, and a field added later had to REMEMBER
     * to appear in it.
     */
    @Test
    fun should_keep_the_authorization_and_the_identity_when_the_user_keeps_driving() {
        val before = SessionTelemetry()
            .armed(ArmEvidence.LABEL_VERIFIED_ENTER)
            .seededOnArmTrust()
            .attributeVehicle("veh-1", VehicleType.CAR)
            .countFix()
            .onFix(fix(speed = 20f), 100L, reachedDrivingSpeed = true, moved = true, driveProven = false)
            .observed(fix(at = 50L))

        val after = before.keepingAuthorization()

        // Kept: the session is still this session, and it still drove.
        assertTrue(after.driveAuthorized)
        assertTrue(after.hasEverMoved)
        assertEquals(ArmEvidence.LABEL_VERIFIED_ENTER, after.armEvidence)
        assertEquals("veh-1", after.attributedVehicleId)
        assertEquals(VehicleType.CAR, after.attributedVehicleType)
        assertEquals(1, after.fixCount)

        // Dropped: every heuristic starts over.
        assertNull(after.origin)
        assertNull(after.firstFixAtMs)
        assertNull(after.previousFix)
        assertEquals(0f, after.lastSpeedMps)

        // ⚠️ Dropped ON PURPOSE, because it is dropped today: after a "keep driving" a lent seed
        // can no longer be retracted by a dismissal. A quirk, preserved — not a design.
        assertFalse(after.authorizedOnArmTrustOnly)
    }

    /** The user's stop wipes the reasoning but not who the session was. */
    @Test
    fun should_keep_only_the_identity_when_the_user_stops_the_session() {
        val after = SessionTelemetry()
            .armed(ArmEvidence.LABEL_VERIFIED_ENTER)
            .seededOnArmTrust()
            .attributeVehicle("veh-1", VehicleType.CAR)
            .keepingIdentity()

        assertEquals(ArmEvidence.LABEL_VERIFIED_ENTER, after.armEvidence)
        assertEquals("veh-1", after.attributedVehicleId)
        assertFalse(after.driveAuthorized, "a stopped session carries no authorization forward")
    }
}
