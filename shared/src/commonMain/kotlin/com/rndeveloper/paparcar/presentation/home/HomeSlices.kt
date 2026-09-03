package com.rndeveloper.paparcar.presentation.home

import androidx.compose.runtime.Immutable
import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkedVehicleSummary
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleMonitoringStatus
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.detection.shouldShowParkNudgeBanner
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.FirstStepsProgress
import com.rndeveloper.paparcar.presentation.home.model.DetectionUiState
import com.rndeveloper.paparcar.presentation.home.model.ParkedWatchBadge

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
    /**
     * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Where an OPEN "did you park?" question is
     * about, or null when none is open. Comes from the durable `PendingPromptWindow` — already
     * filtered for expiry upstream — so it survives a cold open inside the window, which is the
     * only reason the map can show the CAR instead of the person holding the phone.
     */
    val unconfirmedParking: GpsPoint?,
    /** [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The car the open question is about, so its
     *  marker wears the real vehicle instead of the generic silhouette. The ACTIVE vehicle: both
     *  paths that post the question name that one, so glyph and title cannot disagree. */
    val askVehicle: Vehicle?,
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
    /** Ids of the vehicles the peek's ‹ / › stepper walks — ALL registered vehicles, in the same
     *  order as the "TUS VEHÍCULOS" strip ([vehiclesRowOrder]), not just the parked ones. Each
     *  vehicle resolves to ITS modal: with a session → its ParkingPeek, without → its
     *  AddingParkingPeek. [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001] */
    val steppableVehicleIds: List<String>,
    val activeSessions: List<UserParking>,
    val selection: HomeSelection?,
    val vehicles: List<Vehicle>,
    val userGpsPoint: GpsPoint?,
    /** Spots already voted on this session — their vote buttons stop being offered.
     *  [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] */
    val votedSpotIds: Set<String>,
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
    /** [DET-NUDGE-PIN-PROVENANCE-001] True when the open AddingParking came from a detection
     *  nudge — the peek then hides its stepper: stepping to another car and back would re-enter
     *  the mode without the nudge flag and silently drop the pin's detection provenance. */
    val addingParkingFromDetectionNudge: Boolean,
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

    /** Neighbours of [currentVehicleId] among ALL registered vehicles — what the car-lane ‹ / ›
     *  offer. The ids are VEHICLE ids: the caller resolves each into its modal (session or
     *  add-parking). [MULTI-PARKING-001] [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001] */
    fun vehicleStep(currentVehicleId: String?): PeekStep =
        PeekStep.of(steppableVehicleIds, currentVehicleId)
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
    /** [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] Where the new user stands in the guided
     *  checklist. Already resolved by the pure projection — the sheet renders it, it does not decide
     *  it, and it is also what tells the detection surface to stand down on the cold start. */
    val firstSteps: FirstStepsProgress,
) {
    /**
     * Whether the guided checklist is actually ON SCREEN — visibility AND the two conditions that
     * make it make sense. No vehicle means there is nothing to mark, and `DetectionStory.NoVehicle`
     * already owns that ask with the right CTA; no core permissions means the app cannot do any of
     * the three things the checklist teaches.
     *
     * Resolved HERE, not at the render site, because three surfaces ask the same question: the card
     * itself, the cold-start detection row standing down for it, and the sheet opening itself on it.
     * Three copies of the gate are three chances for the sheet to spring open on a checklist that
     * is not there. [ONBOARDING-FIRST-STEPS-MUST-BE-READABLE-AND-FOUND-001]
     */
    val showsFirstSteps: Boolean
        get() = firstSteps.isVisible && hasCorePermissions && vehicleCards.isNotEmpty()

    /**
     * The step the checklist is asking for right now, or null when it is not on screen / has nothing
     * left to ask. The KEY of the sheet's auto-open: it only ever moves FORWARD (the latch in
     * `subscribeFirstSteps` banks a done step, so a step never un-completes), so the sheet opens once
     * per step and dragging it back down sticks until there is genuinely something new to show.
     */
    val firstStepAnchor: FirstStep?
        get() = firstSteps.current.takeIf { showsFirstSteps }
}

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
    unconfirmedParking = promptWindow?.candidate,
    askVehicle = promptWindow?.let { vehicles.firstOrNull { v -> v.isActive } },
)

fun HomeState.toPeekSlice(): HomePeekSlice {
    // The one list the peek quotes twice: its size is the Browse counter, its order is what the
    // ‹ / › stepper walks. Materialised once. [UI-PEEK-STEPS-BETWEEN-PINS-001]
    val browsable = filteredNearbySpots()
    // The car lane of the stepper: every registered vehicle, in the strip's own order, so the
    // ‹ / › and the "TUS VEHÍCULOS" row can never disagree about who comes next.
    // [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001]
    val steppableVehicles = vehiclesRowOrder(
        cards = vehicles.map { v ->
            VehicleCard(vehicle = v, session = activeSessions.firstOrNull { it.vehicleId == v.id })
        },
        drivingVehicleId = drivingMeta?.vehicleId,
    )
    return HomePeekSlice(
        detectionUiState = detectionUiState,
        mode = mode,
        freeCount = browsable.size,
        nearbySpots = nearbySpots,
        browsableSpotIds = browsable.map { it.id },
        steppableVehicleIds = steppableVehicles.map { it.vehicle.id },
        activeSessions = activeSessions,
        selection = selection,
        vehicles = vehicles,
        userGpsPoint = userGpsPoint,
        votedSpotIds = votedSpotIds,
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
        addingParkingFromDetectionNudge = addingParkingFromDetectionNudge,
    )
}

fun HomeState.toBrowseListSlice() = HomeBrowseListSlice(
    detectionUiState = detectionUiState,
    parkedWatchBadge = parkedWatchBadge,
    showParkNudge = shouldShowParkNudgeBanner(pendingParkNudge, activeSessions),
    parkNudgeVehicleId = pendingParkNudge?.vehicleId,
    promptWindow = promptWindow,
    hasCorePermissions = hasCorePermissions,
    isLoading = isLoading,
    sizeFilter = sizeFilter,
    filteredSpots = filteredNearbySpots(),
    // The filter bar and the empty state ask "is there anything on offer at all" — resolved by
    // [spotsOnOffer], the same list the checklist's third step consults before offering to show any.
    hasAnySpots = spotsOnOffer.isNotEmpty(),
    vehicleCards = vehicles.map { v ->
        VehicleCard(vehicle = v, session = activeSessions.firstOrNull { it.vehicleId == v.id })
    },
    userGpsPoint = userGpsPoint,
    drivingMeta = drivingMeta,
    firstSteps = firstSteps,
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
 * The community spots genuinely ON OFFER right now: available, and not my own still-parked car
 * [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001] [DET-HANDOFF-NOT-MANUAL-001 §B.3].
 *
 * ONE definition, three consumers: the filter bar / empty states (`hasAnySpots`), the guided
 * checklist's third step deciding whether it can offer to SHOW spots at all, and the spot that step
 * opens when it does. The rule used to be spelled inline in the slice; a second copy for the
 * checklist is how a step ends up promising a list that turns out to be empty.
 * [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
 *
 * NOT size-filtered on purpose: the size filter is a lens the user chose, and the question here is
 * whether the community has anything at all.
 */
internal val HomeState.spotsOnOffer: List<Spot>
    get() = nearbySpots.filter { it.status.isAvailable && !isMyOwnLiveSession(it) }

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
