package com.rndeveloper.paparcar.presentation.home

import androidx.compose.runtime.Immutable
import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkedVehicleSummary
import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotReportGate
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
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.presentation.home.model.homeAwaitsLocationAnswer
import com.rndeveloper.paparcar.presentation.home.model.homeHasNoLocation
import com.rndeveloper.paparcar.presentation.home.model.DetectionUiState
import com.rndeveloper.paparcar.presentation.home.model.ParkedWatchBadge
import com.rndeveloper.paparcar.presentation.util.PlaceLine
import com.rndeveloper.paparcar.presentation.util.secondaryPlaceLine
import com.rndeveloper.paparcar.presentation.util.toPlaceLine
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.presentation.home.model.HomeParkingsSlice
import com.rndeveloper.paparcar.presentation.home.model.ViewportRefetch
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery

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

/** What the floating header (search bar, map-type picker, zone chips) sees. */
@Immutable
data class HomeHeaderSlice(
    val searchQuery: String,
    val searchResults: List<SearchResult>,
    val isSearchActive: Boolean,
    val isSearching: Boolean,
    val searchNoResults: Boolean,
    val mapType: MapType,
    val zones: List<Zone>,
)

/** What the map's FAB band sees — the camera column on the end edge plus the GPS pill on the start
 *  edge: visibility booleans and the identity of the session the column currently points at; the
 *  tap actions read live coordinates via lambdas owned by HomeContent. */
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
    /** How the car being FOLLOWED right now is watched — the `MyLocation` FAB wears that identity
     *  while it re-engages driver-follow, the same colour its trail and puck carry on the map.
     *  Null when no trip is live. [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001] */
    val drivingWatch: VehicleMonitoringStatus?,
    /** Accuracy of the last GPS fix (metres) — the pill on the band's start edge speaks when it is
     *  too coarse to trust. It rides in THIS slice, not the header's, because the band is where it
     *  is drawn and the band's visibility is the one it must follow: the header hides for every pin
     *  mode, which is when the warning matters most. [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001] */
    val gpsAccuracy: Float?,
    /** There is no location at all (permission or GPS) — see [homeHasNoLocation]. Same pill, and
     *  it rides in this slice for the same reason as [gpsAccuracy]: it is drawn here. It used to be
     *  a badge in the peek, where it ate the width the header needs to NAME things.
     *  [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001] */
    val locationMissing: Boolean,
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
    /**
     * How the car whose trip is being drawn is watched — the trail, its origin dot and the puck's
     * halo wear that identity instead of a generic movement blue. Null when no trip is live.
     * [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001]
     */
    val drivingWatch: VehicleMonitoringStatus?,
    /** The fuel-stations layer: empty whenever it is OFF, so the map never has to ask why.
     *  [FUEL-STATIONS-MAP-AND-PEEK-001] */
    val fuelStations: List<NearbyFuelStation>,
    /** Which station wears the selected capsule, or null. */
    val selectedFuelStationId: String?,
    /** Which fuel those capsule prices ARE — a figure with no subject forces the reader to
     *  remember what their car burns. Null when the answer was fetched fuel-less (unpriced). */
    val fuelPricesFor: FuelType?,
    /** The public-parkings layer: empty whenever it is OFF. [PARKINGS-ON-THE-MAP-001] */
    val publicParkings: List<NearbyPublicParking>,
    /** Which car park wears the selected capsule, or null. */
    val selectedPublicParkingId: String?,
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
    /**
     * WHICH nearby subject the open sheet is showing. The expanded header speaks for it — with the
     * lane on car parks, a header counting free spots is two subjects' cifras 300 dp apart.
     * [HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001]
     *
     * Collapsed, the peek speaks for your car — the live trip, else the parked one — only until the
     * user first opens Browse; from then on a collapsed peek keeps the lane too (a live trip still
     * wins). [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
     *
     * …and gives it straight back when that lane's list is EMPTY: a subject over a zero is not news,
     * and the parked car always is. [HOME-AN-EMPTY-LANE-HANDS-THE-HEADER-BACK-TO-THE-CAR-001]
     */
    val nearbyLens: HomeNearbyLens,
    /**
     * How many there are of the subject being shown, or **null when nobody has asked yet**.
     *
     * ⛔ Null is not zero. A complement only has a count once its lane has been entered, and a `0`
     * on a subject nobody asked is a verdict about the world no source ever gave — the same rule the
     * tab row already follows. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     *
     * Free spots are never null: they are a standing subscription, not an ask.
     */
    val laneCount: Int?,
    /** El carril está preguntando ahora mismo — la cápsula late en vez de enseñar un hueco.
     *  [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001] */
    val laneLoading: Boolean,
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
    /** The fuel answer the peek resolves a station selection against — the LIST stays in the slice
     *  (same reason as [nearbySpots]): the outgoing peek keeps resolving while it animates away.
     *  [BUG-PEEK-JITTER-001] [FUEL-STATIONS-MAP-AND-PEEK-001] */
    val fuelStations: List<NearbyFuelStation>,
    /** The feed's own hour for those prices, verbatim; null where the source has none. */
    val fuelReportedAt: String?,
    /** True when the feed guarantees near-real-time prices instead of stamping an hour (Germany's
     *  MTS-K). [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001] */
    val fuelPricesAreLive: Boolean,
    /** `source|id` of the forecourts the user follows — the peek's star reads this and nothing else.
     *  Free, never gated: what Plus buys is the watching, not the star.
     *  [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
    val followedFuelStationKeys: Set<String>,
    /** The car parks the peek resolves a selection against — same reason the fuel list is here.
     *  [PARKINGS-ON-THE-MAP-001] */
    val publicParkings: List<NearbyPublicParking>,
    /** Spots already voted on this session — their vote buttons stop being offered.
     *  [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] */
    val votedSpotIds: Set<String>,
    val drivingMeta: DrivingMeta?,
    /** [DET-ASK-STATE-001] True while a "did you park?" question is open — the CLOSED sheet says so
     *  in its phase eyebrow, which is the peek's existing voice for the live trip. */
    val isAwaitingAnswer: Boolean,
    val cameraAddressAndPlace: AddressAndPlace?,
    /**
     * Where the car being driven RIGHT NOW is — its own geocode, null whenever no trip is live.
     * NOT the camera: the camera is the subject of the ZONE header only. A moving car that borrows
     * its address from wherever the map happens to be looking is the driving twin of the bug
     * [UI-SHEET-004] already closed for the parked one ("one car parked in two places") — it just
     * hides until you pan the map mid-trip. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
     */
    val drivingPlace: PlaceLine?,
    /** Street/city under [drivingPlace], same subject. Null when there is no trip or nothing to add. */
    val drivingSecondaryLine: String?,
    val isCameraMoving: Boolean,
    val isCameraGeocoding: Boolean,
    /**
     * Si el pin está sobre un SITIO. Falso mientras nadie haya posado la cámara — sin fix, sin coche
     * aparcado y sin zona, el mapa se abre en una semilla de emergencia que no es ningún lugar y que
     * por tanto no reporta su centro. Los confirms de pin lo usan para callarse con su razón en vez
     * de guardar el Golfo de Guinea. [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001]
     */
    val pinHasPlace: Boolean,
    // Reporting form
    val isReporting: Boolean,
    /** Whether the spot under the pin may be published, and if not, which sentence the peek owes the
     *  user. Measured in the state that holds both the fix and the pin.
     *  [SPOT-A-REPORT-YOU-CANNOT-SEE-IS-NOT-A-REPORT-001] */
    val reportGate: SpotReportGate,
    val reportingSize: VehicleSize?,
    // Zone form
    val isSavingZone: Boolean,
    /** True while the delete write for the zone being EDITED is in flight — the only zone this peek
     *  can delete, so the whole id set never has to cross the boundary. */
    val isDeletingZone: Boolean,
    val addingZoneName: String,
    val addingZoneIconKey: String,
    val addingZoneRadius: Float,
    val addingZoneIsPrivate: Boolean,
    /** Plus zone-alert opt-in of the zone being created/edited. [MONEY-001] */
    val addingZoneNotify: Boolean,
    val editingZoneId: String?,
    // Parking form
    /** WHICH confirm is in flight — see [HomeState.savingParkingAsNewSession]. The peek shows two
     *  confirms, so the spinner needs to know which. [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] */
    val savingParkingAsNewSession: Boolean?,
    /** Non-null while a release runs — the edit peek's "delete record" confirm spins on it. */
    val releasingReason: ParkingReleaseReason?,
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

    /** The selected fuel station, resolved inside its own arm like the other two.
     *  [FUEL-STATIONS-MAP-AND-PEEK-001] */
    val selectedFuelStation: NearbyFuelStation?
        get() = (selection as? HomeSelection.FuelStation)
            ?.let { sel -> fuelStations.firstOrNull { it.station.id == sel.id } }

    /** The selected car park, resolved inside its own arm. [PARKINGS-ON-THE-MAP-001] */
    val selectedPublicParking: NearbyPublicParking?
        get() = (selection as? HomeSelection.PublicParking)
            ?.let { sel -> publicParkings.firstOrNull { it.parking.id == sel.id } }

    /** Neighbours of the selected spot in the browse order — what the peek's ‹ / › offer.
     *  [UI-PEEK-STEPS-BETWEEN-PINS-001] */
    val spotStep: PeekStep
        get() = PeekStep.of(browsableSpotIds, selectedSpot?.id)

    /** Neighbours of the selected fuel station in the "nearest first" order the sheet lists — the
     *  fuel lane's ‹ / ›. A station that dropped out on a refresh yields [PeekStep.None], never
     *  invented edges. [FUEL-PEEK-STEPS-BETWEEN-STATIONS-001] */
    val fuelStationStep: PeekStep
        get() = PeekStep.of(fuelStations.map { it.station.id }, selectedFuelStation?.station?.id)

    /** Neighbours of the selected car park in the "nearest first" order the sheet lists — the
     *  parkings lane's ‹ / ›, the same stepper spots, fuel and the car lane already use. A car park
     *  that dropped out on a refresh yields [PeekStep.None]. [CARPARK-DETAIL-HAS-PREV-NEXT-CLOSE-001] */
    val publicParkingStep: PeekStep
        get() = PeekStep.of(publicParkings.map { it.parking.id }, selectedPublicParking?.parking?.id)

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
    /**
     * The first-entry location ask is imminent or on screen with no answer yet, so the amber
     * `BlockedCore` row stands down — the app would be telling the user their location is off while
     * the dialog asking for it is still open on top. Resolved by `homeAwaitsLocationAnswer`, the
     * same verdict the map's pill obeys, so the two cannot go quiet at different moments.
     * [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
     */
    val locationAskPending: Boolean,
    val isLoading: Boolean,
    val sizeFilter: VehicleSize?,
    /** [HomeState.nearbySpots] after [sizeFilter] — materialised once per state emission. */
    val filteredSpots: List<Spot>,
    /** True when the UNFILTERED nearby list is non-empty (drives the filter bar + empty states). */
    val hasAnySpots: Boolean,
    /** Nadie ha preguntado por las plazas todavía — no hay centro de consulta. El carril lo DICE en
     *  vez de dar por vacío un sitio que no ha mirado. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] */
    val spotsUnasked: Boolean,
    /** One entry per registered vehicle, joined to its active session — materialised once. */
    val vehicleCards: List<VehicleCard>,
    val userGpsPoint: GpsPoint?,
    val drivingMeta: DrivingMeta?,
    /**
     * Where the car being driven RIGHT NOW is, for the vehicle chip / card footer. Same subject and
     * same resolver as the peek's [HomePeekSlice.drivingPlace], so the two surfaces cannot name two
     * streets for one trip. Null whenever no trip is live — which is what keeps this list's slice
     * from re-emitting outside a drive. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
     */
    val drivingPlace: PlaceLine?,
    /** [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] Where the new user stands in the guided
     *  checklist. Already resolved by the pure projection — the sheet renders it, it does not decide
     *  it, and it is also what tells the detection surface to stand down on the cold start. */
    val firstSteps: FirstStepsProgress,
    /** "Where to refuel" — the answer only; whether it is ON SHOW is [nearbyLens].
     *  [FUEL-STATIONS-LIST-001] */
    val fuel: HomeFuelSlice,
    /** "Car parks nearby" — same contract, same rule. [PARKINGS-ON-THE-MAP-001] */
    val parkings: HomeParkingsSlice,
    /** WHICH of the three nearby subjects the lane is answering. One lane at a time, so a subject
     *  never sits behind another's list. [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001] */
    val nearbyLens: HomeNearbyLens,
    /**
     * The complement being introduced in this very visit, or null — the lane owes its provenance
     * line only the first time it is asked for. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
     *
     * Carried as the COMPLEMENT and not as a boolean per lane: each lane compares it against its own
     * subject, so a line can never be rendered by the lane that is not the one being introduced.
     */
    val nearbyIntro: NearbyComplement?,
) {
    /**
     * Whether the guided checklist is actually ON SCREEN — visibility AND the condition that makes
     * it make sense: there has to be a car. With none there is nothing to mark, and
     * `DetectionStory.NoVehicle` already owns that ask with the right CTA.
     *
     * ⛔ **The CORE permission is NOT a condition.** It was, on the premise that without location the
     * app "cannot do any of the three things the checklist teaches" — and that premise was wrong for
     * all three: marking a parking starts from the camera, the nearby lane answers about the place
     * you are looking at, and the two steps that used to open flows now only POINT at them
     * [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001]. Hiding the tutorial was reserved for exactly the user
     * who had never seen it. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
     *
     * Resolved HERE, not at the render site, because three surfaces ask the same question: the card
     * itself, the cold-start detection row standing down for it, and the sheet opening itself on it.
     * Three copies of the gate are three chances for the sheet to spring open on a checklist that
     * is not there. [ONBOARDING-FIRST-STEPS-MUST-BE-READABLE-AND-FOUND-001]
     */
    val showsFirstSteps: Boolean
        get() = firstSteps.isVisible && vehicleCards.isNotEmpty()

    /**
     * WHAT the checklist has to show right now, as a key — or null when it is not on screen.
     * [ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001]
     *
     * The KEY of the sheet's auto-open. It used to be the current STEP, which had a hole: reading a
     * step's explanation can complete it, and if it was the last one the user came back from that
     * screen to a closed sheet — with the checklist hidden at the exact moment they finished it.
     * A finished-but-still-visible checklist has something to show too (its closing card), so it
     * gets its own key instead of collapsing to "nothing to ask".
     *
     * Steps only ever move FORWARD (the latch banks a done step), so this changes a handful of times
     * at most: once per step, once on closing.
     */
    val firstStepsCue: String?
        get() = when {
            !showsFirstSteps -> null
            else -> firstSteps.current?.name ?: FIRST_STEPS_CLOSING_CUE
        }
}

