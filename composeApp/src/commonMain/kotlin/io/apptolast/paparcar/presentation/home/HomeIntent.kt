package io.apptolast.paparcar.presentation.home

sealed class HomeIntent {
    data object LoadNearbySpots : HomeIntent()
    data object OpenMap : HomeIntent()
    data object OpenHistory : HomeIntent()
    data object ReportTestSpot : HomeIntent()
    data class ReleaseParking(val lat: Double, val lon: Double) : HomeIntent()
}