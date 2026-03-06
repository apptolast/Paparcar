package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking

/**
 * Estado de la pantalla Home.
 * Representa todos los datos necesarios para renderizar la UI.
 */
data class HomeState(
    val isLoading: Boolean = false,
    val allPermissionsGranted: Boolean = false,
    val nearbySpots: List<Spot> = emptyList(),
    val error: String? = null,
    val userGpsPoint: GpsPoint? = null,
    /** LocationInfo for the user's current GPS position (geocoded on-demand, not stored). */
    val userLocationInfo: LocationInfo? = null,
    val isDetectionActive: Boolean = false,
    val userParking: UserParking? = null,
)
