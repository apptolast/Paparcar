@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/** Narrow port so the coordinator can ask for this without knowing the repositories behind it —
 *  the same shape as [RetractDeducedDeparture]. */
fun interface RestoreRefutedDeparture {
    /** See [RestoreRefutedDepartureUseCase.invoke]. */
    suspend operator fun invoke(geofenceId: String, vehicleId: String?): Boolean
}

/**
 * [DET-A-TRAIN-IS-NOT-A-CAR-001] Gives the car back after a departure the trip itself refuted.
 *
 * ## Why this exists, when [RetractDeducedDepartureUseCase] already undoes a bicycle
 *
 * Because the two departures take different things, and only one of them takes the car.
 *
 * A DEDUCED departure publishes provisionally and *gives up nothing*: the session and its geofence
 * stay exactly where they were, so when the bicycle ride of 2026-08-19 refuted it there was a spot
 * to withdraw and nothing to restore — which is what that use case's own KDoc says out loud.
 *
 * A WITNESSED departure is the other half of that pair, and it is what the 2026-09-15 train
 * produced: the first credible driving fix arrived 45 seconds after the arm — a train pulling out of
 * a platform accelerates like nothing else the phone can see — so `ProcessConfirmedDepartureUseCase`
 * took the witnessed branch, published the freed plaza and CLOSED the session. By the time the
 * road-plausibility ceiling could possibly know it was a train (the impossible band needs five
 * minutes of it, the journey corroborator an hour), the car had already been given away.
 *
 * So the ceiling alone leaves the state merely less wrong: no phantom pin at the terminus, but a car
 * that the app has forgotten and a plaza a stranger may still be driving to. Refusing to repeat a
 * mistake is not the same as undoing it.
 *
 * ## What is undone, and what is deliberately not
 *
 * Three things, in the order that matters if the process dies half-way:
 *
 *  1. **The community's plaza first.** It is the only part of this state that other people act on,
 *     and the one with a deadline — the same `retractSpot` call the bicycle path makes, on a spot
 *     that carries the session's own id.
 *  2. **The session re-opens**, through [UserParkingRepository.restoreParkingSession]: active again,
 *     `endedAtMs` cleared so the real departure can date itself when it comes, `publishedSpot` back
 *     to false because the plaza above has just been taken back.
 *  3. **The geofence is re-registered**, because a restored pin with no fence is a car that cannot
 *     announce its next departure. Field 2026-08-19 priced exactly this omission on the deduced
 *     path: *"the geofence removal took their next EXIT trigger with them"*.
 *
 * ⛔ **It refuses when the vehicle already has an active session.** Whatever put that session there
 * knows more than this one does: the user marking their parking by hand outranks every inference
 * ([DET-ASSERTION-OUTRANKS-INFERENCE-001]), and a genuine later park must never be shadowed by a
 * second active session for the same car. The refusal is silent and returns false — the ceiling's
 * own close still happened, and this was only ever the repair on top of it.
 *
 * ⛔ **It refuses to resurrect a WITHDRAWN pin** (the `retractedAtMs IS NULL` clause in the DAO
 * query). A withdrawal is the app — or the user — concluding that pin was a fiction; a refuted
 * departure is no argument against that verdict, and the two undos must not fight.
 */
class RestoreRefutedDepartureUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val spotRepository: SpotRepository,
    private val geofenceManager: GeofenceManager,
    private val config: ParkingDetectionConfig,
    private val detectionEventLogger: DetectionEventLogger? = null,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : RestoreRefutedDeparture {

    /**
     * @param geofenceId The fence whose EXIT armed the refuted session — the address of the parking
     *   that was given away. A session's `geofenceId` is its own id, so this identifies the row.
     * @param vehicleId The car the session was attributed to, or null when it never locked one.
     * @return whether the parking was actually restored.
     */
    override suspend fun invoke(geofenceId: String, vehicleId: String?): Boolean {
        val session = runCatching { userParkingRepository.getSessionById(geofenceId) }
            .onFailure { PaparcarLogger.w(TAG, "could not read the departed session — nothing restored", it) }
            .getOrNull() ?: run {
            PaparcarLogger.d(TAG, "no session behind geof=${geofenceId.take(8)} — nothing to give back")
            return false
        }
        if (session.isActive) {
            PaparcarLogger.d(TAG, "session ${geofenceId.take(8)} never closed — nothing to give back")
            return false
        }

        // [DET-ASSERTION-OUTRANKS-INFERENCE-001] Something newer already answers "where is my car".
        // Read through the vehicle rather than the session so a park saved under a NEW id counts.
        val current = vehicleId?.let {
            runCatching { userParkingRepository.getActiveSessionByVehicle(it) }.getOrNull()
        }
        if (current != null) {
            PaparcarLogger.d(
                TAG,
                "⊘ not restoring ${geofenceId.take(8)} — the car already has an active park " +
                    "(${current.id.take(8)}), and whatever placed it knows more than a refuted trip does",
            )
            return false
        }

        // 1 ── The plaza first: it is the part strangers are acting on. Idempotent, and a failure
        // here must not stop the restore — the spot's TTL bounds that damage, nothing bounds a car
        // the app has forgotten.
        if (session.publishedSpot) {
            spotRepository.retractSpot(session.id)
                .onFailure { PaparcarLogger.w(TAG, "could not withdraw the plaza — its TTL still bounds it", it) }
                .onSuccess {
                    PaparcarLogger.d(TAG, "withdrew the plaza ${session.id.take(8)} — it was never freed")
                    detectionEventLogger?.log(
                        DetectionEvent.SpotRetracted(
                            sessionId = session.geofenceId ?: session.id,
                            timestampMs = nowMs(),
                            sessionAgeMs = session.endedAtMs?.let { nowMs() - it },
                            location = session.location,
                        )
                    )
                }
        }

        // 2 ── The car comes back.
        userParkingRepository.restoreParkingSession(session.id)
            .onFailure {
                PaparcarLogger.e(TAG, "✗ could not re-open ${session.id.take(8)} — the car stays lost", it)
                return false
            }

        // 3 ── …and with the fence that lets it announce its NEXT departure. The radius is the
        // session's own — its zone radius when it had one, otherwise the same size-and-accuracy
        // policy `ConfirmParkingUseCase` used to register it in the first place. Re-deriving it any
        // other way would give the restored car a different fence from the one it had.
        val radiusMeters = session.zoneRadiusMeters
            ?: config.geofenceRadiusFor(session.sizeCategory, session.location.accuracy)
        geofenceManager.createGeofence(
            geofenceId = session.id,
            latitude = session.location.latitude,
            longitude = session.location.longitude,
            radiusMeters = radiusMeters,
        ).onFailure { PaparcarLogger.w(TAG, "⚠ re-registering the geofence failed (park restored anyway)", it) }

        PaparcarLogger.d(
            TAG,
            "✓ gave the car back: session ${session.id.take(8)} is active again — the trip that " +
                "took it was measured to be no road vehicle [DET-A-TRAIN-IS-NOT-A-CAR-001]",
        )
        return true
    }

    private companion object {
        const val TAG = "PARKDIAG/RestoreRefutedDeparture"
    }
}
