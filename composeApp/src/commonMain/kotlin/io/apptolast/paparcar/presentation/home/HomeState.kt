package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon

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
    val userParking: UserParking? = null,
    /** ID of the currently selected item: a spot ID, [PARKING_ITEM_ID], or null for none. */
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
     * Last camera centre captured while the user is in a pin-positioning mode
     * ([HomeMode.Reporting], [HomeMode.AddingZone] or [HomeMode.AddingParking]).
     * Used at confirm time as the lat/lon for the new spot, zone, or parking
     * location. Cleared on Browse re-entry.
     */
    val pinCameraLat: Double? = null,
    val pinCameraLon: Double? = null,
    /** User's habitual zones (Casa, Trabajo…) shown as chips on the sheet. */
    val zones: List<Zone> = emptyList(),
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

    /** True when [selectedItemId] points at the user's parked car (vs a community spot). */
    val isParkingSelected: Boolean
        get() = selectedItemId == PARKING_ITEM_ID

    /**
     * The selected community spot, or null if nothing is selected or the
     * selection is the user's own parking. Replaces an O(n) `nearbySpots.find { }`
     * scan that fired on every recomposition of HomeContent.
     */
    val selectedSpot: Spot?
        get() = selectedItemId?.takeIf { it != PARKING_ITEM_ID }
            ?.let { id -> nearbySpots.firstOrNull { it.id == id } }

    companion object {
        /** Sentinel value used as [selectedItemId] when the user's parked car is selected. */
        const val PARKING_ITEM_ID = "__parking__"
    }
}
