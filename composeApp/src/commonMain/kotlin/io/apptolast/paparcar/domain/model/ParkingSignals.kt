package io.apptolast.paparcar.domain.model

data class ParkingSignals(
    val speed: Float,
    val stoppedDurationMs: Long,
    val gpsAccuracy: Float,
    val activityExit: Boolean,
    // [DET-D-03] STILL was dropped as a fed signal (redundant with the egress gate, fires in traffic
    // jams). This is always false in production now; the scorer's still-bonus paths are inert pending
    // the D-03c scorer→metadata rework. Do not re-feed STILL here.
    val activityStill: Boolean = false,
)
