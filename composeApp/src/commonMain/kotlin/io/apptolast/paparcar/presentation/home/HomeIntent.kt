package io.apptolast.paparcar.presentation.home

sealed class HomeIntent {
    data object LoadNearbySpots : HomeIntent()
    data class SpotSelected(val spotId: String) : HomeIntent()
    data object OpenMap : HomeIntent()
    data object OpenHistory : HomeIntent()
    data object ReportTestSpot : HomeIntent()
    data object ReleaseParking : HomeIntent()
}