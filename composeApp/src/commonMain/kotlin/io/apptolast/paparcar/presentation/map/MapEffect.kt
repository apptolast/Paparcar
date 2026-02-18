package io.apptolast.paparcar.presentation.map

sealed class MapEffect {
    data class NavigateToSpotDetails(val spotId: String) : MapEffect()
}
