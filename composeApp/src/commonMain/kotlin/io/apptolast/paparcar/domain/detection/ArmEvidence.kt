package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.detection.state.DriveProofSource

/**
 * [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] What an arm's evidence entitles the session
 * to assume about the drive, DECLARED per arm rather than inferred from which subtypes an `is`
 * chain happened to list.
 *
 * The predecessor of this type was `isVerifiedDeparture = this is VerifiedBySpeed || this is
 * VerifiedByVehicleEnter` — membership by spelling, the exact accident
 * [io.apptolast.paparcar.domain.detection.physics.SessionOutcome] was typed to prevent. It also
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
     *  ([io.apptolast.paparcar.domain.model.ParkingDetectionConfig.btMinRideDurationMs]) for the
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

    /** Whether this evidence proves the departure — seeds `hasEverReachedDrivingSpeed` so the
     *  coordinator does not re-litigate a drive its stream structurally cannot observe. */
    val isVerifiedDeparture: Boolean
        get() = driveAuthorization != DriveAuthorization.None

    /** Stable label persisted on the session / logged in diagnostics. */
    val persistLabel: String
        get() = when (this) {
            is Manual -> LABEL_MANUAL
            is VerifiedBySpeed -> LABEL_VERIFIED_SPEED
            is VerifiedByVehicleEnter -> LABEL_VERIFIED_ENTER
            is Unverified -> LABEL_SELF_OBSERVED
            is BoardingAtCar -> LABEL_ENTER_AT_CAR
            is ArrivalHandoff -> LABEL_ARRIVAL_HANDOFF
            is BtRide -> LABEL_BT_RIDE
            is InheritedDrive -> LABEL_INHERITED_DRIVE
        }

    companion object {
        const val LABEL_MANUAL = "manual"
        const val LABEL_VERIFIED_SPEED = "verified_speed"
        const val LABEL_VERIFIED_ENTER = "verified_enter"
        /** [DET-AR-FIRST-001] Boarding caught at the car (fresh ENTER inside the own fence). */
        const val LABEL_ENTER_AT_CAR = "enter_at_car"
        /** A departure verdict confirmed AFTER the arm (worker upgrade). [DET-G-05] */
        const val LABEL_VERIFIED_LATE = "verified_late"
        /** No external verification — the coordinator's own stream is the only witness. */
        const val LABEL_SELF_OBSERVED = "self_observed"
        /** [DET-HANDOFF-NOT-MANUAL-001] The safety net's arrival handoff: a DEDUCED departure, never
         *  a witnessed drive and never the user's own word. */
        const val LABEL_ARRIVAL_HANDOFF = "arrival_handoff"
        /** [DET-BT-DISCONNECT-WITHOUT-RIDE-001] A ride-shaped Bluetooth engagement armed this BT
         *  session. The BT lane used to stamp NOTHING, so a field pin could not be traced to its
         *  trigger without pulling the device log over a cable. */
        const val LABEL_BT_RIDE = "bt_ride"
        /** [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] The drive proof of the session this
         *  one replaced, carried across the supersede. */
        const val LABEL_INHERITED_DRIVE = "inherited_drive"

        /**
         * Labels that bypass the repark-plausibility and assertion guards: the drive was externally
         * proven.
         *
         * [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] [LABEL_INHERITED_DRIVE] belongs here
         * and the reason is the whole ticket: those two guards exist to catch a session that never
         * saw a car, and they read the SUCCESSOR's own peak — which on the last hop of a trip is a
         * manoeuvring speed. Without the bypass the one arm that carries a MEASURED drive would be
         * the one they mistake for a pedestrian.
         */
        fun isVerifiedLabel(label: String?): Boolean =
            label == LABEL_VERIFIED_SPEED || label == LABEL_VERIFIED_ENTER ||
                label == LABEL_VERIFIED_LATE || label == LABEL_INHERITED_DRIVE
    }
}
