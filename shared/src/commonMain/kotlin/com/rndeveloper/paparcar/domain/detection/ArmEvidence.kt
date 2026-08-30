package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.state.DriveProofSource

/**
 * [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] What an arm's evidence entitles the session
 * to assume about the drive, DECLARED per arm rather than inferred from which subtypes an `is`
 * chain happened to list.
 *
 * The predecessor of this type was `isVerifiedDeparture = this is VerifiedBySpeed || this is
 * VerifiedByVehicleEnter` — membership by spelling, the exact accident
 * [com.rndeveloper.paparcar.domain.detection.physics.SessionOutcome] was typed to prevent. It also
 * could not express the third case at all, and the third case is the one the 25-08 field session
 * needed: a drive this session did not measure and did not borrow on trust, but INHERITED from the
 * session it replaced, which measured it.
 *
 * The difference between the last two is not cosmetic — it decides whether a `Dismissed` departure
 * verdict may retract the authorization. Trust may be taken back; a measurement may not.
 */
enum class DriveAuthorization {
    /** Nothing proved a drive at the arm. Every anti-walking guard stays armed and this session's
     *  own stream is expected to witness the drive itself. */
    None,

    /** The arm's word: a departure was verified elsewhere, so the session starts authorized but
     *  RETRACTABLE until one of its own measurements backs it. */
    OnTrust,

    /** A drive was MEASURED — by this trip, just not inside this session's own stream. Not
     *  retractable: no later adjudication can un-drive a track that was observed. */
    Measured,
}

/**
 * [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001] **The persisted vocabulary of the
 * arm**, and the only place that classifies it.
 *
 * ## Why this is not just [ArmEvidence]
 *
 * An arm carries measurements — a speed, an enter-to-exit gap, an engagement duration, an inherited
 * peak. The persisted session carries only the **word**. So the string cannot be parsed back into
 * [ArmEvidence] without inventing the numbers, and every door that receives a session instead of an
 * arm — `ConfirmParkingUseCase`, `EvaluateParkingDecisionUseCase` — used to answer its question by
 * spelling the words out again: two hand-kept lists of `label == LABEL_…`, mirroring two exhaustive
 * `when`s that lived a few lines above them.
 *
 * Nothing bound the mirrors. Both failed CLOSED, so a divergence would not have planted a phantom
 * pin — it would have asked where it should not have, quietly, forever. And the guardrail written to
 * forbid exactly this shape (`no hand-kept set of arm labels decides anything`) exempts
 * `ArmEvidence.kt`, which is where both lists lived: the exemption is legitimate for DECLARING the
 * words and was sheltering a DECISION made by comparing them.
 *
 * So the word gets its own total type. [ofPersisted] is the one parse, the two questions are
 * answered here once, and [ArmEvidence] delegates to its own [ArmEvidence.label] rather than
 * answering them a second time.
 *
 * ⚠️ [VERIFIED_LATE] is the reason this cannot be a property of the sealed hierarchy alone: it is a
 * label with no arm. `SessionTelemetry.departureConfirmed()` writes it when the departure worker
 * measures an exit AFTER the arm, so it exists only as a word on a live session.
 */
enum class ArmLabel(val persisted: String) {

    /** The user explicitly started detection — their own word. */
    MANUAL("manual"),

    /** A one-shot fix at departure speed witnessed the exit. */
    VERIFIED_SPEED("verified_speed"),

    /** A recent AR `IN_VEHICLE_ENTER` backs the exit. Fires on ANY vehicle. */
    VERIFIED_ENTER("verified_enter"),

    /** [DET-G-05] A departure the worker MEASURED after the arm — a post-arm upgrade, never an arm. */
    VERIFIED_LATE("verified_late"),

    /** No external verification: the coordinator's own stream is the only witness. */
    SELF_OBSERVED("self_observed"),

    /** [DET-AR-FIRST-001] A fresh AR ENTER inside the parked car's own fence — the boarding moment. */
    ENTER_AT_CAR("enter_at_car"),

    /** [DET-HANDOFF-NOT-MANUAL-001] The safety net DEDUCED a departure and handed the trip over. */
    ARRIVAL_HANDOFF("arrival_handoff"),

