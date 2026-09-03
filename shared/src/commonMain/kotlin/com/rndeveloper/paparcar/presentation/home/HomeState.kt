package com.rndeveloper.paparcar.presentation.home

import androidx.compose.runtime.Immutable
import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.detection.ServicePresence
import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.model.DrivingPuck
import com.rndeveloper.paparcar.domain.model.DisabledReason
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.FirstStepsProgress
import com.rndeveloper.paparcar.domain.onboarding.resolveFirstSteps
import com.rndeveloper.paparcar.domain.onboarding.resolveWatchReinforcement
import com.rndeveloper.paparcar.presentation.home.model.DetectionUiState
import com.rndeveloper.paparcar.presentation.home.model.ParkedWatchBadge
import com.rndeveloper.paparcar.presentation.home.model.isDetectionStopped
import com.rndeveloper.paparcar.presentation.home.model.resolveParkedWatchBadge
import com.rndeveloper.paparcar.presentation.home.model.toUiState
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.ParkedVehicleSummary
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
    /** False when the user has panned the map away from their GPS position, driving the recenter FAB. */
    val isSpotQueryCenteredOnUser: Boolean = true,

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

    val mapType: MapType = MapType.TERRAIN,
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

    /** Non-null when a parking event was detected and awaits user confirmation. */
    val pendingParkingGps: GpsPoint? = null,

    /**
     * Readiness of the automatic-detection system, rendered in the persistent top banner.
     * Orthogonal to [mode]: this is *what detection is doing*, not *what the user is doing*.
     * [DET-READY-001g]
     */
    val detectionReadiness: DetectionReadiness = DetectionReadiness.Disabled(DisabledReason.NO_VEHICLE),

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
     * mean the departure watch is genuinely live; [ServicePresence.Dead] means it was killed or never
     * (re)started. Drives the honest watch badge so "Vigilando tu sitio" never claims to watch a spot
     * the OS stopped watching. [DET-WATCH-HONEST-001]
     */
    val servicePresence: ServicePresence = ServicePresence.Dead,

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
    val addingZoneName: String = "",
    val addingZoneIconKey: String = ZoneIcon.DEFAULT,
    val addingZoneRadius: Float = Zone.DEFAULT_RADIUS_METERS,
    val addingZoneIsPrivate: Boolean = false,
    /** Non-null when editing an existing zone instead of creating a new one. */
    val editingZoneId: String? = null,

    // ── AddingParking mode ────────────────────────────────────────────────────

    val isSavingParking: Boolean = false,
    val isReleasingParking: Boolean = false,
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
     */
    val addingParkingFromDetectionNudge: Boolean = false,

) {
    // ── Computed properties ───────────────────────────────────────────────────
    // Only cheap, non-allocating lookups live here. List-materialising projections
    // (size-filtered spots, vehicle cards) moved to the per-section slices in
    // HomeSlices.kt so they are built once per state emission, not once per read.
    // [HOME-ATOMIZE-001 F1]

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

    val isParkingSelected: Boolean
        get() = selectedSession != null

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
            // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] "Is it on?" and "is it solid?"
            // are two questions and two steps. This is the second one, about the car the watch
            // actually works for — the active vehicle. [VEH-ACTIVE-FENCE-001]
            reinforcement = resolveWatchReinforcement(
                vehicleHasBluetoothMac = activeVehicle?.bluetoothDeviceId != null,
                hasPairedBluetoothDevices = hasPairedBluetoothDevices,
                isReliabilityReduced = showBatteryOptimizationNudge,
            ),
            deferred = firstStepsDeferred,
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
 * Order of the "TUS VEHÍCULOS" strip: the vehicle being actively monitored floats first (a live
 * trip outranks any parked car), then parked cars by the SAME preference that picks the preferred
 * session (most recent park first, watch rank only breaks ties — never Room's list order), then
 * unparked cars by watch rank. [CHIP-DRIVING-001] [UI-BROWSE-DRIVING-OVER-PARKED-001]
 */
internal fun vehiclesRowOrder(
    cards: List<VehicleCard>,
    drivingVehicleId: String?,
): List<VehicleCard> {
    val parkedPreference = parkedSessionPreference(cards.map { it.vehicle })
    return cards.sortedWith(
        compareByDescending<VehicleCard> { it.isDriving(drivingVehicleId) }
            .thenBy(nullsLast(parkedPreference.reversed())) { it.session }
            .thenBy { it.vehicle.monitoringStatus().sortRank() },
    )
}
