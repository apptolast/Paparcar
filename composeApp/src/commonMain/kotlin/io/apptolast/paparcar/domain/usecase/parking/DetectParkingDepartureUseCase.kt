package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.service.DepartureEventBus
import kotlin.math.abs

/**
 * Decides whether a geofence-exit event should trigger the publication of
 * the parked spot as free.
 *
 * Combines three independent signals to minimise false positives:
 *
 * 1. **Saved parking session** — an active [ParkingSession] must exist.
 * 2. **Geofence ID match** — the exited geofence must match the active session,
 *    preventing stale or mismatched events from triggering a report.
 * 3. **IN_VEHICLE_ENTER time window** — the user must have entered a vehicle
 *    within [ParkingDetectionConfig.vehicleEnterWindowMs] of the geofence exit.
 *    This is the key discriminator between "drove away" and "went for a walk".
 * 4. **Speed** (optional) — when a GPS reading is available, speed must exceed
 *    [ParkingDetectionConfig.minimumDepartureSpeedKmh]. If speed is unavailable,
 *    this check is skipped; signals 1–3 are sufficient.
 *
 * **Known limitation:** a user who boards a taxi immediately adjacent to their
 * parked car may still produce a false positive — Android's Activity Recognition
 * cannot distinguish the user's own vehicle from any other. This edge case is
 * accepted: the consequence (spot briefly published as free) is minor compared to
 * the benefit of reliable departure detection for the common case.
 */
class DetectParkingDepartureUseCase(
    private val getUserParking: GetUserParkingUseCase,
    private val departureEventBus: DepartureEventBus,
    private val config: ParkingDetectionConfig,
) {

    /**
     * @param geofenceId        ID of the geofence that fired the exit transition.
     * @param exitTimestampMs   Epoch-ms of the geofence exit event.
     * @param currentSpeedKmh   Speed (km/h) at time of exit, or null if unavailable.
     * @return true if the parking spot should be published as free.
     */
    suspend operator fun invoke(
        geofenceId: String,
        exitTimestampMs: Long,
        currentSpeedKmh: Float?,
    ): Boolean {
        // Signal 1: must have an active parking session to report
        val session = getUserParking() ?: return false

        // Signal 2: the exit must belong to the current session's geofence
        if (session.geofenceId != null && session.geofenceId != geofenceId) return false

        // Signals 3+4 combined: need either IN_VEHICLE_ENTER within the time window OR
        // GPS speed confirming movement. The Transitions API can take up to 5 min to
        // deliver IN_VEHICLE_ENTER; a geofence of 80 m is exited in seconds, so
        // CheckDepartureWorker often runs before the signal arrives. Speed is the
        // primary fallback when the transition is missing.
        val vehicleEnteredAt = departureEventBus.lastVehicleEnteredAt
        val speedConfirmsMovement = currentSpeedKmh != null &&
                currentSpeedKmh >= config.minimumDepartureSpeedKmh

        if (vehicleEnteredAt != null) {
            // Signal 3: time window check
            val timeDiffMs = abs(exitTimestampMs - vehicleEnteredAt)
            if (timeDiffMs > config.vehicleEnterWindowMs) return false
            // Signal 4 (belt-and-suspenders): if speed is available it must also confirm movement
            if (currentSpeedKmh != null && !speedConfirmsMovement) return false
        } else {
            // No IN_VEHICLE_ENTER yet — GPS speed is the sole discriminator
            if (!speedConfirmsMovement) return false
        }

        return true
    }
}
