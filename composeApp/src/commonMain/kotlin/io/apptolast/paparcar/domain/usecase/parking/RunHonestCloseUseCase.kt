package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository

/**
 * What the honest close actually DID, plus the evaluator's full [verdict] so the caller can stamp
 * the reasoning into the diagnostics trace ("no mute zones" — field 2026-07-25: an approximate pin
 * appeared with zero remote evidence of why). [DET-FROZEN-COUNTER-001]
 */
data class HonestCloseResult(
    /** [RunHonestCloseUseCase.OUTCOME_APPROXIMATE_PIN] / [RunHonestCloseUseCase.OUTCOME_APPROXIMATE_ZONE]
     *  when an artifact was saved; null when the ladder stayed silent OR the save failed. */
    val outcomeLabel: String?,
    val verdict: HonestCloseVerdict,
    /** Radius of the saved approximate zone, null for a pin / silence. */
    val zoneRadiusMeters: Float? = null,
)

/**
 * Orchestrates the honest close of an aborting detection session. [DET-HONEST-CLOSE-001]
 *
 * Runs the pure ladder ([EvaluateHonestCloseUseCase]) at the abort moment and executes its
 * side effects — the I/O half the pure evaluator deliberately does not own. Lives ABOVE the
 * coordinator (which lacks the repository, the hardware step counter, and the fence machinery):
 * the coordinator emits the silent abort, the caller (the Android detection service, reusing the
 * safety net's step-baseline mechanism to compute [stepsSinceStalePin]) runs this immediately —
 * NOT deferred to the 15-min worker Doze holds for hours. Coordinator-level behaviour is
 * unchanged, so the coordinator's own replay characterization still asserts silence; the honest
 * close is covered here (RunHonestCloseUseCaseTest).
 *
 * The save goes through [ConfirmParkingUseCase], which:
 *  - deactivates the vehicle's previous active session → the stale pin the car drove away from is
 *    RELEASED (and its orphan geofence dropped) as a side effect of saving the replacement;
 *  - registers a fresh geofence at the new spot → the next departure always has a nominator, so
 *    the chain never breaks (the whole point of leaving a zone instead of nothing).
 *
 * Reliability is [ParkingDetectionConfig.reliabilityUnattendedSave] (0.5): low enough that nothing
 * community-facing publishes it, the same floor the unattended-timeout save already uses.
 */
class RunHonestCloseUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val confirmParking: ConfirmParkingUseCase,
    private val notificationPort: AppNotificationManager,
    private val evaluateHonestClose: EvaluateHonestCloseUseCase,
    private val config: ParkingDetectionConfig,
) {
    /**
     * @param vehicleId          The vehicle whose session just aborted (owns the stale pin).
     * @param abortFix           Position at the abort moment (the candidate new spot).
     * @param stepsSinceStalePin Hardware cumulative-counter delta since the stale pin was sealed,
     *                           or null when the counter is mute. Computed by the caller.
     * @param stepSealPoint      WHERE the body was when the step baseline was sealed, or null when
     *                           the seal has no position — the budget's origin, without which the
     *                           ladder refuses the walked-vs-rode verdict. [DET-STEP-BUDGET-ORIGIN-001]
     * @param sessionStepEvents  Steps the aborting session's own step DETECTOR counted — the
     *                           cumulative counter's liveness witness. [DET-FROZEN-COUNTER-001]
     * @param sessionMaxSpeedMps Max GPS speed (m/s) the aborting session measured — measured
     *                           driving outranks the step inference. [DET-FROZEN-COUNTER-001]
     */
    suspend operator fun invoke(
        vehicleId: String,
        abortFix: GpsPoint,
        stepsSinceStalePin: Long?,
        stepSealPoint: GpsPoint?,
        sessionStepEvents: Int = 0,
        sessionMaxSpeedMps: Float = 0f,
    ): HonestCloseResult {
        val stalePin = userParkingRepository.getActiveSessionByVehicle(vehicleId)

        val verdict = evaluateHonestClose(
            stalePin, abortFix, stepsSinceStalePin, stepSealPoint,
            sessionStepEvents = sessionStepEvents,
            sessionMaxSpeedMps = sessionMaxSpeedMps,
        )
        val location: GpsPoint
        val radiusMeters: Float?
        val outcome: String
        when (val decision = verdict.decision) {
            is HonestCloseDecision.ApproximatePin -> {
                location = decision.location
                radiusMeters = null
                outcome = OUTCOME_APPROXIMATE_PIN
            }
            is HonestCloseDecision.ApproximateZone -> {
                location = decision.center
                radiusMeters = decision.radiusMeters
                outcome = OUTCOME_APPROXIMATE_ZONE
            }
            HonestCloseDecision.KeepSilent -> return HonestCloseResult(outcomeLabel = null, verdict = verdict)
        }

        val saved = confirmParking(
            location = location,
            detectionReliability = config.reliabilityUnattendedSave,
            vehicleId = vehicleId,
            zoneRadiusMeters = radiusMeters,
            // [DET-PIN-PROVENANCE-001] the honest-close outcome IS this pin's provenance path
            // ("closed_approximate_pin" / "closed_approximate_zone").
            detectionPath = outcome,
            // The abort fix IS where the body is right now — the honest origin for the new
            // pin's step baseline. [DET-STEP-BUDGET-ORIGIN-001]
            sealPoint = abortFix,
        )
        // Save failed → nothing was released or registered; keep the stale pin and stay silent
        // rather than nudge about a mark that does not exist.
        if (saved.isFailure) return HonestCloseResult(outcomeLabel = null, verdict = verdict)

        // Never silent: ask the user to confirm or refine the approximate mark, from the live
        // service — the honest half of the contract. No pending record: the approximate pin/zone
        // saved above IS the durable trace of this ask; a pending record would resurface as a
        // ghost "car lost" banner after that session's normal release. [DET-NUDGE-PERSIST-001]
        notificationPort.showMarkParkingNudge(source = outcome, vehicleId = vehicleId, persistPending = false)
        return HonestCloseResult(outcomeLabel = outcome, verdict = verdict, zoneRadiusMeters = radiusMeters)
    }

    companion object {
        const val OUTCOME_APPROXIMATE_PIN = "closed_approximate_pin"
        const val OUTCOME_APPROXIMATE_ZONE = "closed_approximate_zone"
    }
}
