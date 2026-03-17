package io.apptolast.paparcar.presentation.home

sealed class HomeIntent {
    data object LoadNearbySpots : HomeIntent()
    data object OpenMap : HomeIntent()
    data object OpenHistory : HomeIntent()
    data object ReportTestSpot : HomeIntent()
    data class ReleaseParking(val lat: Double, val lon: Double) : HomeIntent()
    /** null clears the selection; [PARKING_ITEM_ID] selects the parked car; any other ID selects a spot. */
    data class SelectItem(val itemId: String?) : HomeIntent()
    data object ManualPark : HomeIntent()
    data class CameraPositionChanged(val lat: Double, val lon: Double) : HomeIntent()
}
