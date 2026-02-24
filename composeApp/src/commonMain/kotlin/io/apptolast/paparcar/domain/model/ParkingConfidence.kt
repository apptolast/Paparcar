package io.apptolast.paparcar.domain.model

sealed class ParkingConfidence {
    data object NotYet : ParkingConfidence()
    data object Low : ParkingConfidence()
    data class Medium(val score: Float) : ParkingConfidence()
    data class High(val score: Float) : ParkingConfidence()
}
