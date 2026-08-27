package io.apptolast.paparcar.presentation.home

import androidx.compose.runtime.Immutable
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkedVehicleSummary
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotStatus
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleMonitoringStatus
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.monitoringStatus
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.detection.PendingPromptWindow
import io.apptolast.paparcar.domain.detection.shouldShowParkNudgeBanner
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
import io.apptolast.paparcar.presentation.home.model.ParkedWatchBadge

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
    val searchNoResults: Boolean,
    val mapType: MapType,
    val hasCorePermissions: Boolean,
    val zones: List<Zone>,
    /** Accuracy of the last GPS fix (metres) — drives the low-accuracy banner. */
    val gpsAccuracy: Float?,
)

/** What the right-side camera FAB column sees — visibility booleans plus the
 *  identity of the session it currently points at; the tap actions read live
 *  coordinates via lambdas owned by HomeContent. */
@Immutable
data class HomeFabsSlice(
    val hasActiveParking: Boolean,
    val hasGpsFix: Boolean,
    /**
     * How the SELECTED parked vehicle is watched, or null when no session is selected. The car FAB
     * cycles between parked sessions [MULTI-PARKING-001], so its tint is the only cue for WHICH car
     * the camera is on: it goes through the single identity resolver (blue = Bluetooth, green =
     * active detection, grey = unwatched) instead of a flat brand green. [UI-FAB-CAR-IDENTITY-001]
     */
    val selectedParkingWatch: VehicleMonitoringStatus?,
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
    /** Ids of the spots the peek's ‹ / › stepper walks, in the ORDER the sheet lists them
     *  ([HomeState.filteredNearbySpots]) — not raw [nearbySpots], which still carries the
     *  withdrawn spot the user may have open and the user's own parked session.
     *  [UI-PEEK-STEPS-BETWEEN-PINS-001] */
    val browsableSpotIds: List<String>,
    val activeSessions: List<UserParking>,
    val selection: HomeSelection?,
    val vehicles: List<Vehicle>,
    val userGpsPoint: GpsPoint?,
    val drivingMeta: DrivingMeta?,
    /** [DET-ASK-STATE-001] True while a "did you park?" question is open — the CLOSED sheet says so
     *  in its phase eyebrow, which is the peek's existing voice for the live trip. */
    val isAwaitingAnswer: Boolean,
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
    /** The preferred session — same recency-based resolution as [HomeState.userParking]. */
    val userParking: UserParking?
        get() = preferredSession(activeSessions, vehicles)

    /** The selected session, or null when the selection is a spot / stale. */
    val selectedSession: UserParking?
        get() = (selection as? HomeSelection.Parking)
            ?.let { sel -> activeSessions.firstOrNull { it.id == sel.id } }

    /** The selected community spot, or null when the selection is a session / stale.
     *  [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001] Mirrors [HomeState.selectedSpot]: each side
     *  resolves inside its OWN type, so a shared id no longer shadows the spot. */
    val selectedSpot: Spot?
        get() = (selection as? HomeSelection.Spot)
            ?.let { sel -> nearbySpots.firstOrNull { it.id == sel.id } }

    val isParkingSelected: Boolean
        get() = selectedSession != null

    /** Neighbours of the selected spot in the browse order — what the peek's ‹ / › offer.
     *  [UI-PEEK-STEPS-BETWEEN-PINS-001] */
    val spotStep: PeekStep
        get() = PeekStep.of(browsableSpotIds, selectedSpot?.id)

    /** Neighbours of the selected session among the parked cars — same order the car FAB cycles.
     *  [MULTI-PARKING-001] [UI-PEEK-STEPS-BETWEEN-PINS-001] */
    val sessionStep: PeekStep
        get() = PeekStep.of(activeSessions.map { it.id }, selectedSession?.id)
}

/**
 * What the peek's footer stepper can reach from the pin currently open: the id one step back and
 * one step forward, or null on each side where there is nothing.
 * [UI-PEEK-STEPS-BETWEEN-PINS-001]
 */
