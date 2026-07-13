package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * One triggering geofence, resolved against the local active sessions by the platform caller.
 * The I/O (the repository read) is the platform's; the CLASSIFICATION of its result is this
 * use case's. [AUDIT-A9-KMP-001]
 */
sealed interface GeofenceExitLookup {
    val geofenceId: String

    /** The read succeeded and found an active parked session for this fence. */
    data class Found(override val geofenceId: String, val session: UserParking) : GeofenceExitLookup

    /** The read succeeded and found NO active session — the fence is an ORPHAN (a re-park left it
     *  registered NEVER_EXPIRE; it fires spurious exits with nothing to release). */
    data class NoSession(override val geofenceId: String) : GeofenceExitLookup

    /** The read FAILED (I/O error / cancellation). Indeterminate — must NOT be treated as an
     *  orphan: collapsing a failed read into "no session" removed a LIVE fence and discarded an
     *  on-time EXIT (field 2026-07-11 00:38, the lookup was cancelled mid-flight). */
    data class LookupFailed(override val geofenceId: String) : GeofenceExitLookup
}

/** A geofence whose active session provably belongs to a departing vehicle. */
data class DepartingExit(val geofenceId: String, val session: UserParking)

/**
 * The pure decision for a batch of triggering geofence exits: what to clean, what to dispatch,
 * and which session to arm the next-park detection with. The platform executes it. [AUDIT-A9-KMP-001]
 */
data class GeofenceExitDecision(
    /** Orphan fences to unregister + log — no active session backs them. */
    val orphanGeofenceIds: List<String>,
    /** Departures delivered AT the fence boundary: emit the in-process Exited event AND dispatch
     *  the speed-gated departure worker. */
    val boundaryDepartures: List<DepartingExit>,
    /** Departures delivered FAR from the fence (a real drive-away is far by construction: moving
     *  car + OEM delivery lag): dispatch the SAME worker + record the stale delivery for the
     *  reconcile conjunction, but withhold the Exited event so UI observers don't clear a session
     *  the live re-check may yet dismiss. [DET-RIDE-PROOF-001] */
    val staleDepartures: List<DepartingExit>,
) {
    val hasRealExit: Boolean get() = boundaryDepartures.isNotEmpty() || staleDepartures.isNotEmpty()

    /** The single session to arm the coordinator with — active-preferred, first of the real exits.
     *  Null when every triggering fence was an orphan or a failed lookup. */
    val armTarget: DepartingExit? get() = boundaryDepartures.firstOrNull() ?: staleDepartures.firstOrNull()
}

/**
 * Pure orchestration decision for a geofence-EXIT batch — extracted from the Android
 * `CoordinatorDetectionService` so the logic is unit-tested once and shared by an iOS geofence
 * handler. [AUDIT-A9-KMP-001]
 *
 * Three decisions, no side effects:
 *  1. **Orphan vs real vs skip** — a `NoSession` fence is an orphan to clean; a `LookupFailed` is
 *     skipped (never destructively cleaned); a `Found` is a real exit.
 *  2. **Vehicle attribution** — with overlapping fences the active vehicle's and an inactive
 *     still-parked car's radii can both fire; prefer the active vehicle when its fence is among
 *     those that fired, else fall back to whatever fired (the user left with an inactive car).
 *  3. **Delivery-distance split** — distance never decides WHETHER to look, only what a single
 *     delivery is worth: a boundary delivery also gets the in-process Exited event; a far one is
 *     only recorded for the reconcile. Both run the same speed-gated worker. [DET-EXIT-TRUST-001]
 */
class EvaluateGeofenceExitUseCase(private val config: ParkingDetectionConfig) {

    operator fun invoke(
        lookups: List<GeofenceExitLookup>,
        activeVehicleId: String?,
        triggerLatitude: Double?,
        triggerLongitude: Double?,
    ): GeofenceExitDecision {
        val orphans = lookups.filterIsInstance<GeofenceExitLookup.NoSession>().map { it.geofenceId }
        // LookupFailed is intentionally dropped here — neither cleaned nor dispatched.
        val realExits = lookups.filterIsInstance<GeofenceExitLookup.Found>()
        if (realExits.isEmpty()) {
            return GeofenceExitDecision(orphans, emptyList(), emptyList())
        }

        val departing = realExits
            .filter { it.session.vehicleId == activeVehicleId }
            .ifEmpty { realExits }

        val (boundary, stale) = departing.partition { exit ->
            val deliveredAtMeters = if (triggerLatitude != null && triggerLongitude != null) {
                haversineMeters(
                    triggerLatitude, triggerLongitude,
                    exit.session.location.latitude, exit.session.location.longitude,
                )
            } else {
                null
            }
            deliveredAtMeters == null || deliveredAtMeters <= config.watchdogFarThresholdMeters
        }

        return GeofenceExitDecision(
            orphanGeofenceIds = orphans,
            boundaryDepartures = boundary.map { DepartingExit(it.geofenceId, it.session) },
            staleDepartures = stale.map { DepartingExit(it.geofenceId, it.session) },
        )
    }
}
