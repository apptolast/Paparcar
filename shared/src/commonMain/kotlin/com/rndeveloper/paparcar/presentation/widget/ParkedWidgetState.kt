package com.rndeveloper.paparcar.presentation.widget

import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.displayName

/**
 * What the home-screen widget shows. Resolved by [resolveParkedWidgetState] — the widget layer
 * (Android Glance, in :app) only paints this; every decision about WHAT to show lives here, in
 * common code, where it is testable. Same split as [HistoryTimeline.resolve]. [WIDGET-PARKED-001]
 */
sealed class ParkedWidgetState {

    /** No active parking — the widget invites instead of blaming. */
    data object Empty : ParkedWidgetState()

    data class Parked(
        /** Id of the winning session — what the "I'm leaving" action asks Home to release.
         *  [WIDGET-ACTIONS-002] */
        val sessionId: String,
        val vehicleName: String,
        /** Street of the parked session, or null while background enrichment hasn't landed —
         *  the widget then shows a generic "saved location" line, never a blank. */
        val street: String?,
        val parkedAtMs: Long,
        val latitude: Double,
        val longitude: Double,
        /** Other vehicles ALSO parked right now (multi-parking) — rendered as a "+N" hint. */
        val othersCount: Int,
    ) : ParkedWidgetState()
}

/**
 * Projects the active sessions + vehicles into the widget's state.
 *
 * - Several cars parked → the MOST RECENT session wins (it is the one the user most likely wants
 *   to find), the rest become [ParkedWidgetState.Parked.othersCount].
 * - A session whose vehicle no longer resolves is skipped — same rule as
 *   [com.rndeveloper.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase] (the user
 *   deleted the vehicle while it was parked).
 * - The vehicle name comes from [Vehicle.displayName] — not re-derived here.
 */
fun resolveParkedWidgetState(
    sessions: List<UserParking>,
    vehicles: List<Vehicle>,
): ParkedWidgetState {
    val resolvable = sessions
        .filter { it.isActive }
        .mapNotNull { session ->
            val vehicle = vehicles.find { it.id == session.vehicleId } ?: return@mapNotNull null
            session to vehicle
        }
    val (session, vehicle) = resolvable.maxByOrNull { (s, _) -> s.location.timestamp }
        ?: return ParkedWidgetState.Empty
    return ParkedWidgetState.Parked(
        sessionId = session.id,
        vehicleName = vehicle.displayName(),
        street = session.address?.street?.takeIf { it.isNotBlank() },
        parkedAtMs = session.location.timestamp,
        latitude = session.location.latitude,
        longitude = session.location.longitude,
        othersCount = resolvable.size - 1,
    )
}