    /** [DET-BT-DISCONNECT-WITHOUT-RIDE-001] A ride-shaped Bluetooth engagement armed this session. */
    BT_RIDE("bt_ride"),

    /** [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] The drive proof of the superseded session. */
    INHERITED_DRIVE("inherited_drive"),
    ;

    /**
     * Whether the departure was proven OUTSIDE this session's own stream — the question the
     * repark-plausibility and assertion guards ask before they interrogate a confirm.
     *
     * Those guards exist to catch a session that never saw a car. An arm that carries external
     * proof is not that session, so it passes through. [VERIFIED_LATE] belongs here for the same
     * reason: the departure worker measured the exit, just later than the arm.
     */
    val isVerifiedDeparture: Boolean
        get() = when (this) {
            VERIFIED_SPEED, VERIFIED_ENTER, VERIFIED_LATE, INHERITED_DRIVE -> true
            MANUAL, SELF_OBSERVED, ENTER_AT_CAR, ARRIVAL_HANDOFF, BT_RIDE -> false
        }

    /**
     * [DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001] Whether this arm lets the session save a park in
     * SILENCE when its own stream never measured a drive. The reasoning per case is on
     * [ArmEvidence.confirmsSilentlyWithoutMeasuredDrive], which this answers for.
     *
     * [VERIFIED_LATE] answers `false` and is the one case with no sealed counterpart to inherit the
     * reasoning from: a late upgrade can rest on the same ENTER fall-through it was meant to
     * strengthen, and it must never override a prompt the policy already chose (field 2026-07-04).
     */
    val confirmsSilentlyWithoutMeasuredDrive: Boolean
        get() = when (this) {
            MANUAL, INHERITED_DRIVE, VERIFIED_SPEED -> true
            VERIFIED_ENTER, VERIFIED_LATE, SELF_OBSERVED,
            ENTER_AT_CAR, ARRIVAL_HANDOFF, BT_RIDE,
            -> false
        }

    companion object {
        /**
         * The persisted word back to its type, or **null when nothing recognises it** — the same
         * shape and the same failure direction as `DetectionPath.ofLabel`.
         *
         * ⛔ Fails CLOSED: an unknown or absent label is no evidence, and no evidence means the
         * guards stay armed and the policy asks. This is the ONE place a label string is compared,
         * and every door reads its answer instead of re-deriving one.
         */
        fun ofPersisted(label: String?): ArmLabel? =
            if (label.isNullOrBlank()) null else entries.firstOrNull { it.persisted == label }
    }
}

/**
 * Typed evidence behind a detection-session arm — what proved (or failed to prove) that the
 * vehicle actually drove before this session started looking for the next park.
 *
 * Replaces the `armedByConfirmedDeparture` boolean: each arm carries WHAT verified it, the
 * coordinator decides which confirm paths that evidence unlocks, the label is persisted on the
 * confirmed session (`UserParking.armEvidence`) and the repark-plausibility guard interrogates
 * it. [DET-SOLID-001]
 */
sealed interface ArmEvidence {

    /** The user explicitly started detection ("I'm driving"). The session's own GPS stream is
     *  expected to observe the drive — anti-walking guards stay active. */
    data object Manual : ArmEvidence

    /** A one-shot fix at departure speed (≥ `minimumDepartureSpeedKmh`) with credible accuracy
     *  witnessed the exit — the strongest automatic proof of a real drive-away. */
    data class VerifiedBySpeed(val speedKmh: Float, val accuracyM: Float?) : ArmEvidence

    /** A recent AR `IN_VEHICLE_ENTER` backs the exit. Weaker: AR fires on ANY vehicle (bus,
     *  taxi) — policy may degrade auto-confirm to a user prompt on this evidence alone. */
    data class VerifiedByVehicleEnter(val enterToExitMs: Long) : ArmEvidence

    /** No vehicle evidence at arm time (typical walking exit). Anti-walking guards stay
     *  active; the departure worker may upgrade the live session when late evidence lands. */
    data object Unverified : ArmEvidence

