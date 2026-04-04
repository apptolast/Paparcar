package io.apptolast.paparcar.presentation.map

sealed class ParkingLocationIntent {
    data class OnSpotSelected(val spotId: String) : ParkingLocationIntent()
}
