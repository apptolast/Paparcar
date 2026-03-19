package io.apptolast.paparcar.presentation.util

/**
 * Presentation-layer mapping of [UserParking.detectionReliability] (a raw Float)
 * to a user-facing confidence level.
 *
 * Never expose the raw Float to composables. Always map via [Float.toParkingReliabilityLevel]
 * at the ViewModel boundary.
 */
enum class ParkingReliabilityLevel {
    /** User manually confirmed the parking spot. Ground truth. */
    Confirmed,
    /** Parking was auto-detected with strong signal (vehicle-exit activity transition). */
    High,
    /** Parking was auto-detected by the slow path only (time-based, no activity signal). */
    Auto,
}

fun Float.toParkingReliabilityLevel(): ParkingReliabilityLevel = when {
    this >= 1.0f  -> ParkingReliabilityLevel.Confirmed
    this >= 0.85f -> ParkingReliabilityLevel.High
    else          -> ParkingReliabilityLevel.Auto
}