// ── Projections ───────────────────────────────────────────────────────────────

internal fun HomeState.toHeaderSlice() = HomeHeaderSlice(
    searchQuery = searchQuery,
    searchResults = searchResults,
    isSearchActive = isSearchActive,
    isSearching = isSearching,
    searchNoResults = searchNoResults,
    mapType = mapType,
    zones = zones,
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
    drivingWatch = drivingMeta?.let { meta ->
        vehicles.firstOrNull { it.id == meta.vehicleId }?.monitoringStatus()
            ?: VehicleMonitoringStatus.Inactive
    },
    gpsAccuracy = userGpsPoint?.accuracy,
    // ⛔ Not the watch's block — the PHONE's absence of a position, which is true with or without a
    // registered car. The pill used to read `warnsLocationMissing`, and that left the one user who
    // most needs the sentence (just refused the permission, no car yet) looking at a Home that never
    // said why it did not know where they were.
    // [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]
    //
    // …and it holds its tongue while the first-entry ask is on screen: stating the absence of a
    // permission the user is at that very moment being asked for is answering for them, and the
    // remedy the pill offers (tap → permissions) is the dialog already open on top of it.
    // [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
    locationMissing = homeHasNoLocation(hasCorePermissions, detectionUiState) &&
        !homeAwaitsLocationAnswer(hasCorePermissions, locationEverAsked, locationAnswerInFlight),
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
    // Same shape as [HomeFabsSlice.selectedParkingWatch]: a trip whose vehicle no longer resolves
    // (deleted mid-trip) still has a watch to declare — the unwatched one.
    // [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001]
    drivingWatch = drivingMeta?.let { meta ->
        vehicles.firstOrNull { it.id == meta.vehicleId }?.monitoringStatus()
            ?: VehicleMonitoringStatus.Inactive
    },
    // Only while the LANE is on this subject: the map shows the complement you are LOOKING AT, never
    // two label layers stacked over the spot pins that are the product.
    // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
    //
    // ⛔ The SAME projection the lane lists, not a union of stores: one store, one anchor, so a pin
    // and a row can never be about two different areas. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
    // Priced for the car you drive. [MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001]
    // ⛔ `…OnMap`, no `…Shown`: el mismo CONJUNTO, sin el orden del carril. Un marcador no se
    // reordena —no tiene fila— así que heredar el orden sólo servía para que un chip de orden
    // entregara una lista distinta y reconstruyera cientos de marcadores idénticos.
    // [MAP-THE-MAP-READS-THE-SET-NOT-THE-ORDER-001]
    fuelStations = if (showsFuelOnMap) fuelStationsOnMap else emptyList(),
    selectedFuelStationId = (selection as? HomeSelection.FuelStation)?.id,
    // The label of the figure comes from the same place the figure does — a capsule that reads
    // "1,529 Diésel" while quoting petrol is the one failure this pairing exists to prevent.
    fuelPricesFor = pricedFuelType,
    publicParkings = if (showsParkingsOnMap) publicParkingsOnMap else emptyList(),
    selectedPublicParkingId = (selection as? HomeSelection.PublicParking)?.id,
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
        nearbyLens = nearbyLens,
        // "Asked yet?" is answered by the ANCHOR the slice already keeps — the centre its answer is
        // about. No new flag: a lane with no anchor is a lane nobody has entered.
        // [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001]
        // Las plazas NUNCA cargan: son una suscripción viva, no una pregunta.
        laneLoading = when (nearbyLens) {
            HomeNearbyLens.SPOTS -> false
            HomeNearbyLens.PARKINGS -> parkings.isLoading
            HomeNearbyLens.FUEL -> fuel.isLoading
        },
        laneCount = when (nearbyLens) {
            HomeNearbyLens.SPOTS -> browsable.size
            HomeNearbyLens.PARKINGS -> parkings.parkings.size.takeIf { parkings.anchorLat != null }
            HomeNearbyLens.FUEL -> fuel.stations.size.takeIf { fuel.anchorLat != null }
        },
        nearbySpots = nearbySpots,
        browsableSpotIds = browsable.map { it.id },
        steppableVehicleIds = steppableVehicles.map { it.vehicle.id },
        activeSessions = activeSessions,
        selection = selection,
        vehicles = vehicles,
        userGpsPoint = userGpsPoint,
        // The same projection the lane lists and the map paints, so the stepper walks exactly the
        // rows on show, in their order. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
        // [MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001]
        fuelStations = fuelStationsShown,
        fuelReportedAt = fuel.reportedAt,
        // Solo en respuesta VIVA: una lista recordada del disco reconstruye sin el flag, y su
        // frase («no se pudo preguntar hoy») manda igualmente.
        fuelPricesAreLive = fuel.pricesAreLive && fuel.rememberedAtMs == null,
        followedFuelStationKeys = followedFuelStationKeys,
        publicParkings = publicParkingsShown,
        votedSpotIds = votedSpotIds,
        drivingMeta = drivingMeta,
        isAwaitingAnswer = promptWindow != null,
        cameraAddressAndPlace = cameraAddressAndPlace,
        // Resolved from the phone's OWN geocode — while a trip runs the phone is in the car, the
        // same premise the whole detector stands on. Only while it runs: no trip, no field.
        // [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
        drivingPlace = drivingMeta?.let { userAddressAndPlace.toPlaceLine() },
        drivingSecondaryLine = drivingMeta?.let { userAddressAndPlace.secondaryPlaceLine() },
        isCameraMoving = isCameraMoving,
        isCameraGeocoding = isCameraGeocoding,
        pinHasPlace = pinCoordinates() != null,
        isReporting = isReporting,
        reportGate = reportGate(),
        reportingSize = reportingSize,
        isSavingZone = isSavingZone,
        isDeletingZone = editingZoneId != null && editingZoneId in deletingZoneIds,
        addingZoneName = addingZoneName,
        addingZoneIconKey = addingZoneIconKey,
        addingZoneRadius = addingZoneRadius,
        addingZoneIsPrivate = addingZoneIsPrivate,
        addingZoneNotify = addingZoneNotify,
        editingZoneId = editingZoneId,
        savingParkingAsNewSession = savingParkingAsNewSession,
        releasingReason = releasingReason,
        editingParkingId = editingParkingId,
        addingParkingVehicleId = addingParkingVehicleId,
        addingParkingFromDetectionNudge = addingParkingNudgeSource != null,
    )
}

