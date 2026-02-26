package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.model.UserParkingSession
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation

data class MapState(
    val isLoading: Boolean = true,
    val userLocation: SpotLocation? = null,
    val spots: List<Spot> = emptyList(),
    val userParking: UserParkingSession? = null,
    val error: String? = null,
)
