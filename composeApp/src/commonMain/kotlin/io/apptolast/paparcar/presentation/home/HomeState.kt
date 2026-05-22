package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.model.ParkedVehicleView
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon

/**
 * Per-vehicle row used by the "TUS VEHÍCULOS" section of the Home sheet.
 * Each registered vehicle gets one entry; [session] is non-null when that
 * vehicle currently has an active parking, null otherwise (→ shows the park
 * CTA in the row). [MULTI-PARKING-001]
 */
data class VehicleCard(
    val vehicle: Vehicle,
    val session: UserParking?,
)

/**
 * Active interaction mode of the Home surface.
 *
 *  - [Browse] (default): user explores spots and their parked car. Sheet
 *    shows the regular item list, selection state applies, map markers
 *    render at full opacity.
 *  - [Reporting]: user is positioning a manual spot report. Centre pin
 *    + dimmed markers + report peek row.
 *  - [AddingZone]: user is positioning a new habitual zone (Casa,
 *    Trabajo…). Same pin + dim affordance as Reporting; only the peek
 *    content and the terminal use case differ.
 *  - [AddingParking]: user is positioning the parked-car pin. Two flavours
 *    distinguished by [HomeState.editingParkingId] — null = create new
 *    session, non-null = move an existing one.
 */
sealed class HomeMode {
    data object Browse : HomeMode()
    data object Reporting : HomeMode()
    data object AddingZone : HomeMode()
    data object AddingParking : HomeMode()
}

/**
 * Estado de la pantalla Home.
 * Representa todos los datos necesarios para renderizar la UI.
 */
