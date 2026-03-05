package io.apptolast.paparcar.presentation.home

import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.apptolast.paparcar.presentation.map.CameraTarget

@OptIn(ExperimentalMaterial3Api::class)
class HomeUiController(val scaffoldState: BottomSheetScaffoldState) {

    var cameraTarget: CameraTarget? by mutableStateOf(null)
        private set

    private var centeredOnUser = false

    val sheetExpanded: Boolean
        get() = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded

    fun moveCamera(lat: Double, lon: Double, zoom: Float = 17f) {
        cameraTarget = CameraTarget(lat, lon, zoom, token = (cameraTarget?.token ?: 0) + 1)
    }

    fun onUserLocationAvailable(lat: Double, lon: Double) {
        if (!centeredOnUser) {
            centeredOnUser = true
            moveCamera(lat, lon, zoom = 15f)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberHomeUiController(): HomeUiController {
    val scaffoldState = rememberBottomSheetScaffoldState()
    return remember(scaffoldState) { HomeUiController(scaffoldState) }
}
