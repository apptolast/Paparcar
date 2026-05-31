package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.model.VehicleSize

sealed class HomeIntent {

    // ── Map & navigation ──────────────────────────────────────────────────────

    data class CameraPositionChanged(val lat: Double, val lon: Double) : HomeIntent()
    /** Reset the spot query centre to the user's GPS and move the camera there. */
    data object RecenterSpots : HomeIntent()
    data class SetMapType(val type: MapType) : HomeIntent()

    // ── Spot interactions ─────────────────────────────────────────────────────

    data object LoadNearbySpots : HomeIntent()
    /** null clears the selection; a spot or session id sets it. */
    data class SelectItem(val itemId: String?) : HomeIntent()
    /** null clears the active size filter; non-null restricts to that size. */
    data class SetSizeFilter(val size: VehicleSize?) : HomeIntent()
    /** "Still there" (accepted = true) / "Gone" (accepted = false) community signal. */
    data class SendSpotSignal(val spotId: String, val accepted: Boolean) : HomeIntent()

    // ── Reporting mode ────────────────────────────────────────────────────────

    /** Enter reporting mode anchored at the map's current centre. */
    data class EnterReportMode(val lat: Double, val lon: Double) : HomeIntent()
    data object ExitReportMode : HomeIntent()
    data object ConfirmReportSpot : HomeIntent()

    // ── Detection lifecycle ───────────────────────────────────────────────────

    /** Detection pipeline signals a parking event — shows the confirmation sheet. */
    data class ShowParkingConfirmation(val gps: GpsPoint) : HomeIntent()
    data object ConfirmDetectedParking : HomeIntent()
    data object DismissConfirmation : HomeIntent()

    // ── Parking lifecycle ─────────────────────────────────────────────────────

    /**
     * Release the active parking session. [publishSpot] = true also reports
     * the freed plaza to the community. [PEEK-ACTIONS-001]
     */
    data class ReleaseParking(
        val lat: Double,
        val lon: Double,
        val publishSpot: Boolean = true,
    ) : HomeIntent()

    /**
     * Enter manual parked-car positioning mode.
     * - [editingParkingId] null → create session for [targetVehicleId] (or default vehicle).
     * - [editingParkingId] non-null → move the existing session; [targetVehicleId] is ignored.
     * [MULTI-PARKING-001]
     */
    data class EnterAddParkingMode(
        val initialGps: GpsPoint?,
        val editingParkingId: String? = null,
        val targetVehicleId: String? = null,
    ) : HomeIntent()

    data object ExitAddParkingMode : HomeIntent()
    /** Confirm the parked-car position at the current camera centre (falls back to user GPS). */
    data object ConfirmAddParking : HomeIntent()

    // ── Zone management ───────────────────────────────────────────────────────

    /** Enter zone-placement mode anchored at the map's current centre. */
    data class EnterAddZoneMode(val lat: Double, val lon: Double) : HomeIntent()
    data object ExitAddZoneMode : HomeIntent()
    data object ConfirmAddZone : HomeIntent()
    data class UpdateAddingZoneName(val name: String) : HomeIntent()
    data class UpdateAddingZoneIcon(val iconKey: String) : HomeIntent()
    data class SelectZone(val zoneId: String) : HomeIntent()
    data class DeleteZone(val zoneId: String) : HomeIntent()
    data class EnterEditZoneMode(val zoneId: String) : HomeIntent()

    // ── Search ────────────────────────────────────────────────────────────────

    data class SearchQueryChanged(val query: String) : HomeIntent()
    data class SelectSearchResult(val result: SearchResult) : HomeIntent()
    data object ClearSearch : HomeIntent()

    // ── Debug ─────────────────────────────────────────────────────────────────

    data object ReportTestSpot : HomeIntent()
}
