package io.apptolast.paparcar.domain.model

data class ParkingSignals(
    val speed: Float,
    val stoppedDurationMs: Long,
    val gpsAccuracy: Float,
    val activityExit: Boolean,
    val activityStill: Boolean = false,
)
