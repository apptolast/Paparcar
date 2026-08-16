package io.apptolast.paparcar.presentation.home

import androidx.compose.runtime.Immutable
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.detection.PendingParkNudge
import io.apptolast.paparcar.domain.detection.ServicePresence
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DrivingPuck
import io.apptolast.paparcar.domain.model.DisabledReason
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
import io.apptolast.paparcar.presentation.home.model.ParkedWatchBadge
import io.apptolast.paparcar.presentation.home.model.resolveParkedWatchBadge
import io.apptolast.paparcar.presentation.home.model.toUiState
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.ParkedVehicleSummary
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon
import io.apptolast.paparcar.domain.model.preferredParkingSession

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
    val phase: io.apptolast.paparcar.domain.detection.DetectionPhase,
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
     * ID of the selected item — either a spot or an active session ID.
     * Both share the same UUID space so equality resolves the type. [MULTI-PARKING-001]
     */
    val selectedItemId: String? = null,

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
     * True when automatic detection is ON but its background survival is fragile on this device —
     * no Bluetooth-paired car, no battery-optimization exemption, and an aggressive OEM (the
     * reliability [io.apptolast.paparcar.domain.model.DetectionReliabilityLevel.REDUCED] case).
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

    /** The session matching [selectedItemId], or null if the selection is a spot. [MULTI-PARKING-001] */
    val selectedSession: UserParking?
        get() = selectedItemId?.let { id -> activeSessions.firstOrNull { it.id == id } }

    /** The selected community spot, or null if nothing is selected or a parking session is selected. */
    val selectedSpot: Spot?
        get() = selectedItemId
            ?.takeIf { id -> activeSessions.none { it.id == id } }
            ?.let { id -> nearbySpots.firstOrNull { it.id == id } }

    val isParkingSelected: Boolean
        get() = selectedItemId != null && activeSessions.any { it.id == selectedItemId }

    /** Presentation projection of [detectionReadiness] for the Home detection surface. [DET-READY-001h] */
    val detectionUiState: DetectionUiState
        get() = detectionReadiness.toUiState()

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
