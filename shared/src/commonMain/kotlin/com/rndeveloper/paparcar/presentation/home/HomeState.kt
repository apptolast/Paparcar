package com.rndeveloper.paparcar.presentation.home

import androidx.compose.runtime.Immutable
import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.detection.ServicePresence
import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.model.DrivingPuck
import com.rndeveloper.paparcar.domain.model.DisabledReason
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.FirstStepsProgress
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.domain.onboarding.resolveFirstSteps
import com.rndeveloper.paparcar.domain.onboarding.resolveWatchReinforcement
import com.rndeveloper.paparcar.presentation.home.model.PublicParkingFilter
import com.rndeveloper.paparcar.presentation.home.model.filteredBy
import com.rndeveloper.paparcar.presentation.home.model.DetectionNotice
import com.rndeveloper.paparcar.presentation.home.model.DetectionUiState
import com.rndeveloper.paparcar.presentation.home.model.ParkedWatchBadge
import com.rndeveloper.paparcar.presentation.home.model.isDetectionStopped
import com.rndeveloper.paparcar.presentation.home.model.resolveParkedWatchBadge
import com.rndeveloper.paparcar.presentation.home.model.toUiState
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.ParkedVehicleSummary
import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.model.ZoneIcon
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.domain.model.parkedSessionPreference
import com.rndeveloper.paparcar.domain.model.preferredParkingSession
import com.rndeveloper.paparcar.domain.model.sortRank
import com.rndeveloper.paparcar.presentation.home.model.FuelLaneSort
import com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.presentation.home.model.HomeParkingsSlice
import com.rndeveloper.paparcar.presentation.home.model.NearbyOrdering

/**
 * Per-vehicle row used by the "TUS VEHÍCULOS" section.
 * [session] is non-null when that vehicle has an active parking session. [MULTI-PARKING-001]
 */
data class VehicleCard(
    val vehicle: Vehicle,
    val session: UserParking?,
)

/**
 * Active interaction mode of the Home surface.
 *
 * - [Browse]: default explore mode — spot list, full-opacity markers.
 * - [Reporting]: manual spot report in progress — centre pin, dimmed markers.
 * - [AddingZone]: habitual zone (Casa, Trabajo…) being positioned.
 * - [AddingParking]: parked-car pin being placed or moved.
 */
sealed class HomeMode {
    data object Browse : HomeMode()
    data object Reporting : HomeMode()
    data object AddingZone : HomeMode()
    data object AddingParking : HomeMode()
}

/** The trip's rarely-changing metadata for the sheet/peek — NOT its fix-rate position (that's in
 *  [HomeViewModel.tripRender]). [DRIVE-PUCK-NATIVE-001] */
@Immutable
data class DrivingMeta(
    val vehicleId: String?,
    val phase: com.rndeveloper.paparcar.domain.detection.DetectionPhase,
)

