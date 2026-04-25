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

    /**
     * True while the map is running a programmatic camera animation
     * (moveCamera / moveCameraToBounds / initial GPS centering). The Map
     * library fires onCameraMove at ~60 fps during the animation, which
     * would otherwise trigger the idle-drag glass effect — HomeContent
     * reads this flag to suppress the effect for synthetic frames.
     *
     * Set synchronously from [moveCamera] / [moveCameraToBounds] so it is
     * true before the first animation frame arrives, and cleared by
     * HomeContent once the animation has settled.
     */
    var isProgrammaticMove: Boolean by mutableStateOf(false)
        private set

    fun onCameraMoved(lat: Double, lon: Double) {
        cameraLat = lat
        cameraLon = lon
    }

    fun clearProgrammaticMove() {
        isProgrammaticMove = false
    }

    private var centeredOnUser = false

    fun moveCamera(lat: Double, lon: Double, zoom: Float = 17f) {
        isProgrammaticMove = true
        cameraTarget = CameraTarget(lat, lon, zoom, token = (cameraTarget?.token ?: 0) + 1)
    }

    fun moveCameraToBounds(lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        isProgrammaticMove = true
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
