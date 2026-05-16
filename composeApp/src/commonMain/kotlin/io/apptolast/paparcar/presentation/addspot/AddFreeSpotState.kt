package io.apptolast.paparcar.presentation.addspot

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.presentation.map.CameraTarget

data class AddFreeSpotState(
    val userGpsPoint: GpsPoint? = null,
    val cameraLat: Double? = null,
    val cameraLon: Double? = null,
    val nearbySpots: List<Spot> = emptyList(),
    val pinLocation: LocationInfo? = null,
    val isReporting: Boolean = false,
    // Set once on the first GPS fix so the map flies from world-origin to the
    // user's actual location. Stays stable after that — the user can pan freely
    // without retriggering animations.
    val initialCameraTarget: CameraTarget? = null,
)
