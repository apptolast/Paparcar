package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.Spot

/**
 * Estado de la pantalla Home.
 * Representa todos los datos necesarios para renderizar la UI.
 */
data class HomeState(
    val isLoading: Boolean = false,
    val allPermissionsGranted: Boolean = false, // Necesario para que la UI reaccione
    val nearbySpots: List<Spot> = emptyList(),
    val error: String? = null,
    val userLocation: Pair<Double, Double>? = null,
    val isDetectionActive: Boolean = false
)
