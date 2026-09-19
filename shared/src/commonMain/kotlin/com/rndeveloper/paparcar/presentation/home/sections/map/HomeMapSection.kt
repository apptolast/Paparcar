package com.rndeveloper.paparcar.presentation.home.sections.map

import com.rndeveloper.paparcar.presentation.home.sections.map.components.HomeLocationPill
import com.rndeveloper.paparcar.presentation.home.sections.map.components.HomeMapFabColumn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.rndeveloper.paparcar.domain.model.DrivingPuck
import com.rndeveloper.paparcar.domain.model.GpsPoint
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.home.HomeFabsSlice
import com.rndeveloper.paparcar.presentation.home.HomeMapSlice
import com.rndeveloper.paparcar.presentation.map.CameraTarget
import com.rndeveloper.paparcar.presentation.util.MAP_FLOATING_SIDE_INSET_DP
import com.rndeveloper.paparcar.presentation.util.fuelTypeLabel
import com.rndeveloper.paparcar.ui.components.CenterPinKind
import com.rndeveloper.paparcar.ui.components.PaparcarMapConfig
import com.rndeveloper.paparcar.ui.components.PaparcarMapView
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.watch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_fuel_attended_price
import paparcar.composeapp.generated.resources.parking_fee_free
import paparcar.composeapp.generated.resources.parking_fee_paid

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
    onFuelStationClick: (String) -> Unit = {},
    onPublicParkingClick: (String) -> Unit = {},
    onCameraMove: (lat: Double, lon: Double) -> Unit,
    /** [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Tap on the unconfirmed-parking marker. */
    onAskMarkerClick: () -> Unit = {},
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
        // The trip wears the identity of the car it belongs to. [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001]
        tripWatch = slice.drivingWatch?.watch(),
        centerDrivingPuck = followingDriver,
        parkingLocation = slice.parkingLocation,
        // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The open question's place.
        unconfirmedParking = slice.unconfirmedParking,
        onAskMarkerClick = onAskMarkerClick,
        askVehicleCarbody = slice.askVehicle?.carbodyType,
        askVehicleSize = slice.askVehicle?.sizeCategory,
        askVehicleColor = slice.askVehicle?.color,
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
        fuelStations = slice.fuelStations,
        selectedFuelStationId = slice.selectedFuelStationId,
        // Resolved HERE because the map's marker builders run outside composition and cannot call
        // stringResource. [FUEL-STATIONS-MAP-AND-PEEK-001]
        fuelPricesLabel = slice.fuelPricesFor?.let { fuelTypeLabel(it) },
        // Unconditional, unlike the one above: which price a capsule is showing does not depend on
        // what the active car burns. [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
        fuelAttendedLabel = stringResource(Res.string.home_fuel_attended_price),
        // Las mismas dos palabras que ya usa la FILA del parking: una idea, una redacción.
        // [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001]
        carParkPaidLabel = stringResource(Res.string.parking_fee_paid),
        carParkFreeLabel = stringResource(Res.string.parking_fee_free),
        onFuelStationClick = onFuelStationClick,
        publicParkings = slice.publicParkings,
        selectedPublicParkingId = slice.selectedPublicParkingId,
        onPublicParkingClick = onPublicParkingClick,
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
 * Right-side FAB column, the end-edge lane of the map's FAB band. Visibility is
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
        HomeMapFabColumn(
            hasActiveParking = slice.hasActiveParking,
            hasGpsFix = slice.hasGpsFix,
            selectedParkingWatch = slice.selectedParkingWatch,
            drivingWatch = slice.drivingWatch,
            // During a monitored trip the MyLocation FAB re-engages driver-follow instead of GPS. [DET-STATUS-SHEET-001]
            followsCar = isDriving,
            onMyLocation = onMyLocation,
            onParkedCar = onParkedCar,
            onMidpoint = onMidpoint,
            modifier = Modifier.padding(end = MAP_FLOATING_SIDE_INSET_DP.dp),
        )
    }
}

/**
 * Start-edge lane of the same band: the location pill, on the floor the FAB column stands on.
 *
 * It shares the band's [visible] gate rather than the header's, which is the point of moving it
 * here — the header hides itself for every selection and every pin mode, so the warning used to
 * vanish exactly while a pin was being placed against the bad fix it warns about. It also shares
 * the band's offset, passed in by the caller as ONE lambda, so the two lanes cannot drift apart.
 * [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001]
 *
 * Since [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001] this lane also carries the missing-location
 * warning that used to be a badge in the peek's trailing slot, so Home has ONE voice about the
 * location it is working with.
 *
 * The pill runs its own enter/exit inside (it is silent almost always), so this layer only fades
 * with the band — no second slide stacked on the first.
 */
@Composable
internal fun HomeMapGpsLane(
    locationMissing: Boolean,
    accuracy: Float?,
    visible: Boolean,
    onFixLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(PapMotion.medium()),
        exit = fadeOut(PapMotion.medium()),
        modifier = modifier,
    ) {
        HomeLocationPill(
            locationMissing = locationMissing,
            accuracy = accuracy,
            onFixLocation = onFixLocation,
            modifier = Modifier.padding(start = MAP_FLOATING_SIDE_INSET_DP.dp),
        )
    }
}
