package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * WHICH strategy put this pin, at the only granularity the user is allowed to see.
 * [UI-HISTORY-IDENTITY-AND-SOURCE-001]
 *
 * `spotType` alone cannot answer this: `AUTO_DETECTED` collapses the two independent strategies
 * (deterministic Bluetooth, probabilistic Coordinator) into one word, and the history detail needs
 * to tell them apart — that is the first thing the user asks when a pin looks wrong.
 *
 * The answer is READ from provenance that was already decided and persisted elsewhere
 * ([UserParking.detectionPath], [DET-PIN-PROVENANCE-001]); nothing here decides anything, so this
 * is a pure top-level function and NOT a use case — same shape as [HumanPoweredRide] /
 * [SentryWakeCooldown]. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ⚠️ This used to warn that the coordinator's path labels were diagnostic jargon
 * (`"steps=3 kinematicFixes=7"`, `"motorBand=41000ms ≥8.0mps"`). **They are not, and never were.**
 * That jargon is a TRACE note — `FastConfirmStage`'s `"▶ steps+egress (steps=8 kinematicFixes=0)"` —
 * while `detectionPath` has only ever carried declared labels: three real accounts of parking
 * history read on 2026-08-30 hold `steps+egress`, `kinematic+egress`, `unattended_zone_gap_anchor`,
 * `user`, `manual`, `nudge`, `bt`, `safety_net_backfill`, `closed_approximate_pin` and nulls.
 * [DET-DETECTION-PATH-IS-A-TYPE-001] now states that set as a type, so the warning is not just
 * false, it is unreachable. What DOES hold is the second half: nothing here is ever shown to the
 * user — the copy speaks of tiers, never of mechanics.
 */
enum class ParkingDetectionSource {
    /** The deterministic BT-disconnect strategy — the "automatic" tier. */
    Bluetooth,

    /** The probabilistic Coordinator (geofence + activity recognition) — the "assisted" tier. */
    Assisted,

    /** The user placed or confirmed this pin by hand. */
    Manual,

    // ⛔ There used to be a `PrivateZone` member here, and it was not a source.
    // [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] It answered WHERE the car is,
    // in the enum that answers WHO put the pin, and being an enum it could only do that by
    // REPLACING the real answer: a park in the user's own garage stopped saying whether Bluetooth
    // or the coordinator had detected it — the exact question this type exists to answer. And
    // because it was derived from `spotType == HOME_GEOFENCE`, which `ConfirmParkingUseCase` only
    // assigns to AUTO_DETECTED pins, a pin placed BY HAND inside a private zone read "Manual" and
    // never mentioned the zone at all, though it carried `privateZoneId` all the same.
    //
    // Both axes are now reported, separately: the strategy here, the place (and the consequence —
    // that no plaza was published) on its own line in the history detail, resolved live from the
    // user's current zones.

    /**
     * Auto-detected, but the row predates provenance ([UserParking.detectionPath] is null). We do
     * NOT know which strategy detected it, so nothing is claimed: the copy stays the generic
     * "auto-detected" it already was. Asymmetric failure applies to what we tell the user too.
     */
    Unknown,
}

/** [ParkingDetectionSource] of this session — see the enum for the classification rules. */
fun UserParking.detectionSource(): ParkingDetectionSource =
    parkingDetectionSourceOf(spotType, detectionPath)

/**
 * Pure classifier behind [UserParking.detectionSource], split out so it can be tested (and reused)
 * without building a whole session.
 */
fun parkingDetectionSourceOf(
    spotType: SpotType,
    detectionPath: String?,
): ParkingDetectionSource = when (spotType) {
    SpotType.MANUAL_REPORT -> ParkingDetectionSource.Manual
    // [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] A park at home is still a park
    // SOMETHING detected: HOME_GEOFENCE says where the car is, never who found it, so it reads its
    // strategy off the path exactly like any other auto-detected pin.
    SpotType.HOME_GEOFENCE,
    // [DET-DETECTION-PATH-IS-A-TYPE-001] The path DECLARES its strategy; this no longer re-derives
    // it. What used to live here was a prefix guess and a fall-through:
    //
    //   detectionPath.startsWith("bt")     -> Bluetooth   // any future "bt…" path, by two letters
    //   detectionPath in USER_PLACED_PATHS -> Manual
    //   else                               -> Assisted    // …with `Unknown` sitting right there
    //
    // The `else` is the one that mattered: an unrecognised path was ATTRIBUTED to the Coordinator,
    // in the exact field a user opens to ask who placed a pin they did not expect. Now an unknown
    // label resolves to `Unknown`, which claims nothing — the same asymmetric-failure rule the
    // detection side obeys, applied to what we tell the user.
    SpotType.AUTO_DETECTED ->
        DetectionPath.ofLabel(detectionPath)?.source ?: ParkingDetectionSource.Unknown
}
