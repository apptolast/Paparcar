package com.rndeveloper.paparcar.domain.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001]
 *
 * The arm existed as two things that had to agree — a sealed hierarchy with the payload, and a
 * persisted word — and nothing bound them. These are the bindings.
 */
class ArmLabelTest {

    @Test
    fun should_roundTripEveryLabel_through_ofPersisted() {
        ArmLabel.entries.forEach { label ->
            assertSame(label, ArmLabel.ofPersisted(label.persisted), "label=${label.persisted}")
        }
    }

    @Test
    fun should_haveUniqueWords() {
        // Two labels sharing a word would make `ofPersisted` answer by declaration order.
        val words = ArmLabel.entries.map { it.persisted }
        assertEquals(words.size, words.toSet().size, "duplicated words in $words")
    }

    @Test
    fun should_failClosed_when_theWordIsUnknown_or_absent() {
        // ⛔ The direction that matters. An unrecognised arm is the least trustworthy thing the
        // decision sites can be handed: null means "no evidence", and no evidence asks.
        assertNull(ArmLabel.ofPersisted("an_arm_invented_after_this_ticket"))
        assertNull(ArmLabel.ofPersisted(null))
        assertNull(ArmLabel.ofPersisted(""))
        assertNull(ArmLabel.ofPersisted("   "))
    }

    /**
     * The binding that replaces the mirror: an arm and the word it persists as must give the SAME
     * answer, because the two decision sites receive whichever of the two is at hand.
     *
     * `driveAuthorization` stays a `when` over the sealed hierarchy — it distinguishes trust from
     * measurement, which no label needs — so this asserts the one thing both must agree on.
     */
    @Test
    fun should_agree_between_theArm_and_itsWord_on_verifiedDeparture() {
        ArmEvidence.allArms.forEach { arm ->
            assertEquals(
                arm.driveAuthorization != DriveAuthorization.None,
                arm.label.isVerifiedDeparture,
                "${arm.label.persisted}: driveAuthorization=${arm.driveAuthorization} " +
                    "disagrees with the word's own answer",
            )
        }
    }

    /**
     * [DET-A-VERIFIED-BOARDING-MUST-REACH-THE-VETO-001] **Which arms may assert that a boarding was
     * witnessed** — enumerated, so a new arm cannot become one by omission (the
     * `VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001` failure mode, on a different type).
     *
     * The list is the assertion: adding an arm that answers non-null without touching this test is
     * exactly what must not compile past review.
     */
    @Test
    fun should_attestABoarding_only_when_theArmActuallyWitnessedOne() {
        val armedAt = 1_000_000L
        val attesting = ArmEvidence.allArms
            .filter { it.boardingWitnessedAtMs(armedAt) != null }
            .map { it.label }
            .toSet()

        assertEquals(
            setOf(ArmLabel.VERIFIED_ENTER, ArmLabel.ENTER_AT_CAR),
            attesting,
            "the set of arms that may revoke the pedal-cadence veto changed. Each one has to have " +
                "WITNESSED an AR IN_VEHICLE boarding AND carry its instant — see the KDoc on " +
                "ArmEvidence.boardingWitnessedAtMs before widening this",
        )
    }

    /**
     * ⛔ The one that costs a CAR if it ever flips. Field 2026-08-16: a 59-minute BICYCLE ride to Los
     * Toruños was sealed `verified_speed` — a bicycle clears the 10 km/h departure bar without effort
     * — broke the car's own geofence at 352 m and re-pinned a Mercedes 4,8 km away while the real car
     * sat in Calle Toledo. If a speed-verified arm could assert a boarding, that ride would receive
     * the refutation the human-powered veto exists to withhold from it.
     *
     * Asymmetric failure is what decides the direction: the loss this ticket repairs costs a spot,
     * this one costs a car. Spelled as its own test rather than left implicit in the set above,
     * because the set can be widened by someone who never reads the field record.
     */
    @Test
    fun should_neverAttestABoarding_when_theDepartureWasProvenOnlyBySpeed() {
        assertNull(
            ArmEvidence.VerifiedBySpeed(speedKmh = 38f, accuracyM = 6f).boardingWitnessedAtMs(1L),
            "a bicycle clears the departure speed bar — this door stays shut",
        )
        assertNull(
            ArmEvidence.BtRide(engagementMs = 600_000L).boardingWitnessedAtMs(1L),
            "and the Bluetooth lane never feeds the Coordinator's reasoning",
        )
    }

    /**
     * The arithmetic, stated: `verified_enter` carries the GAP the verifier measured to the boarding,
     * so the instant is the arm's own clock minus that gap. `enter_at_car` carries the instant itself
     * and must ignore the arm's clock entirely — two different shapes answering one question.
     */
    @Test
    fun should_dateTheBoarding_from_whicheverQuantityTheArmCarries() {
        assertEquals(
            940_000L,
            ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = 60_000L)
                .boardingWitnessedAtMs(armedAtMs = 1_000_000L),
        )
        assertEquals(
            777L,
            ArmEvidence.BoardingAtCar(enterTrueTimeMs = 777L)
                .boardingWitnessedAtMs(armedAtMs = 1_000_000L),
            "an absolute instant must not be re-derived from the arm's clock",
        )
    }

    /**
     * The population witness. `allArms` is hand-listed (the payload cases are not objects), so this
     * is what stops it silently covering less than the type does: every word must be produced by an
     * arm, except the one that has none by construction.
     */
    @Test
    fun should_cover_everyWord_with_anArm_except_theOne_thatHasNoArm() {
        val produced = ArmEvidence.allArms.map { it.label }.toSet()
        assertEquals(
            ArmLabel.entries.toSet() - ArmLabel.VERIFIED_LATE,
            produced,
            "an arm was added without being listed in ArmEvidence.allArms, or a word has no arm",
        )
        assertTrue(produced.size >= 8, "the population shrank: ${produced.size} arms")
    }

    /**
     * [DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001] survives the move. This is the field verdict of
     * 2026-08-29 23:56 restated on the type that now answers it: `enter_at_car` does not save in
     * silence, and neither does the label with no arm.
     */
    @Test
    fun should_keep_theSilentConfirmSet_closed() {
        val silent = ArmLabel.entries.filter { it.confirmsSilentlyWithoutMeasuredDrive }.toSet()
        assertEquals(
            setOf(ArmLabel.MANUAL, ArmLabel.INHERITED_DRIVE, ArmLabel.VERIFIED_SPEED),
            silent,
            "the set of arms that may pin without a measured drive changed",
        )
    }

    /** The other closed set, the one the repark and assertion guards read. */
    @Test
    fun should_keep_theVerifiedDepartureSet_closed() {
        val verified = ArmLabel.entries.filter { it.isVerifiedDeparture }.toSet()
        assertEquals(
            setOf(
                ArmLabel.VERIFIED_SPEED,
                ArmLabel.VERIFIED_ENTER,
                ArmLabel.VERIFIED_LATE,
                ArmLabel.INHERITED_DRIVE,
                // [DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001] The departure worker's own
                // echo-gated measurement — the same fact VERIFIED_LATE names, delivered to a
                // follower arm instead of to a live session.
                ArmLabel.DEPARTURE_FOLLOWED,
            ),
            verified,
            "the set of arms that bypass the repark and assertion guards changed",
        )
    }
}
