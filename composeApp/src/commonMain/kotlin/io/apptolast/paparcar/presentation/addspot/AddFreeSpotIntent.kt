package io.apptolast.paparcar.presentation.addspot

sealed class AddFreeSpotIntent {
    data class CameraPositionChanged(val lat: Double, val lon: Double) : AddFreeSpotIntent()
    data object ConfirmReport : AddFreeSpotIntent()
}
