package io.apptolast.paparcar.domain.detection

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

    /** Whether this evidence proves the departure — seeds `hasEverReachedDrivingSpeed` so the
     *  coordinator does not re-litigate a drive its stream structurally cannot observe. */
    val isVerifiedDeparture: Boolean
        get() = this is VerifiedBySpeed || this is VerifiedByVehicleEnter

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

        /** Labels that bypass the repark-plausibility guard: the drive was externally proven. */
        fun isVerifiedLabel(label: String?): Boolean =
            label == LABEL_VERIFIED_SPEED || label == LABEL_VERIFIED_ENTER || label == LABEL_VERIFIED_LATE
    }
}
