package io.apptolast.paparcar.presentation.map

sealed class MapEffect {
    data class NavigateToSpotDetails(val spotId: String) : MapEffect()
    data class ShowError(val message: String) : MapEffect()
}
