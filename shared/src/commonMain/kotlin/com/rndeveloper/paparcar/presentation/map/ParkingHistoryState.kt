package com.rndeveloper.paparcar.presentation.map

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle

/**
 * What the screen knows about the parking it was asked to show.
 * [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
 *
 * These used to be one `null`: `focusedSession` returned null both while Room had not emitted yet and
 * when the id was not in the history at all. With a single null for two questions the UI picked the
 * worse meaning of the two — it rendered "Sin dirección" over a parking that has one, and fell back to
 * painting the ACTIVE parking's pin on the map. Two questions, two answers.
 */
sealed interface FocusedParking {

    /** The history has not arrived yet. Renders as a skeleton — never as a parking without data. */
    data object Unresolved : FocusedParking

    /** The history arrived and this id is not in it: retracted, deleted, or a dead deep link. */
    data object NotFound : FocusedParking

    data class Resolved(val session: UserParking) : FocusedParking
}

data class ParkingHistoryState(
    val userLocation: GpsPoint? = null,
    /**
     * Full parking history ordered most-recent → oldest, ALL vehicles interleaved. Raw source only —
     * the stepper never walks this list, it walks [orderedSessions]. [HISTORY-DETAIL-001]
     *
     * **`null` means Room has not emitted yet**, and it has NO default on purpose: an empty list is a
     * fact ("this user has no history"), while null is the absence of one.
     * [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
     */
    val allSessions: List<UserParking>?,
    // Registered vehicles — used to resolve the focused session's real body shape + paint colour for
    // the modal + map marker (UserParking has no colour of its own). [HISTORY-DETAIL-001]
    val vehicles: List<Vehicle> = emptyList(),
    // Which history entry is on screen. Kept as an id (not the object) so it survives list refreshes
    // and prev/next recomputes deterministically against [orderedSessions]. [HISTORY-DETAIL-001]
    val focusedSessionId: String? = null,
) {
    /**
     * The parking currently shown in the detail sheet + map, resolved against the FULL history so a
     * deep-link lands on its session whatever vehicle owns it — that session is then what scopes
     * [orderedSessions].
     */
    val focusedParking: FocusedParking
        get() {
            val sessions = allSessions ?: return FocusedParking.Unresolved
            return sessions.firstOrNull { it.id == focusedSessionId }
                ?.let(FocusedParking::Resolved)
                ?: FocusedParking.NotFound
        }

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
        get() {
            val focused = (focusedParking as? FocusedParking.Resolved)?.session ?: return emptyList()
            return allSessions.orEmpty().filter { it.vehicleId == focused.vehicleId }
        }

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
        get() = (focusedParking as? FocusedParking.Resolved)?.session
            ?.vehicleId
            ?.let { id -> vehicles.firstOrNull { it.id == id } }
}
