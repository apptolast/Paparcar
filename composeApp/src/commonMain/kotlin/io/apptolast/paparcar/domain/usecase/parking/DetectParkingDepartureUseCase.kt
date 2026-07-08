package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.util.PaparcarLogger

/**
 * Outcome of [DetectParkingDepartureUseCase].
 *
 * - [Confirmed]   — all signals agree: the user drove away in their own car.
 * - [Rejected]    — a definitive "not a departure" (no session, wrong geofence ID,
 *                   or IN_VEHICLE_ENTER signal too old / from a previous trip).
 * - [Inconclusive]— session and geofence match, but either the IN_VEHICLE_ENTER
 *                   signal has not arrived yet, or it is present but GPS speed is
 *                   still below the departure threshold (user leaving a garage slowly).
 *                   `DepartureDetectionWorker` should retry after a short delay.
 */
sealed class DepartureDecision {
    data object Confirmed : DepartureDecision()
    data object Rejected : DepartureDecision()
    data object Inconclusive : DepartureDecision()
}

/**
 * Decides whether a geofence-exit event should trigger the publication of
 * the parked spot as free.
 *
 * Combines three independent signals to minimise false positives:
 *
 * 1. **Saved parking session** — an active [UserParking] must exist.
 * 2. **Geofence ID match** — the exited geofence must match the active session,
 *    preventing stale or mismatched events from triggering a report.
 * 3. **IN_VEHICLE_ENTER time window** — the user must have entered a vehicle
 *    within [ParkingDetectionConfig.vehicleEnterWindowMs] of the geofence exit.
 *    This is the key discriminator between "drove away" and "went for a walk".
 * 4. **Speed** (optional) — when a GPS reading is available, speed must exceed
 *    [ParkingDetectionConfig.minimumDepartureSpeedKmh]. If speed is unavailable,
 *    this check is skipped when used as the primary signal.
 *
 * When signal 3 has not yet arrived and speed is insufficient, returns
 * [DepartureDecision.Inconclusive] instead of [DepartureDecision.Rejected] so
 * that the caller can retry — the Transitions API can take up to
 * ~2 minutes to deliver IN_VEHICLE_ENTER after the geofence fires.
 *
 * **Known limitation:** a user who boards a taxi immediately adjacent to their
 * parked car may still produce a false positive — Android's Activity Recognition
 * cannot distinguish the user's own vehicle from any other. This edge case is
 * accepted: the consequence (spot briefly published as free) is minor compared to
 * the benefit of reliable departure detection for the common case.
 */
class DetectParkingDepartureUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val departureEventBus: DepartureEventBus,
    private val config: ParkingDetectionConfig,
) {
    private companion object {
        const val TAG = "DetectParkingDepartureUseCase"
    }

    /**
     * @param geofenceId        ID of the geofence that fired the exit transition.
     * @param exitTimestampMs   Epoch-ms of the geofence exit event.
     * @param currentSpeedKmh   Speed (km/h) at time of exit, or null if unavailable.
     * @param currentAccuracyM  Horizontal accuracy (m) of that same fix, or null if unavailable.
     *        Speed without credible accuracy is NOT movement evidence — a single acc=100 m cache
     *        jump faked 21.6 km/h on a motionless phone and confirmed a departure (field
     *        2026-07-08 04:18, Oppo). Same canonical rule as the pre-arm verifier and the
     *        safety-net evaluator ([ParkingDetectionConfig.isCredibleDrivingSpeed]).
     *        [DET-EXIT-TRUST-001]
     * @return [DepartureDecision] indicating whether to publish, skip, or retry.
     */
    suspend operator fun invoke(
        geofenceId: String,
        exitTimestampMs: Long,
        currentSpeedKmh: Float?,
        currentAccuracyM: Float? = null,
    ): DepartureDecision {
        // Signals 1+2: an active parking session must exist *for this exact geofence*.
        // Looking up by geofenceId (instead of fetching the single active session and
        // comparing) is correct under multi-parking — different vehicles can each have
        // their own active session, each tied to its own geofence. [MULTI-PARKING-001]
        userParkingRepository.getActiveSessionByGeofence(geofenceId)
            ?: run {
                PaparcarLogger.w(TAG, "departure rejected: no active session for geofenceId=$geofenceId")
                return DepartureDecision.Rejected
            }

        val vehicleEnteredAt = departureEventBus.lastVehicleEnteredAt
        val speedConfirmsMovement = config.isCredibleDrivingSpeed(currentSpeedKmh, currentAccuracyM)

        return if (vehicleEnteredAt != null) {
            // Signal 3: IN_VEHICLE_ENTER is present — validate the time window. STRICT ordering:
            // the boarding must PRECEDE the exit. The bus carries TRUE transition times (the
            // receiver converts elapsedRealTimeNanos → epoch), so an ENTER stamped after the exit
            // is a vehicle boarded OUTSIDE the radius (bus/taxi after walking out) — not evidence
            // that the user drove their car away. [DET-SOLID-001]
            val enterToExitMs = exitTimestampMs - vehicleEnteredAt
            if (enterToExitMs !in 0..config.vehicleEnterWindowMs) {
                // From a previous trip (too old) or from AFTER the exit — ignore it.
                PaparcarLogger.w(TAG, "departure rejected: IN_VEHICLE_ENTER outside window — enterToExit=${enterToExitMs}ms window=${config.vehicleEnterWindowMs}ms geofenceId=$geofenceId")
                DepartureDecision.Rejected
            } else if (currentSpeedKmh != null && !speedConfirmsMovement) {
                // IN_VEHICLE_ENTER is within the window (strong signal), but speed is low.
                // This is common when leaving a tight parking space or a garage ramp.
                // Returning Inconclusive (not Rejected) lets DepartureDetectionWorker retry
                // once the vehicle has accelerated past the departure threshold.
                DepartureDecision.Inconclusive
            } else {
                DepartureDecision.Confirmed
            }
        } else {
            // Signal 3 not yet available. Fall back to speed as sole discriminator.
            // If speed is also inconclusive, ask the caller to retry — IN_VEHICLE_ENTER
            // can arrive up to ~2 minutes after the geofence exit on some devices.
            if (speedConfirmsMovement) {
                DepartureDecision.Confirmed
            } else {
                DepartureDecision.Inconclusive
            }
        }
    }
}
