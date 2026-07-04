package io.apptolast.paparcar.domain.model

/**
 * Sensor snapshot consumed by the confidence scorer. [DET-SOLID-001][C1] `activityStill` was
 * removed: STILL was dropped as a fed signal long ago (redundant with the egress gate, fires in
 * traffic jams), the field was hardwired `false` in production, and the scorer branches it fed
 * were unreachable — tests against them were fake coverage. Do not re-add STILL.
 */
data class ParkingSignals(
    val speed: Float,
    val stoppedDurationMs: Long,
    val gpsAccuracy: Float,
    val activityExit: Boolean,
)
