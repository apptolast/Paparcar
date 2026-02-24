package io.apptolast.paparcar.presentation.home

/**
 * Intents (acciones del usuario) para la pantalla Home.
 * Representan todas las interacciones posibles del usuario con la UI.
 */
sealed class HomeIntent {
    object LoadNearbySpots : HomeIntent()
    data class SpotSelected(val spotId: String) : HomeIntent()
    object RefreshSpots : HomeIntent()
    object ToggleDetection : HomeIntent()
    object OpenMap : HomeIntent()
    object OpenHistory : HomeIntent()
    object ReportTestSpot : HomeIntent() // Para pruebas
}
