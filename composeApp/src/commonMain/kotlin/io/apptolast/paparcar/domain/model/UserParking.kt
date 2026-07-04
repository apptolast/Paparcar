package io.apptolast.paparcar.domain.model

/**
 * Represents the user's own active or past parking session.
 *
 * Distinct from [Spot], which represents spots shared with other users.
 * Only one session should be active ([isActive] = true) at a time.
 *
 * [address] and [placeInfo] are enriched asynchronously after the session is saved
 * (via Overpass + geocoder). Both may be null for legacy records or if the network
 * was unavailable at parking time.
 *
 * Snapshot of the vehicle taken at confirmation time:
 * - [spotType]       – how the parking was detected / confirmed.
 * - [sizeCategory]   – length-based size of the vehicle (null = unknown legacy row).
 * - [carbodyType]    – body-shape of the vehicle (null for non-CAR or legacy rows).
 *
 * Both [sizeCategory] and [carbodyType] are propagated to the released [Spot]
 * on departure so the community fit indicator can match against other users.
 */
data class UserParking(
    val id: String,
    val userId: String = "",
    val vehicleId: String? = null,
    val location: GpsPoint,
    val spotId: String? = null,
    val geofenceId: String? = null,
    val isActive: Boolean = true,
    val address: AddressInfo? = null,
    val placeInfo: PlaceInfo? = null,
    /** Probability [0.0, 1.0] that this is a genuine parking event.
     *  1.0 = user manually confirmed; ~0.90 = vehicle-exit signal observed;
     *  ~0.75 = slow-path auto-detection only. */
    val detectionReliability: Float? = null,
    /** How the parking was detected / confirmed. Propagated to the released [Spot]. */
    val spotType: SpotType = SpotType.AUTO_DETECTED,
    /** Snapshot of the vehicle's length category at park time. Propagated to the released [Spot]. */
    val sizeCategory: VehicleSize? = null,
    /** Snapshot of the vehicle's body shape at park time. Null for motorcycles / scooters / bikes. */
    val carbodyType: CarbodyType? = null,
    /** Non-null when the session was parked inside a private zone — spot publication is suppressed on departure. */
    val privateZoneId: String? = null,
    /** Max GPS speed (m/s) observed during the detection session that confirmed this park.
     *  Local-only (never synced) — feeds the repark-plausibility guard: a replacement park whose
     *  session never reached driving speed is suspect. [DET-SOLID-001] */
    val tripMaxSpeedMps: Float? = null,
    /** Arm-evidence label of the confirming session ("speed", "vehicle_enter", "unverified",
     *  "manual"). Local-only diagnostics/guard input. [DET-SOLID-001] */
    val armEvidence: String? = null,
)
