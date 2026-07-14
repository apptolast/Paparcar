package io.apptolast.paparcar.presentation.home

import androidx.compose.runtime.Immutable
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkedVehicleSummary
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.presentation.home.model.DetectionUiState

// ─────────────────────────────────────────────────────────────────────────────
// HomeSlices — per-section projections of [HomeState]. [HOME-ATOMIZE-001 F1]
//
// HomeState stays the single source of truth in the ViewModel (MVI intact);
// each section composable receives only ITS slice, so a change in an unrelated
// field (e.g. typing in the search bar) no longer recomposes the map, the FABs
// or the sheet. Slices are pure projections — `HomeState.toXxxSlice()` — built
// once per state emission in HomeScreen (`remember(state)`), and being data
// classes their structural equality gives sections free skipping.
//
// The two computed Lists that used to allocate on every read of HomeState
// (filteredNearbySpots, vehicleCards) now materialise exactly once here, in
// the projections that need them.
// ─────────────────────────────────────────────────────────────────────────────

/** What the floating header (search bar, map-type picker, zone chips, GPS banner) sees. */
@Immutable
data class HomeHeaderSlice(
    val searchQuery: String,
    val searchResults: List<SearchResult>,
    val isSearchActive: Boolean,
    val isSearching: Boolean,
    val mapType: MapType,
    val hasCorePermissions: Boolean,
    val zones: List<Zone>,
    /** Accuracy of the last GPS fix (metres) — drives the low-accuracy banner. */
    val gpsAccuracy: Float?,
)

/** What the right-side camera FAB column sees — visibility booleans only; the
 *  tap actions read live coordinates via lambdas owned by HomeContent. */
@Immutable
data class HomeFabsSlice(
    val hasActiveParking: Boolean,
    val hasGpsFix: Boolean,
    val isParkingSelected: Boolean,
)

/** What the map tile layer sees. [addParkingVehicle] is the vehicle being
 *  positioned in AddingParking mode (create or edit), pre-resolved here so the
 *  section stays purely presentational. */
@Immutable
data class HomeMapSlice(
    val mapType: MapType,
    val nearbySpots: List<Spot>,
    val userGpsPoint: GpsPoint?,
    val parkingLocation: GpsPoint?,
    val addParkingVehicle: Vehicle?,
    val parkedVehicles: List<ParkedVehicleSummary>,
    val zones: List<Zone>,
    val isAnyItemSelected: Boolean,
    val isLoading: Boolean,
    val addingZoneRadius: Float,
    val addingZoneIsPrivate: Boolean,
)

/**
 * What the peek handle (and the sheet chrome around it) sees: selection, mode
 * and the active pin-mode forms. Lists stay in the slice (instead of a single
 * pre-resolved item) because the peek's AnimatedContent must keep resolving the
 * OUTGOING variant's data while it animates away. [BUG-PEEK-JITTER-001]
 */
@Immutable
data class HomePeekSlice(
    val detectionUiState: DetectionUiState,
    val mode: HomeMode,
    /** Size of the size-filtered nearby list — the Browse spot counter. */
    val freeCount: Int,
    val nearbySpots: List<Spot>,
    val activeSessions: List<UserParking>,
    val selectedItemId: String?,
    val vehicles: List<Vehicle>,
    val parkedVehicles: List<ParkedVehicleSummary>,
    val userGpsPoint: GpsPoint?,
    val drivingMeta: DrivingMeta?,
    val cameraAddressAndPlace: AddressAndPlace?,
    val isCameraMoving: Boolean,
    val isCameraGeocoding: Boolean,
    // Reporting form
    val isReporting: Boolean,
    val reportingSize: VehicleSize?,
    // Zone form
    val isSavingZone: Boolean,
    val addingZoneName: String,
    val addingZoneIconKey: String,
    val addingZoneRadius: Float,
    val addingZoneIsPrivate: Boolean,
    val editingZoneId: String?,
    // Parking form
    val isSavingParking: Boolean,
    val editingParkingId: String?,
    val addingParkingVehicleId: String?,
) {
    /** First active session — convenience mirroring [HomeState.userParking]. */
    val userParking: UserParking?
        get() = activeSessions.firstOrNull()

    /** The session matching [selectedItemId], or null if the selection is a spot. */
    val selectedSession: UserParking?
        get() = selectedItemId?.let { id -> activeSessions.firstOrNull { it.id == id } }

    /** The selected community spot, or null if nothing/a parking session is selected. */
    val selectedSpot: Spot?
        get() = selectedItemId
            ?.takeIf { id -> activeSessions.none { it.id == id } }
            ?.let { id -> nearbySpots.firstOrNull { it.id == id } }

    val isParkingSelected: Boolean
        get() = selectedItemId != null && activeSessions.any { it.id == selectedItemId }
}

