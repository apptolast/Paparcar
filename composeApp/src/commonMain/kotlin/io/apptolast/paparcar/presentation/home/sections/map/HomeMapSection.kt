package io.apptolast.paparcar.presentation.home.sections.map

import io.apptolast.paparcar.presentation.home.sections.map.components.HomeMapFabColumn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.map.CameraTarget
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
    mapHeightDp: Dp,
    showAnimatedCenterPin: Boolean = false,
    dimSpots: Boolean = false,
    onSpotClick: (String) -> Unit,
    onMyCarClick: () -> Unit,
    onCameraMove: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaparcarMapView(
        config = PaparcarMapConfig(
            mapType = state.mapType,
            showAnimatedCenterPin = showAnimatedCenterPin,
        ),
        spots = state.nearbySpots,
        userLocation = state.userGpsPoint,
        parkingLocation = state.userParking?.location,
        selectedSpotId = selectedSpotId,
        isMyCarSelected = isMyCarSelected,
        reportMode = reportMode,
        isAnyItemSelected = state.selectedItemId != null,
        isLoading = state.isLoading,
        dimSpots = dimSpots,
        onSpotClick = onSpotClick,
        onMyCarClick = onMyCarClick,
        onCameraMove = onCameraMove,
        cameraTarget = cameraTarget,
        modifier = modifier
            .fillMaxWidth()
            .height(mapHeightDp),
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
    bottomInset: Dp,
    onMyLocation: () -> Unit,
    onParkedCar: () -> Unit,
    onMidpoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.padding(end = 14.dp, bottom = bottomInset),
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
