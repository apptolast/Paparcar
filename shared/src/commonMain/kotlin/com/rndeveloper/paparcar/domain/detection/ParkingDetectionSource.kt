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
 * ⚠️ The coordinator's own path labels are diagnostic jargon (`"steps=3 kinematicFixes=7"`,
 * `"motorBand=41000ms ≥8.0mps"`). They are CLASSIFIED here and never shown — user copy carries no
 * internal mechanics.
 */
enum class ParkingDetectionSource {
    /** The deterministic BT-disconnect strategy — the "automatic" tier. */
    Bluetooth,

    /** The probabilistic Coordinator (geofence + activity recognition) — the "assisted" tier. */
    Assisted,

    /** The user placed or confirmed this pin by hand. */
    Manual,

    /** Parked inside a private zone — publication is suppressed on departure. */
    PrivateZone,

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
    SpotType.HOME_GEOFENCE -> ParkingDetectionSource.PrivateZone
    SpotType.MANUAL_REPORT -> ParkingDetectionSource.Manual
    SpotType.AUTO_DETECTED -> when {
        detectionPath.isNullOrBlank() -> ParkingDetectionSource.Unknown
        // "bt" and "bt_timeout" — both are the Bluetooth strategy; the timeout leg differs only in
        // how much walk corroboration it got, which is a reliability question, not a "who" question.
        detectionPath.startsWith(BLUETOOTH_PATH_PREFIX) -> ParkingDetectionSource.Bluetooth
        // A pin the user placed answering a detection nudge keeps AUTO_DETECTED on purpose (so the
        // freed spot publishes with the auto TTL), but the hand that put it there was the user's.
        // [DET-NUDGE-PIN-PROVENANCE-001]
        detectionPath in USER_PLACED_PATHS -> ParkingDetectionSource.Manual
        else -> ParkingDetectionSource.Assisted
    }
}

/** `"bt"` (walk corroborated) and `"bt_timeout"` (walk watch expired) — see BluetoothParkingDetector. */
private const val BLUETOOTH_PATH_PREFIX = "bt"

/** The three user-ground-truth paths of SaveManualParkingUseCase. */
private val USER_PLACED_PATHS = setOf("manual", "user", "nudge")
