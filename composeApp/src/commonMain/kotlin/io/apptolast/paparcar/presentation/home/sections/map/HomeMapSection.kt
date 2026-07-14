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
import androidx.compose.runtime.State
import io.apptolast.paparcar.domain.model.DrivingPuck
import io.apptolast.paparcar.domain.model.GpsPoint
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.HomeFabsSlice
import io.apptolast.paparcar.presentation.home.HomeMapSlice
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
    slice: HomeMapSlice,
    // Live trip render data as State — passed through to the map's isolated scopes so the fix-rate puck
    // never recomposes this section or the map. [DRIVE-PUCK-NATIVE-001]
    drivingPuck: State<DrivingPuck?>,
    tripTrail: State<List<GpsPoint>>,
    matchedTrail: State<List<GpsPoint>>,
    departurePoint: State<GpsPoint?>,
    selectedSpotId: String?,
    selectedSessionId: String?,
    reportMode: Boolean,
    cameraTarget: CameraTarget?,
    centerPin: CenterPinKind? = null,
    dimSpots: Boolean = false,
    previewZoneLat: Double? = null,
    previewZoneLon: Double? = null,
    onSpotClick: (String) -> Unit,
    onMyCarClick: (sessionId: String) -> Unit,
    onZoneClick: (String) -> Unit,
    onCameraMove: (lat: Double, lon: Double) -> Unit,
    onUserMapGesture: () -> Unit = {},
    followingDriver: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // The vehicle being positioned (its center pin matches its real map marker: icon, paint color
    // AND state border) arrives pre-resolved in [HomeMapSlice.addParkingVehicle]. [MOTION-POLISH-001]
    val addParkingVehicle = slice.addParkingVehicle

    PaparcarMapView(
        config = PaparcarMapConfig(
            mapType = slice.mapType,
            centerPin = centerPin,
        ),
        spots = slice.nearbySpots,
        userLocation = slice.userGpsPoint,
        drivingPuck = drivingPuck,
        tripTrail = tripTrail,
        matchedTrail = matchedTrail,
        departurePoint = departurePoint,
        centerDrivingPuck = followingDriver,
        parkingLocation = slice.parkingLocation,
        parkingVehicleCarbody = addParkingVehicle?.carbodyType,
        parkingVehicleSize = addParkingVehicle?.sizeCategory,
        parkingVehicleColor = addParkingVehicle?.color,
        parkingIsActive = addParkingVehicle?.isActive ?: true,
        parkingIsBluetoothPaired = addParkingVehicle?.bluetoothDeviceId != null,
        parkedVehicles = slice.parkedVehicles,
        zones = slice.zones,
        previewZoneLat = previewZoneLat,
        previewZoneLon = previewZoneLon,
        previewZoneRadius = slice.addingZoneRadius,
        previewZoneIsPrivate = slice.addingZoneIsPrivate,
        selectedSpotId = selectedSpotId,
        selectedSessionId = selectedSessionId,
        reportMode = reportMode,
        isAnyItemSelected = slice.isAnyItemSelected,
        isLoading = slice.isLoading,
        dimSpots = dimSpots,
        onSpotClick = onSpotClick,
        onMyCarClick = onMyCarClick,
        onZoneClick = onZoneClick,
        onCameraMove = onCameraMove,
        cameraTarget = cameraTarget,
        // A finger touching the map disengages driver-follow IMMEDIATELY (any touch, not only a pan past
        // touch-slop). The driving puck is now a persistent native marker, so pausing follow no longer
        // makes it vanish — which is what previously forced us to wait for a real pan. Observing on the
        // Initial pass WITHOUT consuming keeps the map fully pannable. [DRIVE-PUCK-NATIVE-001] [FOLLOW-001]
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
    slice: HomeFabsSlice,
    visible: Boolean,
    isDriving: Boolean,
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
                hasActiveParking = slice.hasActiveParking,
                hasGpsFix = slice.hasGpsFix,
                isParkingSelected = slice.isParkingSelected,
                // During a monitored trip the MyLocation FAB re-engages driver-follow instead of GPS. [DET-STATUS-SHEET-001]
                followsCar = isDriving,
                onMyLocation = onMyLocation,
                onParkedCar = onParkedCar,
                onMidpoint = onMidpoint,
            )
        }
    }
}
