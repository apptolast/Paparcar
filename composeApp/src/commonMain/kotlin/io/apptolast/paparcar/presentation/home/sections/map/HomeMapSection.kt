package io.apptolast.paparcar.presentation.home.sections.map

import io.apptolast.paparcar.presentation.home.sections.map.components.HomeMapFabColumn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.map.CameraTarget
import io.apptolast.paparcar.ui.components.CenterPinKind
import io.apptolast.paparcar.ui.components.PaparcarMapConfig
import io.apptolast.paparcar.ui.components.PaparcarMapView

/**
 * The map tile layer. Height is supplied entirely through [modifier] by the
 * parent using a layout-phase modifier (Modifier.layout) so that dragging the
 * sheet never triggers a composition of this tree — only a re-layout.
 */
@Composable
internal fun HomeMapSection(
    state: HomeState,
    selectedSpotId: String?,
    isMyCarSelected: Boolean,
    reportMode: Boolean,
    cameraTarget: CameraTarget?,
    centerPin: CenterPinKind? = null,
    dimSpots: Boolean = false,
    onSpotClick: (String) -> Unit,
    onMyCarClick: () -> Unit,
    onZoneClick: (String) -> Unit,
    onCameraMove: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaparcarMapView(
        config = PaparcarMapConfig(
            mapType = state.mapType,
            centerPin = centerPin,
        ),
        spots = state.nearbySpots,
        userLocation = state.userGpsPoint,
        parkingLocation = state.userParking?.location,
        parkedVehicles = state.parkedVehicles,
        zones = state.zones,
        selectedSpotId = selectedSpotId,
        isMyCarSelected = isMyCarSelected,
        reportMode = reportMode,
        isAnyItemSelected = state.selectedItemId != null,
        isLoading = state.isLoading,
        dimSpots = dimSpots,
        onSpotClick = onSpotClick,
        onMyCarClick = onMyCarClick,
        onZoneClick = onZoneClick,
        onCameraMove = onCameraMove,
        cameraTarget = cameraTarget,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Right-side FAB column. Positioning (bottom inset) is supplied entirely
 * through [modifier] by the parent using a layout-phase offset modifier so
 * that dragging the sheet never triggers a composition of this tree.
 */
@Composable
internal fun HomeMapFabsLayer(
    state: HomeState,
    visible: Boolean,
    onMyLocation: () -> Unit,
    onParkedCar: () -> Unit,
    onMidpoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(end = 14.dp),
    ) {
        Column(horizontalAlignment = Alignment.End) {
            HomeMapFabColumn(
                userParking = state.userParking,
                userGpsPoint = state.userGpsPoint,
                onMyLocation = onMyLocation,
                onParkedCar = onParkedCar,
                onMidpoint = onMidpoint,
            )
        }
    }
}