@Immutable
data class HomeState(

    // ── Loading / permissions ─────────────────────────────────────────────────

    val isLoading: Boolean = false,
    /**
     * CORE permissions (foreground location + notifications) — gates the consumer side (map,
     * spots, filters). PRODUCER (background + AR) is NOT required here; its state lives in
     * [detectionReadiness]. Replaces the old all-or-nothing `allPermissionsGranted`. [DET-READY-001d]
     */
    val hasCorePermissions: Boolean = false,

    /** One-time pitch dialog for auto-detection, shown right after the first-entry location grant
     *  on the map — the app's star feature no longer has a first-run screen to introduce it, so
     *  this is its introduction. Declining is one tap and never returns; the banner and checklist
     *  keep the ask. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001] */
    val showDetectionPitch: Boolean = false,
    /**
     * The KEY of the sheet's auto-open onto the WARNING that leads the detection stories. A counter,
     * not a flag, so it opens once per cause and a sheet dragged back down stays down.
     * [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
     *
     * ⛔ **ONE counter for every cause, and that is the point.** Two causes bump it today:
     *  - the first-entry location ask was **refused** while the location warning applies;
     *  - the departure watch just **died** (`watchInterruptionEarnsAnOpen`).
     *
     * A second counter beside this one would have been a second copy of «rebobina y despliega» — and
     * the day the two drift is the day a warning opens the sheet twice or not at all. A third cause
     * bumps THIS field and writes its own verdict, never its own cue.
     * [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
     */
    val warningOpenCue: Int = 0,
    /**
     * The first-entry location ask has been made at least once on this install — the persisted
     * `AppPreferences.hasRequestedLocationPermission`, mirrored into state so the projections can
     * read it. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
     *
     * Kept RAW next to [locationAnswerInFlight] instead of storing the chewed verdict: the rule that
     * combines them is `homeAwaitsLocationAnswer`, written once, so no surface can spell it its own
     * way. Defaults to `false`, which means the frame before `init` reads the preference stays
     * QUIET — the safe side: better a mute frame than a frame accusing the user of a refusal they
     * have not made.
     */
    val locationEverAsked: Boolean = false,
    /** The first-entry location dialog is up and its answer has not been read yet. This IS the
     *  ViewModel's latch, not a copy of it, so the two cannot disagree — the answer is read off the
     *  map's own foreground bracket. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001] */
    val locationAnswerInFlight: Boolean = false,

    // ── User location ─────────────────────────────────────────────────────────

    val userGpsPoint: GpsPoint? = null,
    val userAddressAndPlace: AddressAndPlace? = null,
    // The live trip render data (driving puck POSITION + trail) is deliberately NOT here — it changes at
    // the GPS fix rate and would recompose the whole Home tree. It lives in its own StateFlow,
    // HomeViewModel.tripRender, collected separately by the map. Only the trip's rarely-changing
    // METADATA (which vehicle, which phase) lives here — the sheet/peek need it and it's deduped so a
    // fix that doesn't change it doesn't recompose. Null when no trip. [DRIVE-PUCK-NATIVE-001]
    val drivingMeta: DrivingMeta? = null,

    // ── Community data ────────────────────────────────────────────────────────

    /** Raw active parking sessions — source of truth for behavioural logic. [MULTI-PARKING-001] */
    val activeSessions: List<UserParking> = emptyList(),
    /** Enriched display projection of active sessions (one row per vehicle). [MULTI-PARKING-001] */
    val parkedVehicles: List<ParkedVehicleSummary> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val zones: List<Zone> = emptyList(),

    // ── Nearby spots ──────────────────────────────────────────────────────────

    val nearbySpots: List<Spot> = emptyList(),
    val sizeFilter: VehicleSize? = null,

    /** El filtro del carril de parkings, o null (lo normal).
     *  [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001] */
    val parkingFilter: PublicParkingFilter? = null,
    // ── Filtros del carril de gasolineras [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001] ──

    /** Con qué criterio se ordena el carril. No esconde ninguna gasolinera. */
    val fuelSort: FuelLaneSort = FuelLaneSort.NEAREST,
    /**
     * El producto por el que se cotiza el carril, cuando el usuario elige uno.
     *
     * `null` = el del coche activo, que es lo de siempre. Existe porque el coche activo no es
     * siempre el que se conduce: quien tiene un diésel de diario y un GLP por Bluetooth necesita
     * poder preguntar por el otro sin cambiar de coche activo (issue #42). Entra por
     * [pricedFuelType], que es la ÚNICA definición de «qué echa este conductor», así que elegir
     * producto requota la fila, la cápsula del mapa y el peek a la vez — igual que cambiar de coche.
     */
    val fuelProduct: FuelType? = null,
    /** False when the user has panned the map away from their GPS position, driving the recenter FAB. */
    val isSpotQueryCenteredOnUser: Boolean = true,
    /**
     * ⛔ **Nadie ha preguntado todavía**: ni hay fix ni el usuario ha llevado el mapa a ningún sitio,
     * así que no hay centro de consulta. La lista está vacía porque no se ha mirado, no porque no
     * haya nada — y el carril dice una cosa muy distinta en cada caso.
     *
     * Por defecto `false` (= preguntado) para que un estado construido a mano en tests, previews o
     * galería siga significando lo que significaba: una respuesta vacía de verdad.
     * [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     */
    val spotsUnasked: Boolean = false,

    // ── Selection ─────────────────────────────────────────────────────────────

    /**
     * What the user has tapped — a spot or one of their own sessions, TYPED. [MULTI-PARKING-001]
     *
     * [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001] It used to be a bare id whose kind was recovered
     * by asking both lists, with the session winning ties. A freed spot deliberately reuses its
     * session's id, so that tie is not a rare race — it is every deduced departure.
     */
    val selection: HomeSelection? = null,

    // ── Map / camera ──────────────────────────────────────────────────────────

    val mapType: MapType = MapType.NORMAL,
    /** Geocoded address of the map camera centre (updated as the user pans). */
    val cameraAddressAndPlace: AddressAndPlace? = null,
    /** True while camera geocoding is in flight — drives a skeleton placeholder. */
    val isCameraGeocoding: Boolean = false,

    // ── Search ────────────────────────────────────────────────────────────────

    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isSearchActive: Boolean = false,
    val isSearching: Boolean = false,
    /** The last search finished fine but found nothing — drives the "no results" row under the
     *  bar, so an empty dropdown never reads the same as a geocoder failure. [UX-PARK-FLOW-001 H3] */
    val searchNoResults: Boolean = false,

    // ── Detection ─────────────────────────────────────────────────────────────

    /**
     * Readiness of the automatic-detection system, rendered in the persistent top banner.
     * Orthogonal to [mode]: this is *what detection is doing*, not *what the user is doing*.
     * [DET-READY-001g]
     */
    val detectionReadiness: DetectionReadiness = DetectionReadiness.Disabled(DisabledReason.NO_VEHICLE),

    /**
     * Los avisos de salud del sistema que el usuario ha callado con la × del badge.
     * [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001]
     *
     * ⛔ Vive en el ESTADO y no en disco a propósito: un permiso que falta deja la app rota, y
     * persistir el silencio convertiría un «ahora no» en un «nunca más». Al volver a abrir, el aviso
     * vuelve — porque el problema sigue ahí.
     */
    val silencedNotices: Set<DetectionNotice> = emptySet(),

    /**
     * The unanswered "where did you leave your car?" nudge, or null. Rendered as a persistent
     * action row until the user marks a parking or dismisses it — the notification alone does
     * not survive being slept through. [DET-NUDGE-PERSIST-001]
     */
    val pendingParkNudge: PendingParkNudge? = null,

    /**
     * [DET-ASK-STATE-001] The "did you park?" question currently posted and STILL ANSWERABLE, or
     * null. Already filtered for expiry by the ViewModel (which owns the clock), so every consumer
     * downstream can read "not null" as "there is something to answer right now".
     *
     * Rendered as the top action row so a user who opens the app during the window can resolve it
     * without going to the notification shade — the case that silently timed out on 2026-07-25.
     */
    val promptWindow: PendingPromptWindow? = null,

    /**
     * True when automatic detection is ON but its background survival is fragile on this device —
     * no Bluetooth-paired car, no battery-optimization exemption, and an aggressive OEM (the
     * reliability [com.rndeveloper.paparcar.domain.model.DetectionReliabilityLevel.REDUCED] case).
     * Drives the persistent Home nudge that re-requests the battery exemption. A Bluetooth car never
     * reaches REDUCED, so this stays false for the strategy that doesn't need it.
     * [DET-BATTERY-EXEMPTION-NUDGE-001]
     */
    val showBatteryOptimizationNudge: Boolean = false,

    /**
     * Real lifecycle of the Coordinator foreground service — [ServicePresence.Sentry]/[ServicePresence.Active]
     * mean the departure watch is genuinely live; [ServicePresence.Dead] means it was killed. Drives
     * the honest watch badge so "Vigilando tu sitio" never claims to watch a spot the OS stopped
     * watching. [DET-WATCH-HONEST-001]
     *
     * ⛔ **Arranca en [ServicePresence.Unknown], no en `Dead`.** Decía `Dead` y eso hacía que Home
     * acusara al sistema de haber matado al vigilante en **cada arranque en frío** con coche
     * aparcado — antes de que el servicio hubiera tenido ocasión de decir nada.
     * [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001]
     */
    val servicePresence: ServicePresence = ServicePresence.Unknown,

    // ── Guided first steps. [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] ──

    /** Steps the checklist has banked, straight from `AppPreferences`. Only the RAW persisted facts
     *  live in the state; which step is current is a projection ([firstSteps]), so the rule sits in
     *  one testable function instead of in whoever last touched the ViewModel. */
    val firstStepsDone: Set<FirstStep> = emptySet(),
    /**
     * Whether the checklist has been skipped or closed. Defaults to **true**: before preferences
     * resolve, the honest answer is "show nothing". Defaulting to false would flash a first-run
     * tutorial at every returning user for one frame on every cold start.
     */
    val firstStepsDismissed: Boolean = true,
    /** Steps answered with "not yet" — persisted, and NEVER folded into [firstStepsDone]: a declined
     *  permission is not an accomplishment. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] */
    val firstStepsDeferred: Set<FirstStep> = emptySet(),
    /** The phone has at least one bonded Bluetooth device, so linking a car in Paparcar is picking
     *  from a list instead of landing on an empty screen. Read once by the ViewModel; false while
     *  the permission is missing or Bluetooth is off, which is the honest answer for this question.
     *  [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] */
    val hasPairedBluetoothDevices: Boolean = false,

    // ── Mode ──────────────────────────────────────────────────────────────────

    val mode: HomeMode = HomeMode.Browse,

    /**
     * Camera lat/lon captured while the user is in a pin-positioning mode
     * (Reporting, AddingZone, AddingParking). Used as the confirmed coordinate.
     * Cleared when returning to Browse.
     */
    val pinCameraLat: Double? = null,
    val pinCameraLon: Double? = null,
    /**
     * True while the map camera is in motion (user drag or programmatic move)
     * during a pin-positioning mode. Confirm buttons are disabled while true
     * to prevent confirming a coordinate that hasn't settled yet.
     */
    val isCameraMoving: Boolean = false,

    // ── Reporting mode ────────────────────────────────────────────────────────

    val isReporting: Boolean = false,
    /** Size the user selected for the spot they are manually reporting. Null = unknown. */
    val reportingSize: VehicleSize? = null,

    // ── AddingZone mode ───────────────────────────────────────────────────────

    val isSavingZone: Boolean = false,
    /** Ids of zones whose delete write is in flight. Prevents double-tap firing
     *  the destructive write twice — UX is already optimistic (the chip vanishes
     *  on success because the Flow stops emitting it). */
    val deletingZoneIds: Set<String> = emptySet(),
    /** Ids of spots whose accept/reject signal is in flight. Prevents double-tap
     *  on the small thumbs-up/down buttons in the spot peek. */
    val inFlightSpotSignals: Set<String> = emptySet(),
    /**
     * Spots this session has already voted on — the buttons stop being offered for them.
     * [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001]
     *
     * In memory on purpose, not persisted. A vote is bounded by its own consequences: a second
     * "gone" on an already-retracted spot is a no-op, and repeated "still there" cannot outlive the
     * spot because refreshing its age never touches `expiresAt`. A Room table to guard counters
     * that no pixel reads would be schema cost for nothing.
     */
    val votedSpotIds: Set<String> = emptySet(),
    /**
     * `source|id` of the forecourts the user follows.
     * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
     *
     * A standing subscription like the free spots, not a lane answer: it is read from the user's own
     * store and survives the fuel lane being emptied, panned away from, or never opened.
     */
    val followedFuelStationKeys: Set<String> = emptySet(),
    val addingZoneName: String = "",
    val addingZoneIconKey: String = ZoneIcon.DEFAULT,
    val addingZoneRadius: Float = Zone.DEFAULT_RADIUS_METERS,
    val addingZoneIsPrivate: Boolean = false,
    /** Plus zone-alert opt-in of the zone being created/edited. [MONEY-001] */
    val addingZoneNotify: Boolean = false,
    /** Non-null when editing an existing zone instead of creating a new one. */
    val editingZoneId: String? = null,

    // ── Paparcar Plus [MONEY-001] ─────────────────────────────────────────────

    /** Live entitlement — decides whether Plus surfaces open or route to the paywall. */
    val isPlus: Boolean = false,
    /** The bell's zone-alert manager dialog (Plus only; free lands on the paywall instead). */

    // ── AddingParking mode ────────────────────────────────────────────────────

    /**
     * WHICH confirm is in flight, not merely that one is:
     * `false` = correct this session's pin in place, `true` = re-park (a NEW session for the same
     * car). Both live in the same peek, so a bare boolean could only ever put the spinner on the
     * first of the two buttons — the one the user may not have pressed.
     * [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
     */
    val savingParkingAsNewSession: Boolean? = null,
    /**
     * WHICH release is in flight. The reason already travelled in
     * [HomeIntent.ReleaseParking]; keeping it here is what lets the release dialog paint the
     * spinner on the option the user chose. And the two options are not interchangeable — one
     * publishes the freed spot for the community and the other does not, so a spinner on the wrong
     * one states the opposite of the decision just taken.
     * [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
     */
    val releasingReason: ParkingReleaseReason? = null,
    /**
     * The session the release dialog is asking about; null = the dialog is closed. It lives here
     * and not in the screen because the release ends it in the SAME atomic update that clears
     * [releasingReason]. The screen used to close the dialog by watching `isReleasingParking` go
     * true → false, and a release faster than a frame never showed the `true`: the dialog stayed
     * open over a session that was already gone, and every further tap "did nothing".
     * [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001]
     */
    val releaseDialogSessionId: String? = null,
    val correctionDialogSessionId: String? = null,
    val stillParkedQuestion: com.rndeveloper.paparcar.domain.detection.StillParkedQuestion? = null,
    val correctingParking: com.rndeveloper.paparcar.domain.usecase.parking.ParkingCorrection? = null,
    /**
     * Non-null when moving an existing session; null when creating a new one.
     * When set, [addingParkingVehicleId] is ignored. [MULTI-PARKING-001]
     */
    val editingParkingId: String? = null,
    /** Vehicle the new session is created for. Null → default vehicle. [MULTI-PARKING-001] */
    val addingParkingVehicleId: String? = null,
    /**
     * True when this pin mode was entered from a DETECTION nudge ("Marcar mi plaza" notification /
     * sheet row): the confirmed pin keeps detection provenance (`AUTO_DETECTED`, path `nudge`)
     * instead of being stamped a manual report. [DET-NUDGE-PIN-PROVENANCE-001]
     *
     * [DET-A-NUDGE-MUST-SAY-WHO-ASKED-001] Holds WHICH lane asked rather than merely that one did:
     * null = nobody asked (a hand-placed pin), empty = asked but the source is no longer on record,
     * otherwise the asking lane. One field because it is one fact — a boolean beside a string is two
     * that can disagree.
     */
    val addingParkingNudgeSource: String? = null,

    /**
     * Which nearby subject the sheet's lane is showing. ONE lane at a time, so no subject sits
     * behind another's list. [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
     *
     * Session-only and always [HomeNearbyLens.SPOTS] on a cold start: Home is the AHORA.
     */
    val nearbyLens: HomeNearbyLens = HomeNearbyLens.SPOTS,

    /**
     * The complements whose provenance the user has already been told, straight from preferences.
     * [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
     *
     * Persisted and user-scoped: it feeds BOTH the lane's first-visit line and
     * [FirstStep.EXPLORE_NEARBY]'s live signal, so "was I told?" has exactly one answer.
     */
    val introducedComplements: Set<NearbyComplement> = emptySet(),

    /**
     * The complement being INTRODUCED right now, or null. Session-only, recomputed on every lane
     * change. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
     *
     * A separate field from [introducedComplements] because the two answer different questions and
     * flip at the same instant: entering a lane for the first time BANKS the complement (so it is
     * never introduced twice) while the line has to stay up for the whole visit. Deriving the line
     * from the set alone would make it appear and vanish within the same frame.
     */
    val nearbyIntro: NearbyComplement? = null,

    /**
     * "Where to refuel", unasked until its lane is entered. [FUEL-STATIONS-LIST-001]
     *
     * Its own value rather than four loose fields on the state: the lane has to keep
     * "nothing within 5 km" and "we could not ask" apart, and a pair of booleans beside a list
     * is two facts that can disagree.
     *
     * ⛔ The ONLY fuel store — lane, map, peek and selection read it through [fuelStationsShown].
     * [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
     */
    val fuel: HomeFuelSlice = HomeFuelSlice(),

    /** "Car parks nearby" — the ONLY car-park store, read through [publicParkingsShown].
     *  [PARKINGS-ON-THE-MAP-001] [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001] */
    val parkings: HomeParkingsSlice = HomeParkingsSlice(),

) {
    // ── Computed properties ───────────────────────────────────────────────────
    // Only cheap, non-allocating lookups live here. List-materialising projections
    // (size-filtered spots, vehicle cards) moved to the per-section slices in
    // HomeSlices.kt so they are built once per state emission, not once per read.
    // [HOME-ATOMIZE-001 F1]

    /** "A confirm is running" — for the guards that only need to know THAT, not which one.
     *  [savingParkingAsNewSession] is what a button reads to decide whether the spinner is its. */
    val isSavingParking: Boolean
        get() = savingParkingAsNewSession != null

    /** "A release is running" — same contract as [isSavingParking], over [releasingReason]. */
    val isReleasingParking: Boolean
        get() = releasingReason != null

    /** The user's PREFERRED session under multi-parking — see [preferredSession]. [MULTI-PARKING-001] */
    val userParking: UserParking?
        get() = preferredSession(activeSessions, vehicles)

    /** The selected session, or null when the selection is a spot / stale. [MULTI-PARKING-001] */
    val selectedSession: UserParking?
        get() = (selection as? HomeSelection.Parking)
            ?.let { sel -> activeSessions.firstOrNull { it.id == sel.id } }

    /** The selected community spot, or null when the selection is a session / stale.
     *  [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001] Each side now resolves inside its OWN type, so
     *  a spot sharing its session's id is reachable instead of being shadowed by it. */
    val selectedSpot: Spot?
        get() = (selection as? HomeSelection.Spot)
            ?.let { sel -> nearbySpots.firstOrNull { it.id == sel.id } }

    /**
     * The fuel every figure on this screen is quoting. [MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001]
     *
     * ⛔ The one definition of "what this driver puts in", read by the map, the peek AND the
     * view model's own ask — which used to keep a second copy of these two lines. Two definitions of
     * the same thing is how the map ended up labelling one fuel and pricing another.
     *
     * ⛔ The PUMP PRODUCT, not the powertrain: what a driver puts in a hybrid is petrol, and every
     * feed is indexed by what a forecourt sells. Asking with HYBRID matched nothing, so the row and
     * the capsule went blank while the peek — which asks with products — quoted both pumps on the
     * same screen. Mapping HERE and not lower down is what keeps the figure and its label sharing
     * one source: `fuelPricesFor` reads this. [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
     */
    val pricedFuelType: FuelType?
        get() = fuelProduct
            ?: vehiclesByRecentUse.firstNotNullOfOrNull { it.fuelType?.takeIf(FuelType::usesLiters) }
                ?.pumpProduct
            ?: vehiclesByRecentUse.firstNotNullOfOrNull { it.fuelType?.pumpProduct }

    /**
     * Los coches de este conductor **por uso reciente**, en el MISMO orden que la tira de «TUS
     * VEHÍCULOS» y que el stepper del peek. [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001]
     *
     * ⛔ **Uso reciente, no la bandera `isActive`** (user, review de #42). El coche «activo» es el
     * que está marcado, y marcar no es conducir: quien tiene un diésel de diario y un GLP por
     * Bluetooth veía el carril cotizado por el que dejó marcado hace un mes. El orden de
     * [vehiclesRowOrder] ya contesta «cuál es el de ahora» —conduciendo primero, luego por sesión
     * aparcada más reciente, luego por vigilancia— y es el que el usuario ya lee en las otras dos
     * superficies, así que reutilizarlo evita que tres sitios discrepen sobre cuál es su coche.
     *
     * ⛔ Y se busca el primero que **eche litros** (`usesLiters`), no el primero a secas: con un
     * eléctrico arriba, quedarse en él dejaría el carril cotizando un producto que ninguna
     * gasolinera vende, teniendo el conductor otro coche que sí reposta. ⚠️ Cuidado con
     * `pumpProduct`: NO es nulo para `ELECTRIC` —se devuelve a sí mismo— así que no sirve para
     * saltárselo; hay que preguntar por `usesLiters`. El segundo `?:` conserva el comportamiento de
     * siempre para un garaje entero de eléctricos, donde no hay nada mejor que ofrecer.
     */
    internal val vehiclesByRecentUse: List<Vehicle>
        get() = vehiclesRowOrder(
            cards = vehicles.map { v ->
                VehicleCard(vehicle = v, session = activeSessions.firstOrNull { it.vehicleId == v.id })
            },
            drivingVehicleId = drivingMeta?.vehicleId,
        ).map { it.vehicle }

    /**
     * Los productos que ESTA respuesta cotiza de verdad, en orden fijo.
     * [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001]
     *
     * ⛔ Los chips ofrecen lo que hay, no la lista de tipos de combustible que existen en el mundo.
     * Un chip «GLP» en una comarca donde ninguna gasolinera lo vende es una promesa que al pulsarla
     * se convierte en una lista vacía, y una lista vacía es un veredicto sobre el mundo que no
     * hemos medido. Si el feed no lo cotiza, el chip no está.
     */
    val fuelProductsAvailable: List<FuelType>
        get() = FuelType.entries
            .filter { type -> fuel.stations.any { type in it.station.prices.keys } }

    /**
     * The stations the lane, the map, the peek, its stepper and the selection all read: the ONE
     * store, **priced for the car you drive**, priced first, distances from the user.
     * [MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001] [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
     *
     * ⛔ A price is a property of the FORECOURT, never of the request — `FuelStation.prices` carries
     * everything that station is known to charge — so it is resolved here, and switching the active
     * car requotes every surface without asking again.
     *
     * ⛔ `by lazy`, no `get()`: **una proyección se calcula una vez por estado, no una vez por
     * lectura**. La leen el mapa, el peek, la lista y la selección, así que con `get()` cada
     * emisión de estado ordenaba la tienda entera cuatro veces —y eso ocurre en el frame que el
     * usuario está esperando para ver su chip marcado.
     * [HOME-A-PROJECTION-IS-COMPUTED-ONCE-001]
     */
    val fuelStationsShown: List<NearbyFuelStation> by lazy {
        NearbyOrdering.fuelStations(fuel.stations, pricedFuelType, userGpsPoint, fuelSort)
    }

    /**
     * Lo que el MAPA pinta: el mismo conjunto, con los mismos precios y las mismas distancias, pero
     * **sin el orden del carril**.
     *
     * ⛔ El mapa leía `fuelStationsShown`, y por tanto heredaba el orden. Un pin no tiene fila ni
     * scroll: cambiar de «cercanas» a «baratas» no mueve un solo marcador de sitio, pero entregaba
     * una lista distinta a la clave `remember` que construye los marcadores, y reconstruía los
     * cientos que hay para no cambiar nada. Un consumidor no debe depender de algo que no puede
     * mostrar. [MAP-THE-MAP-READS-THE-SET-NOT-THE-ORDER-001]
     */
    val fuelStationsOnMap: List<NearbyFuelStation> by lazy {
        NearbyOrdering.fuelStationsPriced(fuel.stations, pricedFuelType, userGpsPoint)
    }

    /** The car parks every surface reads: the ONE store, nearest to the user first.
     *  [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001] [HOME-A-PROJECTION-IS-COMPUTED-ONCE-001] */
    /**
     * El coche contra el que se mide el gálibo de un parking: el MISMO «cuál es el de ahora» que
     * cotiza el carril de gasolineras, no la bandera `isActive`.
     * [VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001] [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001]
     *
     * ⛔ Dos superficies preguntando «¿cuál es tu coche?» y contestándose distinto es exactamente
     * cómo se llega a un carril cotizado para el diésel y un filtro medido para la furgoneta.
     */
    private val clearanceVehicle: Vehicle?
        get() = vehiclesByRecentUse.firstOrNull()

    val publicParkingsShown: List<NearbyPublicParking> by lazy {
        NearbyOrdering.publicParkings(parkings.parkings, userGpsPoint).filteredBy(parkingFilter, clearanceVehicle)
    }

    /** Los parkings del MAPA: el mismo conjunto FILTRADO —el filtro sí decide cuáles existen para
     *  ti, y eso el mapa sí lo enseña— sin el orden de lectura.
     *  [MAP-THE-MAP-READS-THE-SET-NOT-THE-ORDER-001] */
    val publicParkingsOnMap: List<NearbyPublicParking> by lazy {
        NearbyOrdering.publicParkingsMeasured(parkings.parkings, userGpsPoint).filteredBy(parkingFilter, clearanceVehicle)
    }

    /** Había parkings ANTES de filtrar. Es lo que separa «aquí no hay ninguno» de «ninguno cumple
     *  lo que has pedido», que son dos frases distintas y sólo una es un veredicto sobre el mundo.
     *  [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001] */
    val hasAnyPublicParking: Boolean
        get() = parkings.parkings.isNotEmpty()

    /** The selected fuel station, or null when the selection is another kind / stale. Resolves
     *  inside its OWN arm, like the other two, and against the same repriced list everything else
     *  reads. [MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001] */
    val selectedFuelStation: NearbyFuelStation?
        get() = (selection as? HomeSelection.FuelStation)?.let { sel ->
            fuelStationsShown.firstOrNull { it.station.id == sel.id }
        }

    /** The selected public car park, resolved inside its OWN arm. [PARKINGS-ON-THE-MAP-001] */
    val selectedPublicParking: NearbyPublicParking?
        get() = (selection as? HomeSelection.PublicParking)?.let { sel ->
            publicParkingsShown.firstOrNull { it.parking.id == sel.id }
        }

    val isParkingSelected: Boolean
        get() = selectedSession != null

    // ── What the map paints of the two complements ────────────────────────────
    // The LANE decides, and nothing else does. [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
    //
    // These replaced two persisted switches (`showFuelStationsLayer`, `showPublicParkingsLayer`) fed
    // by a layers menu on the map. Two controls for one subject is what let the map paint price
    // capsules while the sheet listed car parks — and, with both switches on, stack two label layers
    // over the spot pins that ARE the product. Now the answer to "what is on the map" is the answer to
    // "what am I looking at", which cannot disagree with itself.
    //
    // ⛔ Only the COMPLEMENTS are governed this way. Free spots, your parked cars, your zones and the
    // live trip are painted always: they were never layers, and nothing the user picks here hides
    // their own car.

    /** True while the lane is fuel — the only state in which price capsules exist. */
    val showsFuelOnMap: Boolean
        get() = nearbyLens == HomeNearbyLens.FUEL

    /** True while Aparcar is active — its second block is the public-parking source. */
    val showsParkingsOnMap: Boolean
        get() = nearbyLens == HomeNearbyLens.SPOTS || nearbyLens == HomeNearbyLens.PARKINGS

    /** Presentation projection of [detectionReadiness] for the Home detection surface. [DET-READY-001h] */
    val detectionUiState: DetectionUiState
        get() = detectionReadiness.toUiState()

    /**
     * Where the user stands in the guided first steps. [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
     *
     * Every live signal is read from the state the rest of Home already trusts, so a step can never
     * be ticked by something the app is not really doing:
     * - [FirstStep.UNDERSTAND_WATCH] rides on the HONEST badge — a killed foreground service reads
     *   [ParkedWatchBadge.WATCH_INTERRUPTED] and the step stays open, which is the truth.
     *   [DET-WATCH-HONEST-001]
     * - [FirstStep.FIND_SPOT] completes on having a community spot OPEN, not on having scrolled
     *   past one; the ViewModel banks it so closing the peek does not undo it. Its FACE (show spots
     *   vs ask for one) rides on [spotsOnOffer] — the very list the sheet's free-spots section
     *   renders, so the step cannot offer to show something that is not there.
     *   [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
     */
    val firstSteps: FirstStepsProgress
        get() = resolveFirstSteps(
            done = firstStepsDone,
            dismissed = firstStepsDismissed,
            hasActiveSession = activeSessions.isNotEmpty(),
            isWatching = parkedWatchBadge == ParkedWatchBadge.WATCHING,
            hasTouchedSpots = selection is HomeSelection.Spot,
            hasSpotsOnOffer = spotsOnOffer.isNotEmpty(),
            // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] Step 2 describes a departure
            // watch. With detection stopped that description would be a promise the app is not
            // keeping, so the step switches to asking for it — read from the SAME flag the release
            // dialog uses to decide whether it can promise the same thing. [DET-WATCH-HONEST-001]
            isAutoDetectionStopped = detectionUiState.isDetectionStopped,
            // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] Having been INTO a complement's lane is the
            // step. Read from the same persisted set the lanes use to know whether they still owe
            // their provenance line — one fact, so the step cannot claim a lesson never given.
            hasExploredNearby = introducedComplements.isNotEmpty(),
            // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] "Is it on?" and "is it solid?"
            // are two questions and two steps. This is the second one, about the car the watch
            // actually works for — the active vehicle. [VEH-ACTIVE-FENCE-001]
            reinforcement = resolveWatchReinforcement(
                vehicleHasBluetoothMac = activeVehicle?.bluetoothDeviceId != null,
                hasPairedBluetoothDevices = hasPairedBluetoothDevices,
                isReliabilityReduced = showBatteryOptimizationNudge,
            ),
            deferred = firstStepsDeferred,
            // [FIRST-STEPS-A-PRIVATE-GARAGE-IS-A-PRIVATE-ZONE-001] The zone the step asks for, read
            // from the same list the header chips render.
            hasPrivateZone = zones.any { it.isPrivate },
        )

    /** The vehicle the watch works for: the flagged active one, else the only sensible fallback —
     *  the same resolution the cold-start CTAs use. [VEH-ACTIVE-FENCE-001] */
    private val activeVehicle: Vehicle?
        get() = vehicles.firstOrNull { it.isActive } ?: vehicles.firstOrNull()

    /**
     * Honest status badge for the parked/active vehicle's departure watch — real, not aspirational.
     * Only meaningful in the parked / awaiting-first-park context (the other detection states own
     * their own surfaces); null elsewhere. [DET-WATCH-HONEST-001]
     */
    val parkedWatchBadge: ParkedWatchBadge?
        get() = when (detectionUiState) {
            DetectionUiState.Parked, DetectionUiState.AwaitingFirstPark -> resolveParkedWatchBadge(
                hasParkedSession = userParking != null,
                isBluetoothCovered = userParking?.vehicleId
                    ?.let { id -> vehicles.firstOrNull { it.id == id }?.bluetoothDeviceId != null }
                    ?: false,
                presence = servicePresence,
                isReliabilityReduced = showBatteryOptimizationNudge,
            )
            else -> null
        }
}

