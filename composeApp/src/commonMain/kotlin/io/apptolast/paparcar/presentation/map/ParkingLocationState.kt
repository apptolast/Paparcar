package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking

data class ParkingLocationState(
    val isLoading: Boolean = true,
    val userLocation: GpsPoint? = null,
    val spots: List<Spot> = emptyList(),
    val userParking: UserParking? = null,
)
