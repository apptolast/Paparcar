package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle

data class ParkingLocationState(
    val isLoading: Boolean = true,
    val userLocation: GpsPoint? = null,
    val spots: List<Spot> = emptyList(),
    val userParking: UserParking? = null,
    // Full parking history ordered most-recent → oldest. Backs the in-screen prev/next stepper so the
    // user can walk the whole history without leaving the detail map. [HISTORY-DETAIL-001]
    val orderedSessions: List<UserParking> = emptyList(),
    // Registered vehicles — used to resolve the focused session's real body shape + paint colour for
    // the modal + map marker (UserParking has no colour of its own). [HISTORY-DETAIL-001]
    val vehicles: List<Vehicle> = emptyList(),
    // Which history entry is on screen. Kept as an id (not the object) so it survives list refreshes
    // and prev/next recomputes deterministically against [orderedSessions]. [HISTORY-DETAIL-001]
    val focusedSessionId: String? = null,
) {
    /** The parking currently shown in the detail sheet + map, resolved from [orderedSessions]. */
    val focusedSession: UserParking?
        get() = orderedSessions.firstOrNull { it.id == focusedSessionId }

    private val focusedIndex: Int
        get() = orderedSessions.indexOfFirst { it.id == focusedSessionId }

    /** True while there is a more-recent entry to step forward to (right › chevron). */
    val hasNewer: Boolean
        get() = focusedIndex > 0

    /** True while there is an older entry to step back to (left ‹ chevron). */
    val hasOlder: Boolean
        get() = focusedIndex in 0 until orderedSessions.lastIndex

    /** The registered vehicle that owns the focused session, or null if it was deleted / unresolved. */
    val focusedVehicle: Vehicle?
        get() = focusedSession?.vehicleId?.let { id -> vehicles.firstOrNull { it.id == id } }
}
