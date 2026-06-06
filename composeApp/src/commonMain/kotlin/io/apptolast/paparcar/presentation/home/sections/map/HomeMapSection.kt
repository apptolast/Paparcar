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
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.map.CameraTarget
import io.apptolast.paparcar.ui.components.CenterPinKind
import io.apptolast.paparcar.ui.components.PaparcarMapConfig
import io.apptolast.paparcar.ui.components.PaparcarMapView

/**
 * The map tile layer with its right-side floating FAB column (location,
 * parked car, midpoint). Strictly presentational — receives the camera
 * target and all action lambdas from the parent, never owns sheet state.
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
    previewZoneLat: Double? = null,
    previewZoneLon: Double? = null,
    previewZoneRadius: Float = Zone.DEFAULT_RADIUS_METERS,
    previewZoneIsPrivate: Boolean = false,
    onSpotClick: (String) -> Unit,
    onMyCarClick: (sessionId: String) -> Unit,
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
        previewZoneLat = previewZoneLat,
        previewZoneLon = previewZoneLon,
        previewZoneRadius = previewZoneRadius,
        previewZoneIsPrivate = previewZoneIsPrivate,
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
 * Right-side FAB column anchored to the bottom of the map. Visibility is
 * driven by the sheet's expansion fraction — hides once the user starts
 * scrolling the sheet past its midpoint so the FABs don't collide with
 * sheet content.
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
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(end = 14.dp),
        ) {
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
