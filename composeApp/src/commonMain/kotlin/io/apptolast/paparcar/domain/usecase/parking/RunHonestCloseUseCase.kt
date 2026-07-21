package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository

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
     * @return the diagnostics outcome label ([OUTCOME_APPROXIMATE_PIN] / [OUTCOME_APPROXIMATE_ZONE])
     *         when the ladder acted, or null when it stayed silent (the coordinator's own abort
     *         outcome then stands).
     */
    suspend operator fun invoke(
        vehicleId: String,
        abortFix: GpsPoint,
        stepsSinceStalePin: Long?,
    ): String? {
        val stalePin = userParkingRepository.getActiveSessionByVehicle(vehicleId)

        val location: GpsPoint
        val radiusMeters: Float?
        val outcome: String
        when (val decision = evaluateHonestClose(stalePin, abortFix, stepsSinceStalePin)) {
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
            HonestCloseDecision.KeepSilent -> return null
        }

        val saved = confirmParking(
            location = location,
            detectionReliability = config.reliabilityUnattendedSave,
            vehicleId = vehicleId,
            zoneRadiusMeters = radiusMeters,
            // [DET-PIN-PROVENANCE-001] the honest-close outcome IS this pin's provenance path
            // ("closed_approximate_pin" / "closed_approximate_zone").
            detectionPath = outcome,
        )
        // Save failed → nothing was released or registered; keep the stale pin and stay silent
        // rather than nudge about a mark that does not exist.
        if (saved.isFailure) return null

        // Never silent: ask the user to confirm or refine the approximate mark, from the live
        // service — the honest half of the contract.
        notificationPort.showMarkParkingNudge()
        return outcome
    }

    companion object {
        const val OUTCOME_APPROXIMATE_PIN = "closed_approximate_pin"
        const val OUTCOME_APPROXIMATE_ZONE = "closed_approximate_zone"
    }
}