fun HomeState.toBrowseListSlice() = HomeBrowseListSlice(
    // The rows are the map's pins, in the order they are listed: one projection over one store.
    // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
    fuel = fuel.copy(
        stations = fuelStationsShown,
        // Los filtros viajan con el carril: la barra los lee del mismo sitio que la lista que
        // ordenan. [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001]
        sort = fuelSort,
        // ⛔ `pricedFuelType`, no `fuelProduct`: el chip enseña el combustible por el que el carril
        // ESTÁ cotizado, y sin elección del usuario ése es el del coche. Leyendo `fuelProduct` en
        // crudo, la barra decía «ningún filtro» mientras la lista ya venía filtrada por gasolina —
        // dos controles contando cosas distintas del mismo estado (user, en device).
        // [FUEL-THE-CHIP-SHOWS-THE-FUEL-THE-LANE-IS-QUOTED-IN-001]
        product = pricedFuelType,
        availableProducts = fuelProductsAvailable,
    ),
    parkings = parkings.copy(
        parkings = publicParkingsShown,
        filter = parkingFilter,
        hasAnyBeforeFilter = hasAnyPublicParking,
    ),
    nearbyLens = nearbyLens,
    nearbyIntro = nearbyIntro,
    detectionUiState = detectionUiState,
    parkedWatchBadge = parkedWatchBadge,
    showParkNudge = shouldShowParkNudgeBanner(pendingParkNudge, activeSessions),
    parkNudgeVehicleId = pendingParkNudge?.vehicleId,
    promptWindow = promptWindow,
    hasCorePermissions = hasCorePermissions,
    locationAskPending = homeAwaitsLocationAnswer(
        hasCorePermissions, locationEverAsked, locationAnswerInFlight,
    ),
    isLoading = isLoading,
    sizeFilter = sizeFilter,
    filteredSpots = filteredNearbySpots(),
    // The filter bar and the empty state ask "is there anything on offer at all" — resolved by
    // [spotsOnOffer], the same list the checklist's third step consults before offering to show any.
    hasAnySpots = spotsOnOffer.isNotEmpty(),
    spotsUnasked = spotsUnasked,
    vehicleCards = vehicles.map { v ->
        VehicleCard(vehicle = v, session = activeSessions.firstOrNull { it.vehicleId == v.id })
    },
    userGpsPoint = userGpsPoint,
    drivingMeta = drivingMeta,
    // Same subject, same resolver as the peek's — one trip, one street. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
    drivingPlace = drivingMeta?.let { userAddressAndPlace.toPlaceLine() },
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
/**
 * El primer elemento del carril que se está mirando, como coordenada — o null si ese carril aún no
 * tiene nada. [HOME-A-LANE-CHANGE-TAKES-THE-CAMERA-WITH-IT-001]
 *
 * ⛔ Lee **las mismas listas que pinta la hoja**, no una consulta propia: el «primer elemento» tiene
 * que ser el que el usuario ve arriba del todo, y una segunda fuente con otro orden convertiría el
 * encuadre en una coordenada que no corresponde a ninguna fila.
 *
 * Eso era lo que decía, y **dejó de ser verdad en cuanto entraron los filtros**
 * [HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001]: parkings y gasolineras leían la lista CRUDA
 * (`parkings.parkings`, `fuel.stations`) mientras la hoja pintaba `publicParkingsShown` y
 * `fuelStationsShown`, que son las que llevan orden y filtro. Con «Covered» puesto, la cámara volaba
 * a un parking que la lista no enseñaba — y al quitar el filtro aparecía. Ahora las tres ramas leen
 * la lista PINTADA, que es lo que el párrafo de arriba prometía.
 *
 * ⛔ **Y no se encuadra una respuesta que va de otro sitio.** Si la lista contesta sobre donde la
 * cámara ya no está —buscas Madrid estando en Italia— el primer elemento es italiano, y encuadrarlo
 * devuelve la cámara de donde el usuario acaba de sacarla. Se espera a que llegue la respuesta de
 * aquí, que es lo que el carril ya está pidiendo. La pregunta la contesta
 * [ViewportRefetch.answerCoversCamera], la misma que decide si hay que repreguntar.
 */
internal fun HomeState.laneFirstPoint(
    cameraLat: Double?,
    cameraLon: Double?,
): Pair<Double, Double>? = laneAnsweringHere(cameraLat, cameraLon).let { here ->
    when (nearbyLens) {
        // Las plazas son una suscripción viva sobre el área: no tienen ancla que comparar.
        HomeNearbyLens.SPOTS -> here.filteredNearbySpots().firstOrNull()
            ?.let { it.location.latitude to it.location.longitude }

        HomeNearbyLens.PARKINGS -> here.publicParkingsShown.firstOrNull()
            ?.let { it.parking.latitude to it.parking.longitude }

        HomeNearbyLens.FUEL -> here.fuelStationsShown.firstOrNull()
            ?.let { it.station.latitude to it.station.longitude }
    }
}

/**
 * El estado tal y como se puede ENSEÑAR desde donde la cámara está mirando: si la tienda de un
 * complemento contesta sobre otro sitio, ese complemento se queda **vacío**.
 * [HOME-A-LANE-THAT-CANNOT-ANSWER-HERE-SHOWS-NO-ROWS-001]
 *
 * ⛔ Nace de que [HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001] enseñó a la CÁMARA a no encuadrar
 * una respuesta de otra ciudad, y dejó a la LISTA sin aprenderlo: buscando Madrid desde Italia, las
 * filas seguían siendo italianas y **tocar una es una orden legítima que te lleva a Italia**. La
 * cámara dejaba de mentir y la lista no.
 *
 * ⛔ Se aplica **una vez, sobre el estado**, y no carril a carril ni superficie a superficie: todas
 * las porciones se derivan de aquí, así que el mapa, el peek, la lista, la selección y el encuadre
 * no pueden quedarse en estados distintos. Que la cuenta del peek hiciera shimmer mientras las filas
 * se pintaban asentadas ES este defecto, visto desde la otra punta.
 *
 * ⛔ Y no hace falta ni un composable nuevo: `NearbyRowsSkeleton` ya se dibuja con
 * `isLoading && lista vacía`. No salía porque la lista **no estaba vacía, tenía Italia**. Misma
 * forma que el `takeUnless { isLoading }` de PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001:
 * la frase ya era correcta, le faltaba el dato honesto debajo.
 *
 * ⚠️ Un fallo de red BORRA el ancla (para que cualquier movimiento reintente), así que sin ancla
 * esto responde «no va de aquí» y el carril se vacía. Correcto y deliberado: quien cuenta que no se
 * pudo preguntar es `failed`, no una lista de otro sitio.
 */
internal fun HomeState.laneAnsweringHere(cameraLat: Double?, cameraLon: Double?): HomeState {
    // ⛔ «Vacía» se comprueba ANTES que «de aquí»: una tienda que nadie ha preguntado todavía no
    // tiene ancla, y sin ancla `answerCoversCamera` dice —con razón— que no va de aquí. Sin esta
    // guarda, una lista vacía «se vaciaba» otra vez y el estado se copiaba en cada movimiento de
    // cámara, que es justo lo que [HOME-A-PROJECTION-IS-COMPUTED-ONCE-001] acaba de quitar de en
    // medio. No hay filas de otro sitio que esconder cuando no hay filas.
    val parkingsStale = parkings.parkings.isNotEmpty() && !ViewportRefetch.answerCoversCamera(
        parkings.anchorLat, parkings.anchorLon, cameraLat, cameraLon,
        radiusMeters = PublicParkingsQuery.DEFAULT_RADIUS_METERS, byZone = true,
    )
    val fuelStale = fuel.stations.isNotEmpty() && !ViewportRefetch.answerCoversCamera(
        fuel.anchorLat, fuel.anchorLon, cameraLat, cameraLon,
        radiusMeters = FuelStationsQuery.DEFAULT_RADIUS_METERS,
    )
    if (!parkingsStale && !fuelStale) return this
    return copy(
        parkings = if (parkingsStale) parkings.copy(parkings = emptyList()) else parkings,
        fuel = if (fuelStale) fuel.copy(stations = emptyList()) else fuel,
    )
}

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
/** The cue of a checklist that has nothing left to ask but is still on screen. */
internal const val FIRST_STEPS_CLOSING_CUE = "closing"

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