    /** [DET-HANDOFF-NOT-MANUAL-001] The safety net DEDUCED a departure (the phone left the parked
     *  car's neighbourhood) and handed the rest of the trip to live detection
     *  [DET-ARRIVAL-HANDOFF-001]. Nothing witnessed a drive — the deduction is about the PHONE, and
     *  the phone can leave on foot, on a bicycle or in someone else's car (field 2026-08-19: a
     *  bicycle ride). Weaker than [Manual] on purpose: it used to borrow that label and with it the
     *  right to confirm silently. Anti-walking guards stay active and auto-confirm waits for
     *  measured driving. */
    data object ArrivalHandoff : ArmEvidence

    /** [DET-AR-FIRST-001] A FRESH AR `IN_VEHICLE_ENTER` whose fix sits INSIDE the parked car's
     *  own fence — the boarding moment, caught BEFORE any driving exists to measure. Arms the
     *  coordinator "waiting for ride proof": deliberately NOT a verified departure (no seed),
     *  so the session must measure the drive itself and the anti-walking aborts stay armed —
     *  a spurious ENTER beside the car costs one false-ENTER/no-movement abort. */
    data object BoardingAtCar : ArmEvidence

    /** [DET-BT-DISCONNECT-WITHOUT-RIDE-001] The paired car stayed CONNECTED long enough
     *  ([com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig.btMinRideDurationMs]) for the
     *  disconnect to be the end of a ride rather than a car passing within radio range. Not a
     *  verified departure: it says the engagement was ride-shaped, not that driving was measured —
     *  the walk-away (or its timeout) still decides. Carries the engagement so field forensics can
     *  read the margin in Firestore instead of on a cable. */
    data class BtRide(val engagementMs: Long) : ArmEvidence

    /**
     * [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] This session REPLACED one that had already
     * PROVEN a drive with its own track, and inherits that proof.
     *
     * A supersede is not a new trip: it is the same trip changing which session follows it, because
     * a fresh trigger (a real AR `IN_VEHICLE ENTER` after a stop to refuel, a fence EXIT from an
     * intermediate stop) says the journey is not over. Field 2026-08-25 19:59:05: a 23-minute
     * session with 60 driving fixes and a 98 km/h peak was cancelled 10 ms before its replacement
     * armed; the replacement measured only the last 35 s of manoeuvring at ≤13,7 km/h, could not
     * reach driving speed, and the false-ENTER abort threw the whole park away. The drive had been
     * measured. It simply did not travel.
     *
     * [DriveAuthorization.Measured] and NOT [OnTrust]: nothing was lent here. The predecessor's
     * [DriveProofSource] is carried so a field trace can read WHICH proof was inherited rather than
     * inferring it from the peak.
     */
    data class InheritedDrive(val maxSpeedMps: Float, val source: DriveProofSource) : ArmEvidence

    /**
     * What this arm entitles the session to assume about the drive. DECLARED by each arm — a new
     * arm does not compile until its author answers.
     */
    val driveAuthorization: DriveAuthorization
        get() = when (this) {
            is VerifiedBySpeed, is VerifiedByVehicleEnter -> DriveAuthorization.OnTrust
            is InheritedDrive -> DriveAuthorization.Measured
            is Manual, is Unverified, is BoardingAtCar, is ArrivalHandoff, is BtRide -> DriveAuthorization.None
        }

    /**
     * Whether this evidence proves the departure — seeds `hasEverReachedDrivingSpeed` so the
     * coordinator does not re-litigate a drive its stream structurally cannot observe.
     *
     * [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001] Delegated to [label] rather
     * than restated as `driveAuthorization != None`, so the arm and the persisted session give the
     * same answer BY CONSTRUCTION instead of by two expressions that happen to agree today.
     * `ArmEvidenceTest` binds the two: for every arm, `driveAuthorization != None` must equal this.
     */
    val isVerifiedDeparture: Boolean
        get() = label.isVerifiedDeparture