@Immutable
data class PeekStep(val prevId: String?, val nextId: String?) {
    companion object {
        val None = PeekStep(null, null)

        /**
         * Neighbours of [currentId] inside [order].
         *
         * An id that is NOT in the list yields [None], never the list's edges: a WITHDRAWN spot
         * stays selected on purpose so the peek can explain itself, while dropping out of the
         * browse order [DET-HANDOFF-NOT-MANUAL-001 §B.3] — offering it "the next spot" would be
         * stepping out of a list it was never in.
         */
        fun of(order: List<String>, currentId: String?): PeekStep {
            val index = currentId?.let { id -> order.indexOfFirst { it == id } } ?: -1
            if (index < 0) return None
            return PeekStep(prevId = order.getOrNull(index - 1), nextId = order.getOrNull(index + 1))
        }
    }
}

/** What the sheet's scrollable list (vehicles + spots feed + detection surface) sees. */
@Immutable
data class HomeBrowseListSlice(
    val detectionUiState: DetectionUiState,
    /** Honest watch health for the parked/awaiting state — drives the truthful "Vigilando tu sitio"
     *  vs fragile/interrupted line. Null outside the parked context. [DET-WATCH-HONEST-001] */
    val parkedWatchBadge: ParkedWatchBadge?,
    /** Show the "where did you leave your car?" action row — an unanswered nudge with no active
     *  session resolving it. [DET-NUDGE-PERSIST-001] */
    val showParkNudge: Boolean,
    /** [DET-ASK-STATE-001] The open, still-answerable "did you park?" question, or null. */
    val promptWindow: PendingPromptWindow?,
    /** Vehicle the pending nudge asks about (pre-targets the mark-spot flow), or null. */
    val parkNudgeVehicleId: String?,
    val hasCorePermissions: Boolean,
    val isLoading: Boolean,
    val sizeFilter: VehicleSize?,
    /** [HomeState.nearbySpots] after [sizeFilter] — materialised once per state emission. */
    val filteredSpots: List<Spot>,
    /** True when the UNFILTERED nearby list is non-empty (drives the filter bar + empty states). */
    val hasAnySpots: Boolean,
    /** One entry per registered vehicle, joined to its active session — materialised once. */
    val vehicleCards: List<VehicleCard>,
    val userGpsPoint: GpsPoint?,
    val drivingMeta: DrivingMeta?,
)

// ── Projections ───────────────────────────────────────────────────────────────

internal fun HomeState.toHeaderSlice() = HomeHeaderSlice(
    searchQuery = searchQuery,
    searchResults = searchResults,
    isSearchActive = isSearchActive,
    isSearching = isSearching,
    searchNoResults = searchNoResults,
    mapType = mapType,
    hasCorePermissions = hasCorePermissions,
    zones = zones,
    gpsAccuracy = userGpsPoint?.accuracy,
)

internal fun HomeState.toFabsSlice() = HomeFabsSlice(
    hasActiveParking = userParking != null,
    hasGpsFix = userGpsPoint != null,
    // A selected session whose vehicle can no longer be found (delete race) still counts as
    // selected — it just carries no watch to claim. [UI-FAB-CAR-IDENTITY-001]
    selectedParkingWatch = selectedSession?.let { session ->
        vehicles.firstOrNull { it.id == session.vehicleId }?.monitoringStatus()
            ?: VehicleMonitoringStatus.Inactive
    },
)

internal fun HomeState.toMapSlice() = HomeMapSlice(
    mapType = mapType,
    // [DET-HANDOFF-NOT-MANUAL-001 §B.3] A withdrawn spot has no marker: leaving a pin on the map
    // for a space we no longer believe in is the exact failure the retraction exists to stop. The
    // peek explains it; the map just stops offering it.
    // [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001] …and neither does MY OWN car, published
    // provisionally while its session is deliberately kept alive: two markers on one pixel, one of
    // them offering the space the other one occupies.
    nearbySpots = nearbySpots.filter { it.status.isAvailable }.filterNot { isMyOwnLiveSession(it) },
    userGpsPoint = userGpsPoint,
    parkingLocation = userParking?.location,
    addParkingVehicle = resolveAddParkingVehicle(),
    parkedVehicles = parkedVehicles,
    zones = zones,
    isAnyItemSelected = selection != null,
    isLoading = isLoading,
    addingZoneRadius = addingZoneRadius,
    addingZoneIsPrivate = addingZoneIsPrivate,
)

