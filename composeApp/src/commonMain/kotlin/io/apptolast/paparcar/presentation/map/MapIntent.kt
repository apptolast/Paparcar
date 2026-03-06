package io.apptolast.paparcar.presentation.map

sealed class MapIntent {
    data class OnSpotSelected(val spotId: String) : MapIntent()
}
