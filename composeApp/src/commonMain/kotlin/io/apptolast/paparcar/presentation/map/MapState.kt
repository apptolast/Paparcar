package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint

data class MapState(
    val isLoading: Boolean = true,
    val userLocation: GpsPoint? = null,
    val spots: List<Spot> = emptyList(),
    val userParking: UserParking? = null,
    val error: String? = null,
)
