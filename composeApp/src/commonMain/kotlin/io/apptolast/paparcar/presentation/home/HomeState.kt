package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize

/**
 * Active interaction mode of the Home surface.
 *
 *  - [Browse] (default): user explores spots and their parked car. Sheet
 *    shows the regular item list, selection state applies, map markers
 *    render at full opacity.
 *  - [Reporting]: user is positioning a manual spot report. Sheet shows
 *    the report form (title / subtitle / address / CTA), nearby markers
 *    dim into the background, and a static centre pin indicates where
 *    the new spot will land.
 */
sealed class HomeMode {
    data object Browse : HomeMode()
    data object Reporting : HomeMode()
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
    /** Last camera centre captured for the report flow — used when [mode] is [HomeMode.Reporting]. */
    val reportCameraLat: Double? = null,
    val reportCameraLon: Double? = null,
) {
    companion object {
        /** Sentinel value used as [selectedItemId] when the user's parked car is selected. */
        const val PARKING_ITEM_ID = "__parking__"
    }
}
