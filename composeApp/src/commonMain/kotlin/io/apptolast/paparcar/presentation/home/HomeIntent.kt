package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.SearchResult

sealed class HomeIntent {
    data object LoadNearbySpots : HomeIntent()
    data object OpenHistory : HomeIntent()
    data object ReportTestSpot : HomeIntent()
    data class ReleaseParking(val lat: Double, val lon: Double) : HomeIntent()
    /** null clears the selection; [HomeState.PARKING_ITEM_ID] selects the parked car; any other ID selects a spot. */
    data class SelectItem(val itemId: String?) : HomeIntent()
    data object ManualPark : HomeIntent()
    data class CameraPositionChanged(val lat: Double, val lon: Double) : HomeIntent()
    data class SearchQueryChanged(val query: String) : HomeIntent()
    data class SelectSearchResult(val result: SearchResult) : HomeIntent()
    data object ClearSearch : HomeIntent()
    data class ReportManualSpot(val lat: Double, val lon: Double) : HomeIntent()
    data object CycleMapType : HomeIntent()
}
