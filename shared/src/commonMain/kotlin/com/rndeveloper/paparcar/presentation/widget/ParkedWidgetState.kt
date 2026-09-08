package com.rndeveloper.paparcar.presentation.widget

import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.model.parkedSessionPreference

/**
 * What the home-screen widget shows. Resolved by [resolveParkedWidgetState] — the widget layer
 * (Android Glance, in :app) only paints this; every decision about WHAT to show lives here, in
 * common code, where it is testable. Same split as [HistoryTimeline.resolve]. [WIDGET-PARKED-001]
 */
sealed class ParkedWidgetState {

    /** No active parking — the widget invites instead of blaming. */
    data object Empty : ParkedWidgetState()

    /**
     * A trip is being followed RIGHT NOW. [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001]
     *
     * Without this state a trip read as [Empty] — because a trip begins by RELEASING the previous
     * session — so the widget spent the whole drive inviting the user to mark a parking, which is
     * the one thing they cannot do and do not need while at the wheel.
     *
     * Carries no address and no actions on purpose:
     * - no address, because with the app closed (the widget's whole point) nothing is geocoding the
     *   phone's position, and opening a background geocode for the length of a drive would spend
     *   network and battery naming a street to someone sitting inside the car;
     * - no actions, because "Directions" needs coordinates and "More" needs a sessionId, and a live
     *   trip has neither — its session was released when it started.
     */
    data class Driving(
        /** The departing vehicle's name, or null when the trip was armed without a known origin. */
        val vehicleName: String?,
        /** The trip stopped and a parking is being confirmed — the widget says so instead of
         *  claiming the car is still moving. */
        val isCandidate: Boolean,
        /**
         * Cars that stay parked while this one drives — only possible with more than one vehicle,
         * which is a Plus situation [FREE-LIMITS-001]. A tall widget lists them under the trip; a
         * short one has room for the trip only. [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001 D5]
         */
        val alsoParked: List<ParkedCar> = emptyList(),
    ) : ParkedWidgetState()

    /**
     * One parked car, as the widget draws it. [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001 D5]
     *
     * The widget declares itself resizable in both axes, so a tall one lists a row per car instead
     * of collapsing them into "+N". Every field a row needs — and its own actions — lives here, so
     * the render site never has to go looking for the second car's data.
     */
    data class ParkedCar(
        /** Id of this session — what the "I'm leaving" action asks Home to release.
         *  [WIDGET-ACTIONS-002] */
        val sessionId: String,
        val vehicleName: String,
        /** Street of the parked session, or null while background enrichment hasn't landed —
         *  the widget then shows a generic "saved location" line, never a blank. */
        val street: String?,
        val parkedAtMs: Long,
        val latitude: Double,
        val longitude: Double,
    )

    /**
     * At least one car is parked. [cars] is ordered by the app's own preference — the subject
     * first — so a short widget shows `cars.first()` and a tall one lists them all in the same
     * order Home's vehicle strip uses.
     */
    data class Parked(val cars: List<ParkedCar>) : ParkedWidgetState() {
        /** The car a one-line widget is about. */
        val subject: ParkedCar get() = cars.first()

        /** What the short layout collapses into "+N more". */
        val othersCount: Int get() = cars.size - 1
    }
}

/**
 * Projects the active sessions + vehicles (+ the live trip, if any) into the widget's state.
 *
 * - **A live trip outranks a parked car** — it is what is happening right now. Same hierarchy the
 *   Browse peek and the vehicles strip already follow. [UI-BROWSE-DRIVING-OVER-PARKED-001]
 * - Several cars parked → they come ORDERED by [parkedSessionPreference], the very comparator
 *   behind Home's preferred session and its vehicle strip, so the widget's subject (and the order
 *   of a tall widget's rows) can never disagree with the app's. It used to be a local `maxByOrNull { timestamp }` — the same answer in
 *   the common case, without the watch tie-break and blind to every later decision taken on the
 *   shared rule. [UI-PREFERRED-SESSION-RECENCY-001] [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001]
 * - A session whose vehicle no longer resolves is skipped — same rule as
 *   [com.rndeveloper.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase] (the user
 *   deleted the vehicle while it was parked).
 * - The vehicle name comes from [Vehicle.displayName] — not re-derived here.
 *
 * @param drivingVehicleId the vehicle of the trip being followed, or null when none is. A trip with
 *   an unknown origin (manual arm) still passes a non-null [isDriving]; the name then reads null.
 */
fun resolveParkedWidgetState(
    sessions: List<UserParking>,
    vehicles: List<Vehicle>,
    isDriving: Boolean = false,
    drivingVehicleId: String? = null,
    isCandidate: Boolean = false,
): ParkedWidgetState {
    // Ordered ONCE, by the app's own preference, and reused by both branches: the parked list a
    // tall widget shows is the same list, in the same order, whether or not a trip is running.
    // [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001 D5]
    val parked = sessions
        .filter { it.isActive }
        .mapNotNull { session ->
            val vehicle = vehicles.find { it.id == session.vehicleId } ?: return@mapNotNull null
            session to vehicle
        }
        .sortedWith(compareBy(parkedSessionPreference(vehicles)) { (s, _) -> s })
        .reversed()
        .map { (session, vehicle) ->
            ParkedWidgetState.ParkedCar(
                sessionId = session.id,
                vehicleName = vehicle.displayName(),
                street = session.address?.street?.takeIf { it.isNotBlank() },
                parkedAtMs = session.location.timestamp,
                latitude = session.location.latitude,
                longitude = session.location.longitude,
            )
        }

    if (isDriving) {
        return ParkedWidgetState.Driving(
            vehicleName = drivingVehicleId
                ?.let { id -> vehicles.find { it.id == id } }
                ?.displayName()
                ?.takeIf { it.isNotBlank() },
            isCandidate = isCandidate,
            alsoParked = parked,
        )
    }
    if (parked.isEmpty()) return ParkedWidgetState.Empty
    return ParkedWidgetState.Parked(parked)
}
