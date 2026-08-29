package com.rndeveloper.paparcar.presentation.map

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle

data class ParkingHistoryState(
    val isLoading: Boolean = true,
    val userLocation: GpsPoint? = null,
    val spots: List<Spot> = emptyList(),
    val userParking: UserParking? = null,
    // Full parking history ordered most-recent → oldest, ALL vehicles interleaved. Raw source only —
    // the stepper never walks this list, it walks [orderedSessions]. [HISTORY-DETAIL-001]
    val allSessions: List<UserParking> = emptyList(),
    // Registered vehicles — used to resolve the focused session's real body shape + paint colour for
    // the modal + map marker (UserParking has no colour of its own). [HISTORY-DETAIL-001]
    val vehicles: List<Vehicle> = emptyList(),
    // Which history entry is on screen. Kept as an id (not the object) so it survives list refreshes
    // and prev/next recomputes deterministically against [orderedSessions]. [HISTORY-DETAIL-001]
    val focusedSessionId: String? = null,
) {
    /**
     * The parking currently shown in the detail sheet + map. Resolved against the FULL history so a
     * deep-link lands on its session whatever vehicle owns it — that session is then what scopes
     * [orderedSessions].
     */
    val focusedSession: UserParking?
        get() = allSessions.firstOrNull { it.id == focusedSessionId }

    /**
     * The stepper's universe: the focused session's OWN vehicle history, most-recent → oldest. The
     * user reaches this screen from a per-vehicle timeline, so ‹/› must stay inside that vehicle
     * instead of interleaving every car by timestamp. Scoped by the focused session's own
     * `vehicleId`, so it holds for any entry point — no vehicle nav-arg needed.
     * [HISTORY-DETAIL-VEHICLE-SCOPE-001]
     *
     * Deliberately NOT narrowed any further (user's call, 20-08-2026): the timeline's week/month
     * filter does NOT carry over — inside the detail you may walk the vehicle's whole history — and
     * the currently-active session counts as one more entry.
     */
    val orderedSessions: List<UserParking>
        get() = focusedSession
            ?.let { focused -> allSessions.filter { it.vehicleId == focused.vehicleId } }
            ?: emptyList()

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
