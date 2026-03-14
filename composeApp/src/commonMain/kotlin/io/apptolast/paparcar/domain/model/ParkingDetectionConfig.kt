package io.apptolast.paparcar.domain.model

/**
 * Configuration thresholds for the parking-detection algorithm.
 *
 * All values are provided with sensible defaults so the use case works
 * out of the box, while allowing overrides in tests or via remote config
 * without touching business logic.
 *
 * Injected into [CalculateParkingConfidenceUseCase] via Koin.
 */
data class ParkingDetectionConfig(

    // ── FAST PATH ─────────────────────────────────────────────────────────────
    /** Minimum stopped duration (ms) required to enter the fast path when an activity-exit event is present. */
    val fastPathMinStoppedMs: Long = 30_000L,
    /** Base confidence score granted by the activity-exit signal alone.
     *  0.50 lets the fast path reach High (0.75) when both speed and GPS-accuracy
     *  bonuses are present, auto-confirming without requiring user action. */
    val fastPathBaseScore: Float = 0.50f,
    /** Bonus added when speed is below [maxSpeedMps] in the fast path. */
    val fastPathSpeedBonus: Float = 0.15f,
    /** Bonus added when GPS accuracy is better than [minGpsAccuracyMeters] in the fast path. */
    val fastPathAccuracyBonus: Float = 0.10f,

    // ── SLOW PATH ─────────────────────────────────────────────────────────────
    /** Minimum stopped duration (ms) before the slow path starts scoring (filters traffic lights). */
    val slowPathGateMs: Long = 90_000L,
    /** Stopped duration threshold (ms) for the highest slow-path base score (~5 min). */
    val slowPath5MinMs: Long = 300_000L,
    /** Stopped duration threshold (ms) for the medium slow-path base score (~3 min). */
    val slowPath3MinMs: Long = 180_000L,
    /** Base score when stopped >= [slowPath5MinMs]. */
    val slowPath5MinScore: Float = 0.70f,
    /** Base score when stopped >= [slowPath3MinMs]. */
    val slowPath3MinScore: Float = 0.55f,
    /** Base score when stopped >= [slowPathGateMs] but below [slowPath3MinMs]. */
    val slowPathBaseScore: Float = 0.40f,
    /** Bonus added when the device reports a STILL activity in the slow path. */
    val stillBonus: Float = 0.10f,
    /** Bonus added when speed is below [maxSpeedMps] in the slow path. */
    val speedBonus: Float = 0.05f,
    /** Bonus added when GPS accuracy is better than [minGpsAccuracyMeters] in the slow path. */
    val accuracyBonus: Float = 0.05f,

    // ── SHARED THRESHOLDS ─────────────────────────────────────────────────────
    /** Score threshold (inclusive) to classify as [ParkingConfidence.High]. */
    val highConfidenceThreshold: Float = 0.75f,
    /** Score threshold (inclusive) to classify as [ParkingConfidence.Medium]. */
    val mediumConfidenceThreshold: Float = 0.55f,
    /** Speed (m/s) below which the vehicle is considered stationary for bonus calculation. */
    val maxSpeedMps: Float = 0.3f,
    /** GPS horizontal accuracy (meters) below which the fix is considered high-quality. */
    val minGpsAccuracyMeters: Float = 15f,

    // ── GEOFENCE ──────────────────────────────────────────────────────────────
    /** Radius (meters) of the geofence registered around the confirmed parking spot. */
    val geofenceRadiusMeters: Float = 80f,

    // ── LOCATION CAPTURE WINDOW ───────────────────────────────────────────────
    /** Time window (ms) after the vehicle first stops during which GPS fixes are
     *  collected into [stoppedFixes]. Fixes outside this window are ignored so that
     *  locations recorded AFTER the user has walked away from the car are not used
     *  as the saved parking spot. At HIGH_ACCURACY (2 s interval) a 30 s window
     *  yields ~15 candidate fixes — enough to select the best accuracy. */
    val initialStopWindowMs: Long = 30_000L,

    // ── FALSE-POSITIVE GUARD ──────────────────────────────────────────────────
    /** Minimum GPS speed (m/s) that must be reached at least once during a driving session
     *  before parking detection is allowed. Guards against spurious [IN_VEHICLE_ENTER]
     *  events fired when the user is stationary or cycling (e.g. sitting at a desk, parked
     *  car, or casual cycling). A genuine driving session will always exceed this threshold.
     *  Default ≈ 18 km/h — above comfortable cycling speed, below urban car speed. */
    val minimumTripSpeedMps: Float = 5f,
    /** Minimum displacement (metres) from the session-start location required — together with
     *  [minimumTripSpeedMps] — before [hasEverMoved] is set. A single GPS-noise speed spike
     *  while the device is stationary cannot satisfy both conditions simultaneously: even a
     *  large position jump (50 m) is well below this threshold, so spurious IN_VEHICLE_ENTER
     *  events at home are silently ignored. Default 150 m. */
    val minimumTripDistanceMeters: Float = 150f,
    /** Maximum duration (ms) to wait for [hasEverMoved] before aborting the session.
     *  If the device shows no real driving movement within this window the session is
     *  treated as a spurious IN_VEHICLE_ENTER (e.g. batched/delayed delivery while the
     *  user was already parked) and detection ends silently.
     *  Default 4 minutes — enough for a slow GPS warm-up but not long enough to drain
     *  battery on false starts. */
    val maxNoMovementMs: Long = 4 * 60_000L,

    // ── DEPARTURE DETECTION ───────────────────────────────────────────────────
    /** Maximum time (ms) between an IN_VEHICLE_ENTER transition and a GEOFENCE_EXIT for
     *  the departure to be considered intentional. 30 minutes covers the common case of
     *  a user sitting in their car (loading bags, finishing a call, waiting for AC) before
     *  driving away. The previous 5-minute default was too tight and silently rejected
     *  legitimate departures. A 30-minute window still correctly rejects the previous
     *  day's IN_VEHICLE signal (24+ hours >> 30 min). */
    val vehicleEnterWindowMs: Long = 30 * 60 * 1_000L,
    /** Minimum speed (km/h) that confirms the user is driving away. Speed check is skipped
     *  when GPS is unavailable. Default 10 km/h. */
    val minimumDepartureSpeedKmh: Float = 10f,
) {
    init {
        require(highConfidenceThreshold in 0f..1f) {
            "highConfidenceThreshold must be in 0..1, was $highConfidenceThreshold"
        }
        require(mediumConfidenceThreshold in 0f..highConfidenceThreshold) {
            "mediumConfidenceThreshold must be in 0..highConfidenceThreshold, was $mediumConfidenceThreshold"
        }
        require(fastPathMinStoppedMs > 0) {
            "fastPathMinStoppedMs must be > 0, was $fastPathMinStoppedMs"
        }
        require(slowPathGateMs > fastPathMinStoppedMs) {
            "slowPathGateMs ($slowPathGateMs) must be > fastPathMinStoppedMs ($fastPathMinStoppedMs)"
        }
        require(geofenceRadiusMeters > 0) {
            "geofenceRadiusMeters must be > 0, was $geofenceRadiusMeters"
        }
        require(vehicleEnterWindowMs > 0) {
            "vehicleEnterWindowMs must be > 0, was $vehicleEnterWindowMs"
        }
        require(minimumTripSpeedMps > 0) {
            "minimumTripSpeedMps must be > 0, was $minimumTripSpeedMps"
        }
        require(minimumTripDistanceMeters > 0) {
            "minimumTripDistanceMeters must be > 0, was $minimumTripDistanceMeters"
        }
        require(maxNoMovementMs > 0) {
            "maxNoMovementMs must be > 0, was $maxNoMovementMs"
        }
        require(minimumDepartureSpeedKmh > 0) {
            "minimumDepartureSpeedKmh must be > 0, was $minimumDepartureSpeedKmh"
        }
        require(initialStopWindowMs > 0) {
            "initialStopWindowMs must be > 0, was $initialStopWindowMs"
        }
    }
}