internal fun HomeState.toPeekSlice(): HomePeekSlice {
    // The one list the peek quotes twice: its size is the Browse counter, its order is what the
    // ‹ / › stepper walks. Materialised once. [UI-PEEK-STEPS-BETWEEN-PINS-001]
    val browsable = filteredNearbySpots()
    return HomePeekSlice(
        detectionUiState = detectionUiState,
        mode = mode,
        freeCount = browsable.size,
        nearbySpots = nearbySpots,
        browsableSpotIds = browsable.map { it.id },
        activeSessions = activeSessions,
        selection = selection,
        vehicles = vehicles,
        userGpsPoint = userGpsPoint,
        drivingMeta = drivingMeta,
        isAwaitingAnswer = promptWindow != null,
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
}

internal fun HomeState.toBrowseListSlice() = HomeBrowseListSlice(
    detectionUiState = detectionUiState,
    parkedWatchBadge = parkedWatchBadge,
    showParkNudge = shouldShowParkNudgeBanner(pendingParkNudge, activeSessions),
    parkNudgeVehicleId = pendingParkNudge?.vehicleId,
    promptWindow = promptWindow,
    hasCorePermissions = hasCorePermissions,
    isLoading = isLoading,
    sizeFilter = sizeFilter,
    filteredSpots = filteredNearbySpots(),
    // The filter bar and the empty state ask "is there anything on offer at all" — a withdrawn
    // spot is not [DET-HANDOFF-NOT-MANUAL-001 §B.3], and neither is my own still-parked car
    // [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001].
    hasAnySpots = nearbySpots.any { it.status.isAvailable && !isMyOwnLiveSession(it) },
    vehicleCards = vehicles.map { v ->
        VehicleCard(vehicle = v, session = activeSessions.firstOrNull { it.vehicleId == v.id })
    },
    userGpsPoint = userGpsPoint,
    drivingMeta = drivingMeta,
)

/**
 * [HomeState.nearbySpots] after applying the size filter. Spots with a null
 * sizeCategory are always included (preserves legacy data with unknown sizes).
 *
 * [DET-HANDOFF-NOT-MANUAL-001 §B.3] Two more rules, both about how well the departure behind a spot
 * is proven:
 * - a WITHDRAWN spot is not on offer, so it never appears in this list. It deliberately stays in
 *   [HomeState.nearbySpots] so a user who had it selected keeps the selection and gets told what
 *   happened, instead of watching the sheet close under them.
 * - an UNCONFIRMED one is offered, but last. [sortedBy] is stable, so within each group the
 *   existing order survives untouched.
 */
internal fun HomeState.filteredNearbySpots(): List<Spot> =
    nearbySpots
        .filter { it.status.isAvailable }
        .filterNot { isMyOwnLiveSession(it) }
        .filter { sizeFilter == null || it.sizeCategory == null || it.sizeCategory == sizeFilter }
        .sortedBy { it.status == SpotStatus.PROVISIONAL }

/**
 * Is this "free spot" actually MY OWN car, still parked?
 * [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001]
 *
 * A deduced departure publishes the spot IMMEDIATELY (freshness is its whole value to a stranger)
 * while deliberately KEEPING the session and its geofence, because nothing measured says the car
 * moved [DET-HANDOFF-NOT-MANUAL-001 §B]. Correct for the community, nonsense for the owner: on
 * 2026-08-21 23:46 the user watched his car and a free space sit on the same pixel for four minutes.
 *
 * "Not on offer" is the same reason a WITHDRAWN spot is filtered above — it is just scoped to one
 * viewer instead of everyone. The publication and its short TTL are untouched.
 *
 * This is the ONE place that reads the spot↔session id reuse as a JOIN rather than as an identity.
 * The reuse is deliberate (it makes a republish rewrite the same document instead of duplicating
 * it); what was wrong was letting it decide the TYPE of a selection, and [HomeSelection] fixed that.
 */
internal fun HomeState.isMyOwnLiveSession(spot: Spot): Boolean =
    activeSessions.any { it.id == spot.id }

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
