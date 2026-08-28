package io.apptolast.paparcar.presentation.vehicles

/**
 * Aggregate insights over a vehicle's ENDED sessions, in USER terms — what the car did, never how
 * confident the detector felt. Each field is null (or absent from the UI) below its significance
 * threshold: a metric without enough data is not shown, not shown as "0"/"—".
 * [VEH-STATS-SAY-SOMETHING-USEFUL-001]
 */
data class HistoryStatsData(
    val mostActiveDayOfWeek: Int?,     // isoDayNumber 1=Mon..7=Sun, null if < 5 ended sessions
    val favoriteStreet: String?,       // null if no street reaches 3 sessions
    val spotsReleasedCount: Int,       // closes that gave the community a spot — 0 is information
    val autoDetected: AutoDetectedShare?, // null below 5 provenance-known ended sessions
)

/** "X of Y parkings detected automatically" — Y counts only sessions whose provenance is known
 *  ([io.apptolast.paparcar.domain.detection.ParkingDetectionSource] != Unknown), so legacy rows
 *  can't drag the ratio down. */
data class AutoDetectedShare(val auto: Int, val known: Int)
