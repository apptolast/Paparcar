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
        }

    companion object {
        const val LABEL_MANUAL = "manual"
        const val LABEL_VERIFIED_SPEED = "verified_speed"
        const val LABEL_VERIFIED_ENTER = "verified_enter"
        /** A departure verdict confirmed AFTER the arm (worker upgrade). [DET-G-05] */
        const val LABEL_VERIFIED_LATE = "verified_late"
        /** No external verification — the coordinator's own stream is the only witness. */
        const val LABEL_SELF_OBSERVED = "self_observed"

        /** Labels that bypass the repark-plausibility guard: the drive was externally proven. */
        fun isVerifiedLabel(label: String?): Boolean =
            label == LABEL_VERIFIED_SPEED || label == LABEL_VERIFIED_ENTER || label == LABEL_VERIFIED_LATE
    }
}