    /**
     * [DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001] Whether this arm lets the session save a park in
     * SILENCE when its own stream never measured a drive. Two arms may, for two different reasons,
     * and both are the doctrine rather than an exception to it:
     *
     *  - [Manual] is the user's own word — an ASSERTION, and an inference never deposes an
     *    assertion [DET-ASSERTION-OUTRANKS-INFERENCE-001]. Note this is the ARM being manual, not a
     *    handoff wearing the label: [DET-HANDOFF-NOT-MANUAL-001] moved the safety net's deduced
     *    departure OUT of `manual` for exactly this reason.
     *  - [InheritedDrive] carries a drive that WAS measured, by the session this one superseded
     *    [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001].
     *  - [VerifiedBySpeed] carries a drive the departure worker MEASURED, outside this session's
     *    stream. The measurement is real even though this stream did not make it.
     *
     * Everything else asks — including [VerifiedByVehicleEnter], which shares
     * [DriveAuthorization.OnTrust] with [VerifiedBySpeed] and parts company here on the only
     * distinction that matters to this question: *verified by SPEED* is a measurement, *verified by
     * vehicle ENTER* is an event. Trust is a nomination, and the governing doctrine is that the
     * event nominates while only measured movement confirms.
     *
     * ## Why this is a `when` and not a set of labels
     *
     * The decision site used to re-derive this from a hand-kept `setOf(LABEL_…)` of WEAK labels —
     * an open set that enumerated the arms someone had already been burned by, so every new arm was
     * strong-by-default until the day it produced a false positive. [BoardingAtCar] was never on it:
     * field 2026-08-29 23:56, an `enter_at_car` arm whose log line says *"waiting for ride proof"*
     * silently pinned "La Parafarmacia" at reliability 0.9 after 29 m of net displacement, with
     * `DriveProof.proven == null` for the whole session. Stated as a `when` over the sealed
     * hierarchy, the set is CLOSED and a new arm does not compile until its author answers.
     *
     * [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001] That `when` now lives on
     * [ArmLabel], because the decision site receives the persisted WORD and not the arm. Answering
     * the same question in both places is how the mirror got written; this delegates instead.
     */
    val confirmsSilentlyWithoutMeasuredDrive: Boolean
        get() = label.confirmsSilentlyWithoutMeasuredDrive

    /**
     * [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001] Which word this arm persists
     * as. Declared per case: a new arm does not compile until its author picks one (or adds it).
     */
    val label: ArmLabel
        get() = when (this) {
            is Manual -> ArmLabel.MANUAL
            is VerifiedBySpeed -> ArmLabel.VERIFIED_SPEED
            is VerifiedByVehicleEnter -> ArmLabel.VERIFIED_ENTER
            is Unverified -> ArmLabel.SELF_OBSERVED
            is BoardingAtCar -> ArmLabel.ENTER_AT_CAR
            is ArrivalHandoff -> ArmLabel.ARRIVAL_HANDOFF
            is BtRide -> ArmLabel.BT_RIDE
            is InheritedDrive -> ArmLabel.INHERITED_DRIVE
        }

    /** Stable label persisted on the session / logged in diagnostics. */
    val persistLabel: String
        get() = label.persisted

    companion object {
        /**
         * [DET-AN-ARM-LABEL-IS-PARSED-ONCE-NOT-SPELLED-AT-EVERY-DOOR-001] Every arm this type can
         * build, for the tests that must not let a new one slip past the two questions.
         *
         * A list, and not `entries`, because the payload cases are not objects — the values here are
         * representative, and only the classification is asserted of them. It is closed by
         * `ArmEvidenceTest`, which requires it to cover every [ArmLabel] except
         * [ArmLabel.VERIFIED_LATE]: add a case, add a label, and the test fails until it is listed.
         */
        val allArms: List<ArmEvidence> = listOf(
            Manual,
            VerifiedBySpeed(speedKmh = 0f, accuracyM = null),
            VerifiedByVehicleEnter(enterToExitMs = 0L),
            Unverified,
            BoardingAtCar,
            ArrivalHandoff,
            BtRide(engagementMs = 0L),
            InheritedDrive(maxSpeedMps = 0f, source = DriveProofSource.TRACK_WINDOW),
        )
    }
}