/** What the sheet's scrollable list (vehicles + spots feed + detection surface) sees. */
@Immutable
data class HomeBrowseListSlice(
    val detectionUiState: DetectionUiState,
    val hasCorePermissions: Boolean,
    val isLoading: Boolean,
    val sizeFilter: VehicleSize?,
    /** [HomeState.nearbySpots] after [sizeFilter] — materialised once per state emission. */
    val filteredSpots: List<Spot>,
    /** True when the UNFILTERED nearby list is non-empty (drives the filter bar + empty states). */
    val hasAnySpots: Boolean,
    /** One entry per registered vehicle, joined to its active session — materialised once. */
    val vehicleCards: List<VehicleCard>,
    val selectedSpotId: String?,
    val userGpsPoint: GpsPoint?,
    val drivingMeta: DrivingMeta?,
)

// ── Projections ───────────────────────────────────────────────────────────────

internal fun HomeState.toHeaderSlice() = HomeHeaderSlice(
    searchQuery = searchQuery,
    searchResults = searchResults,
    isSearchActive = isSearchActive,
    isSearching = isSearching,
    mapType = mapType,
    hasCorePermissions = hasCorePermissions,
    zones = zones,
    gpsAccuracy = userGpsPoint?.accuracy,
)

internal fun HomeState.toFabsSlice() = HomeFabsSlice(
    hasActiveParking = userParking != null,
    hasGpsFix = userGpsPoint != null,
    isParkingSelected = isParkingSelected,
)

internal fun HomeState.toMapSlice() = HomeMapSlice(
    mapType = mapType,
    nearbySpots = nearbySpots,
    userGpsPoint = userGpsPoint,
    parkingLocation = userParking?.location,
    addParkingVehicle = resolveAddParkingVehicle(),
    parkedVehicles = parkedVehicles,
    zones = zones,
    isAnyItemSelected = selectedItemId != null,
    isLoading = isLoading,
    addingZoneRadius = addingZoneRadius,
    addingZoneIsPrivate = addingZoneIsPrivate,
)

internal fun HomeState.toPeekSlice() = HomePeekSlice(
    detectionUiState = detectionUiState,
    mode = mode,
    freeCount = filteredNearbySpots().size,
    nearbySpots = nearbySpots,
    activeSessions = activeSessions,
    selectedItemId = selectedItemId,
    vehicles = vehicles,
    parkedVehicles = parkedVehicles,
    userGpsPoint = userGpsPoint,
    drivingMeta = drivingMeta,
    cameraAddressAndPlace = cameraAddressAndPlace,
    isCameraMoving = isCameraMoving,
    isCameraGeocoding = isCameraGeocoding,
    isReporting = isReporting,
    reportingSize = reportingSize,
    isSavingZone = isSavingZone,
    addingZoneName = addingZoneName,
    addingZoneIconKey = addingZoneIconKey,
    addingZoneRadius = addingZoneRadius,
    addingZoneIsPrivate = addingZoneIsPrivate,
    editingZoneId = editingZoneId,
    isSavingParking = isSavingParking,
    editingParkingId = editingParkingId,
    addingParkingVehicleId = addingParkingVehicleId,
)

internal fun HomeState.toBrowseListSlice() = HomeBrowseListSlice(
    detectionUiState = detectionUiState,
    hasCorePermissions = hasCorePermissions,
    isLoading = isLoading,
    sizeFilter = sizeFilter,
    filteredSpots = filteredNearbySpots(),
    hasAnySpots = nearbySpots.isNotEmpty(),
    vehicleCards = vehicles.map { v ->
        VehicleCard(vehicle = v, session = activeSessions.firstOrNull { it.vehicleId == v.id })
    },
    selectedSpotId = selectedSpot?.id,
    userGpsPoint = userGpsPoint,
    drivingMeta = drivingMeta,
)

/**
 * [HomeState.nearbySpots] after applying the size filter. Spots with a null
 * sizeCategory are always included (preserves legacy data with unknown sizes).
 */
internal fun HomeState.filteredNearbySpots(): List<Spot> =
    if (sizeFilter == null) nearbySpots
    else nearbySpots.filter { it.sizeCategory == null || it.sizeCategory == sizeFilter }

/**
 * The vehicle an AddingParking session is being positioned FOR — edit resolves
 * through the session being moved, create through the tapped row's vehicle id.
 * Null outside AddingParking (both id sources are mode-scoped). [MULTI-PARKING-001]
 */
private fun HomeState.resolveAddParkingVehicle(): Vehicle? {
    val vid = editingParkingId
        ?.let { id -> activeSessions.firstOrNull { it.id == id }?.vehicleId }
        ?: addingParkingVehicleId
    return vid?.let { id -> vehicles.firstOrNull { it.id == id } }
}
