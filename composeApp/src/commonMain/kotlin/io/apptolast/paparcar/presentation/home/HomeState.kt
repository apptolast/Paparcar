package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.GpsPoint

/**
 * Estado de la pantalla Home.
 * Representa todos los datos necesarios para renderizar la UI.
 */
data class HomeState(
    val isLoading: Boolean = false,
    val allPermissionsGranted: Boolean = false,
    val nearbySpots: List<Spot> = emptyList(),
    val error: String? = null,
    val userLocation: Pair<Double, Double>? = null,
    val userGpsPoint: GpsPoint? = null,
    val userAddress: AddressInfo? = null,
    val isDetectionActive: Boolean = false,
    val userParking: UserParking? = null,
    val spotAddresses: Map<String, AddressInfo> = emptyMap(),
)
