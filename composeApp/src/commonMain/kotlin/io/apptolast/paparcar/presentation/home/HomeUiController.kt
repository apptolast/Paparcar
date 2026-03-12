package io.apptolast.paparcar.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.apptolast.paparcar.presentation.map.CameraTarget

class HomeUiController {

    var cameraTarget: CameraTarget? by mutableStateOf(null)
        private set

    /** Actual center reported by the map on every camera move (drag or animation). */
    var cameraLat: Double? by mutableStateOf(null)
        private set
    var cameraLon: Double? by mutableStateOf(null)
        private set

    fun onCameraMoved(lat: Double, lon: Double) {
        cameraLat = lat
        cameraLon = lon
    }

    private var centeredOnUser = false

    fun moveCamera(lat: Double, lon: Double, zoom: Float = 17f) {
        cameraTarget = CameraTarget(lat, lon, zoom, token = (cameraTarget?.token ?: 0) + 1)
    }

    fun moveCameraToBounds(lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        cameraTarget = CameraTarget(
            lat = lat1,
            lon = lon1,
            boundsLat2 = lat2,
            boundsLon2 = lon2,
            token = (cameraTarget?.token ?: 0) + 1,
        )
    }

    fun onUserLocationAvailable(lat: Double, lon: Double) {
        if (!centeredOnUser) {
            centeredOnUser = true
            moveCamera(lat, lon, zoom = 15f)
        }
    }
}

@Composable
fun rememberHomeUiController(): HomeUiController = remember { HomeUiController() }
