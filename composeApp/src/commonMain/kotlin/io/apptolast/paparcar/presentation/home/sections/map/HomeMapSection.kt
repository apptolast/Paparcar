package io.apptolast.paparcar.presentation.home.sections.map

import io.apptolast.paparcar.presentation.home.sections.map.components.HomeMapFabColumn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.map.CameraTarget
import io.apptolast.paparcar.ui.components.CenterPinKind
import io.apptolast.paparcar.ui.components.PaparcarMapConfig
import io.apptolast.paparcar.ui.components.PaparcarMapView
import io.apptolast.paparcar.ui.theme.PapMotion

/**
 * The map tile layer with its right-side floating FAB column (location,
 * parked car, midpoint). Strictly presentational — receives the camera
 * target and all action lambdas from the parent, never owns sheet state.
 */
@Composable
internal fun HomeMapSection(
    state: HomeState,
    selectedSpotId: String?,
    selectedSessionId: String?,
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
    onUserMapGesture: () -> Unit = {},
    followingDriver: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Resolve the vehicle being positioned so its center pin matches its real map marker
    // (icon, paint color AND state border). Mirrors AddingParkingPeekRow's resolution. [MOTION-POLISH-001]
    val addParkingVehicle = run {
        val vid = state.editingParkingId
            ?.let { id -> state.activeSessions.firstOrNull { it.id == id }?.vehicleId }
            ?: state.addingParkingVehicleId
        vid?.let { id -> state.vehicles.firstOrNull { it.id == id } }
    }

    PaparcarMapView(
        config = PaparcarMapConfig(
            mapType = state.mapType,
            centerPin = centerPin,
        ),
        spots = state.nearbySpots,
        userLocation = state.userGpsPoint,
        drivingPuck = state.drivingPuck,
        tripTrail = state.tripTrail,
        departurePoint = state.departurePoint,
        centerDrivingPuck = followingDriver,
        parkingLocation = state.userParking?.location,
        parkingVehicleCarbody = addParkingVehicle?.carbodyType,
        parkingVehicleSize = addParkingVehicle?.sizeCategory,
        parkingVehicleColor = addParkingVehicle?.color,
        parkingIsActive = addParkingVehicle?.isActive ?: true,
        parkingIsBluetoothPaired = addParkingVehicle?.bluetoothDeviceId != null,
        parkedVehicles = state.parkedVehicles,
        zones = state.zones,
        previewZoneLat = previewZoneLat,
        previewZoneLon = previewZoneLon,
        previewZoneRadius = previewZoneRadius,
        previewZoneIsPrivate = previewZoneIsPrivate,
        selectedSpotId = selectedSpotId,
        selectedSessionId = selectedSessionId,
        reportMode = reportMode,
        isAnyItemSelected = state.selectedItemId != null,
        isLoading = state.isLoading,
        dimSpots = dimSpots,
        onSpotClick = onSpotClick,
        onMyCarClick = onMyCarClick,
        onZoneClick = onZoneClick,
        onCameraMove = onCameraMove,
        cameraTarget = cameraTarget,
        // Observe the user's first touch WITHOUT consuming it (Initial pass), so the map still pans,
        // but we can tell a real gesture apart from a programmatic follow/center move — the camera
        // frames alone can't. This is what pauses driver-follow on a manual pan. [FOLLOW-001]
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    onUserMapGesture()
                }
            },
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
        // Right-edge controls slide out to the right (toward their origin) instead of a flat fade.
        enter = fadeIn(PapMotion.medium()) + slideInHorizontally(PapMotion.medium()) { it / 2 },
        exit = fadeOut(PapMotion.medium()) + slideOutHorizontally(PapMotion.medium()) { it / 2 },
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(end = 14.dp),
        ) {
            HomeMapFabColumn(
                userParking = state.userParking,
                userGpsPoint = state.userGpsPoint,
                isParkingSelected = state.isParkingSelected,
                // During a trip the "Following your trip" pill replaces the GPS FAB (and its recenter role). [FOLLOW-001]
                showMyLocation = state.drivingPuck == null,
                onMyLocation = onMyLocation,
                onParkedCar = onParkedCar,
                onMidpoint = onMidpoint,
            )
        }
    }
}