data class HomeState(
    val isLoading: Boolean = false,
    val allPermissionsGranted: Boolean = false,
    val nearbySpots: List<Spot> = emptyList(),
    val userGpsPoint: GpsPoint? = null,
    /** LocationInfo for the user's current GPS position (geocoded on-demand, not stored). */
    val userLocationInfo: LocationInfo? = null,
    /**
     * Raw set of currently-active parking sessions, one per parked vehicle.
     * Source of truth for behavioural logic (release, move-location, geofence).
     * The display projection lives in [parkedVehicles]. [MULTI-PARKING-001]
     */
    val activeSessions: List<UserParking> = emptyList(),
    /** Enriched view of all active parking sessions, one per vehicle. */
    val parkedVehicles: List<ParkedVehicleView> = emptyList(),
    /** ID of the currently selected item: either a spot ID, an active parking session ID,
     *  or null for none. Spot IDs and session IDs share the same UUID space so direct equality
     *  resolves which kind without a sentinel. [MULTI-PARKING-001] */
    val selectedItemId: String? = null,
    /** Geocoded address of the map camera centre (updated as the user pans). */
    val cameraLocationInfo: LocationInfo? = null,
    /** True while the camera geocoding flow is in progress (skeleton instead of "Unknown location"). */
    val isCameraGeocoding: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isSearchActive: Boolean = false,
    val isSearching: Boolean = false,
    val mapType: MapType = MapType.NORMAL,
    /** Non-null when a parking event has been detected and awaits user confirmation. */
    val pendingParkingGps: GpsPoint? = null,
    /** Active size filter — null shows all spots; non-null filters to matching sizeCategory (or null). */
    val sizeFilter: VehicleSize? = null,
    /** Active interaction mode — see [HomeMode]. */
    val mode: HomeMode = HomeMode.Browse,
    /** True while a manual spot report is being submitted (CTA spinner). */
    val isReporting: Boolean = false,
    /** True while an Add-Zone confirm is in flight (CTA spinner). */
    val isSavingZone: Boolean = false,
    /** True while an AddingParking confirm (create or edit) is in flight. */
    val isSavingParking: Boolean = false,
    /**
     * When non-null, [HomeMode.AddingParking] is moving the existing session
     * with this id instead of creating a new one. Reset on Browse re-entry.
     */
    val editingParkingId: String? = null,
    /**
     * Vehicle the user is creating a parking session FOR while in
     * [HomeMode.AddingParking]. Null = default vehicle (legacy / single-car
     * flow). Ignored when [editingParkingId] is set — moving a session never
     * changes its owning vehicle. [MULTI-PARKING-001]
     */
    val addingParkingVehicleId: String? = null,
    /**
     * Last camera centre captured while the user is in a pin-positioning mode
     * ([HomeMode.Reporting], [HomeMode.AddingZone] or [HomeMode.AddingParking]).
     * Used at confirm time as the lat/lon for the new spot, zone, or parking
     * location. Cleared on Browse re-entry.
     */
    val pinCameraLat: Double? = null,
    val pinCameraLon: Double? = null,
    /** User's habitual zones (Casa, Trabajo…) shown as chips on the sheet. */
    val zones: List<Zone> = emptyList(),
    /** All registered vehicles for the current user — drives the "TUS VEHÍCULOS" section. [MULTI-PARKING-001] */
    val vehicles: List<Vehicle> = emptyList(),
    /** In-progress AddingZone form fields. Reset on Browse re-entry. */
    val addingZoneName: String = "",
    val addingZoneIconKey: String = ZoneIcon.DEFAULT,
) {
    /**
     * Subset of [nearbySpots] visible after applying [sizeFilter]. Computed
     * property so the filter rule lives in one place (both the sheet list
     * builder and the spot-index lookup used by `scrollToSelectedSpot` read
     * the same list and stay in sync if the rule ever changes).
     *
     * Spots with a null [Spot.sizeCategory] are always included — that
     * matches the "size unknown" fallback so legacy data isn't hidden.
     */
    val filteredNearbySpots: List<Spot>
        get() = if (sizeFilter == null) nearbySpots
                else nearbySpots.filter { it.sizeCategory == null || it.sizeCategory == sizeFilter }

    /**
     * Convenience projection: the "primary" parked session (first by emission order).
     * Existing UI code still reads `userParking` for camera focus / release flows;
     * under multi-parking it points at the first active session. Per-row behaviour
     * lives in [parkedVehicles] / [activeSessions]. [MULTI-PARKING-001]
     */
    val userParking: UserParking?
        get() = activeSessions.firstOrNull()

    /** True when [selectedItemId] matches one of the active parking sessions (vs a community spot). */
    val isParkingSelected: Boolean
        get() = selectedItemId != null && activeSessions.any { it.id == selectedItemId }

    /** True when any item (parking session or community spot) holds the active selection. */
    val hasActiveContent: Boolean
        get() = activeSessions.isNotEmpty() || selectedItemId != null

    /**
     * The currently selected parking session, or null if no parking is selected.
     * Under multi-parking, this is the specific session whose id equals [selectedItemId]
     * — not just the first active session.
     */
    val selectedSession: UserParking?
        get() = selectedItemId?.let { id -> activeSessions.firstOrNull { it.id == id } }

    /**
     * The selected community spot, or null if nothing is selected or the
     * selection is one of the user's own parking sessions. Replaces an O(n)
     * `nearbySpots.find { }` scan that fired on every recomposition of HomeContent.
     */
    val selectedSpot: Spot?
        get() = selectedItemId
            ?.takeIf { id -> activeSessions.none { it.id == id } }
            ?.let { id -> nearbySpots.firstOrNull { it.id == id } }

    /**
     * Per-vehicle projection used by the "TUS VEHÍCULOS" section. One entry per
     * registered vehicle, joined with its active session (if any) by vehicleId.
     * Drives the multi-parking row UI: present → show release/peek affordances,
     * absent → show park CTA. [MULTI-PARKING-001]
     */
    val vehicleCards: List<VehicleCard>
        get() = vehicles.map { v ->
            VehicleCard(
                vehicle = v,
                session = activeSessions.firstOrNull { it.vehicleId == v.id },
            )
        }
}
