package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.UserParkingSession
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation

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
    val userSpotLocation: SpotLocation? = null,
    val userAddress: AddressInfo? = null,
    val isDetectionActive: Boolean = false,
    val userParking: UserParkingSession? = null,
    val spotAddresses: Map<String, AddressInfo> = emptyMap(),
)
