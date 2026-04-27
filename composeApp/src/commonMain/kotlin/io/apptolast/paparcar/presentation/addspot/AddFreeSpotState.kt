package io.apptolast.paparcar.presentation.addspot

import io.apptolast.paparcar.domain.model.GpsPoint

data class AddFreeSpotState(
    val userGpsPoint: GpsPoint? = null,
    val cameraLat: Double? = null,
    val cameraLon: Double? = null,
    val isReporting: Boolean = false,
)
