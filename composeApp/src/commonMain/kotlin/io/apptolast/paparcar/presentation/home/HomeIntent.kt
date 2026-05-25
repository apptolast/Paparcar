package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.model.VehicleSize

sealed class HomeIntent {
    data object LoadNearbySpots : HomeIntent()
    data object OpenHistory : HomeIntent()
    data object ReportTestSpot : HomeIntent()
    /**
     * Release the active parking session. When [publishSpot] is true the
     * freed plaza is also published to the community; when false the
     * session is just cleared locally and remotely with no spot report.
     * [PEEK-ACTIONS-001]
     */
    data class ReleaseParking(
        val lat: Double,
        val lon: Double,
        val publishSpot: Boolean = true,
    ) : HomeIntent()
    /** null clears the selection; otherwise the id resolves against active sessions first,
     *  then nearby spots. Spot and session ids share a UUID space, so no sentinel is needed. */
    data class SelectItem(val itemId: String?) : HomeIntent()
    data class CameraPositionChanged(val lat: Double, val lon: Double) : HomeIntent()
    data class SearchQueryChanged(val query: String) : HomeIntent()
    data class SelectSearchResult(val result: SearchResult) : HomeIntent()
    data object ClearSearch : HomeIntent()
    data class SetMapType(val type: MapType) : HomeIntent()
    /** Detection pipeline calls this to trigger the confirmation bottom sheet. */
    data class ShowParkingConfirmation(val gps: GpsPoint) : HomeIntent()
    /** User confirmed the pending parking event (or countdown expired). */
    data object ConfirmDetectedParking : HomeIntent()
    /** User dismissed the confirmation sheet without publishing. */
    data object DismissConfirmation : HomeIntent()
    /** null clears the active size filter; non-null applies it. */
    data class SetSizeFilter(val size: VehicleSize?) : HomeIntent()
    /** Community signal: accepted = "Still there"; !accepted = "Gone". */
    data class SendSpotSignal(val spotId: String, val accepted: Boolean) : HomeIntent()

    /** Enter the manual-spot reporting mode — pin appears on the map, sheet shows the report form. */
    data object EnterReportMode : HomeIntent()

    /** Exit reporting mode without submitting; sheet and map return to Browse. */
    data object ExitReportMode : HomeIntent()

    /** Confirm the report at the current camera centre (fallback to user GPS when camera is unknown). */
    data object ConfirmReportSpot : HomeIntent()

    /** Enter the add-zone mode — same pin + dim affordance as Reporting; peek hosts the name/icon form. */
    data object EnterAddZoneMode : HomeIntent()

    /** Exit add-zone mode without saving; sheet and map return to Browse. */
    data object ExitAddZoneMode : HomeIntent()

    /** Confirm the new zone at the current camera centre, with the in-progress name + icon draft. */
    data object ConfirmAddZone : HomeIntent()

    /** Update the in-progress name draft for the AddingZone form. */
    data class UpdateAddingZoneName(val name: String) : HomeIntent()

    /** Update the in-progress icon draft for the AddingZone form. */
    data class UpdateAddingZoneIcon(val iconKey: String) : HomeIntent()

    /** User tapped a zone chip — moves the camera to the zone. */
    data class SelectZone(val zoneId: String) : HomeIntent()

    /** User long-pressed a zone chip → delete. */
    data class DeleteZone(val zoneId: String) : HomeIntent()

    /** User long-pressed a zone chip → enter edit mode pre-filled with the zone's data. */
    data class EnterEditZoneMode(val zoneId: String) : HomeIntent()

    /**
     * Enter the manual parked-car positioning mode — pin appears at the
     * camera centre. Two flavours:
     *  - **create** (`editingParkingId == null`): `initialGps` defaults to the
     *    user's current GPS; on confirm a new active session is written for
     *    [targetVehicleId] (or the default vehicle when null).
     *  - **edit** (`editingParkingId != null`): `initialGps` is the existing
     *    session's location; on confirm the row is updated in-place — the
     *    `targetVehicleId` is ignored because the session's owner can't change.
     *
     * [targetVehicleId] is the vehicle the user picked when tapping a per-vehicle
     * park CTA in the "TUS VEHÍCULOS" section. [MULTI-PARKING-001]
     */
    data class EnterAddParkingMode(
        val initialGps: GpsPoint?,
        val editingParkingId: String? = null,
        val targetVehicleId: String? = null,
    ) : HomeIntent()

    /** Exit AddingParking mode without saving; sheet and map return to Browse. */
    data object ExitAddParkingMode : HomeIntent()

    /**
     * Confirm the parked-car position at the current camera centre (fallback
     * to user GPS when camera is unknown). The VM dispatches to
     * `ConfirmParkingUseCase` (create) or `UpdateParkingLocationUseCase`
     * (edit) based on `editingParkingId`.
     */
    data object ConfirmAddParking : HomeIntent()
}
