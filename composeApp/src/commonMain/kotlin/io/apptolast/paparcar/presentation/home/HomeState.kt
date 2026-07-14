package io.apptolast.paparcar.presentation.home

import androidx.compose.runtime.Immutable
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DrivingPuck
import io.apptolast.paparcar.domain.model.DisabledReason
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
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

    // ── Detection ─────────────────────────────────────────────────────────────

    /** Non-null when a parking event was detected and awaits user confirmation. */
    val pendingParkingGps: GpsPoint? = null,

    /**
     * Readiness of the automatic-detection system, rendered in the persistent top banner.
     * Orthogonal to [mode]: this is *what detection is doing*, not *what the user is doing*.
     * [DET-READY-001g]
     */
    val detectionReadiness: DetectionReadiness = DetectionReadiness.Disabled(DisabledReason.NO_VEHICLE),

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

) {
    // ── Computed properties ───────────────────────────────────────────────────
    // Only cheap, non-allocating lookups live here. List-materialising projections
    // (size-filtered spots, vehicle cards) moved to the per-section slices in
    // HomeSlices.kt so they are built once per state emission, not once per read.
    // [HOME-ATOMIZE-001 F1]

    /** First active session — convenience for code that predates multi-parking. [MULTI-PARKING-001] */
    val userParking: UserParking?
        get() = activeSessions.firstOrNull()

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
}