/**
 * The session that stands in for "the user's parking" when nothing is selected —
 * initial camera focus, Browse peek subject, midpoint FAB, release fallback.
 * Delegates to the single domain resolver so Home and the detection banner can
 * never disagree about which car represents the user.
 * [UI-PREFERRED-SESSION-RECENCY-001]
 */
internal fun preferredSession(
    activeSessions: List<UserParking>,
    vehicles: List<Vehicle>,
): UserParking? = preferredParkingSession(activeSessions, vehicles)

/** The card whose trip is being detected RIGHT NOW (driving, not yet parked). [CHIP-DRIVING-001] */
internal fun VehicleCard.isDriving(drivingVehicleId: String?): Boolean =
    session == null && drivingVehicleId != null && vehicle.id == drivingVehicleId

/**
 * Order of the "TUS VEHÍCULOS" strip: the car the app has something to SAY about floats first, then
 * the vehicle being actively monitored (a live trip outranks any parked car), then parked cars by
 * the SAME preference that picks the preferred session (most recent park first, watch rank only
 * breaks ties — never Room's list order), then unparked cars by watch rank.
 * [CHIP-DRIVING-001] [UI-BROWSE-DRIVING-OVER-PARKED-001]
 *
 * [subjectVehicleId] is the FIRST key, and it had to go above `isDriving`
 * [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001]: the hoist already existed, but it asked *"is this car
 * driving?"*, and **an open question is not a confirmed drive** — the ask arrives once the trip is
 * over. Measured 08-10 in the Dev Catalog with three cars: the car being asked about came THIRD,
 * behind two that had nothing to say. A car that is driving is still its own subject, so nothing
 * that used to float stops floating; the key is generalised, not replaced.
 *
 * Null subject (system-health stories, or none at all) leaves the previous order untouched.
 */
internal fun vehiclesRowOrder(
    cards: List<VehicleCard>,
    drivingVehicleId: String?,
    subjectVehicleId: String? = null,
): List<VehicleCard> {
    val parkedPreference = parkedSessionPreference(cards.map { it.vehicle })
    return cards.sortedWith(
        compareByDescending<VehicleCard> { subjectVehicleId != null && it.vehicle.id == subjectVehicleId }
            .thenByDescending { it.isDriving(drivingVehicleId) }
            .thenBy(nullsLast(parkedPreference.reversed())) { it.session }
            .thenBy { it.vehicle.monitoringStatus().sortRank() },
    )
}
