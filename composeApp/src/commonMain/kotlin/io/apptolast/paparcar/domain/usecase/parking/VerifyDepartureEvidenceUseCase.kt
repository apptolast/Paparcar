package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.math.abs

/**
 * [DET-G-05] Pre-arm verifier for a GEOFENCE_EXIT: is there any *vehicle* evidence behind
 * this exit, or did the phone simply leave the radius on foot?
 *
 * A geofence exit fires whenever the PHONE crosses the parked-car radius — and walking away
 * after a real park is what every user does, every time. Arming the coordinator as a
 * "confirmed departure" on that signal alone seeds `hasEverReachedDrivingSpeed` and disarms
 * the anti-walking guards, letting the steps+egress path re-confirm a bogus park at the
 * pedestrian's position (BUG-REPARK-WALK-001, regression introduced by DET-G-04).
 *
 * Evidence — either signal suffices:
 * 1. **Recent AR IN_VEHICLE_ENTER** within [ParkingDetectionConfig.vehicleEnterWindowMs] of
 *    the exit. Covers the short-hop repark DET-G-04 was written for: the user boarded the
 *    car, drove a hop too short for the GPS stream to catch, and parked again.
 * 2. **A fix at driving speed** (≥ [ParkingDetectionConfig.minimumDepartureSpeedKmh]) sampled
 *    when the exit is handled. Covers the common drive-away, where the exit fires mid-drive.
 *
 * Walking produces neither: pedestrian speed stays far below the departure threshold and no
 * ENTER is recorded. An unverified exit must still arm the coordinator — evidence can arrive
 * late (AR delivery takes up to ~2 min) — but WITHOUT the seed, keeping the legacy
 * `falseEnterAbortSteps` guard active; the departure worker upgrades the live session via
 * `CoordinatorParkingDetector.notifyDepartureConfirmed()` once its own verdict lands.
 *
 * Pure synchronous evaluator — the caller supplies the sampled speed.
 */
class VerifyDepartureEvidenceUseCase(
    private val departureEventBus: DepartureEventBus,
    private val config: ParkingDetectionConfig,
) {
    private companion object {
        const val TAG = "VerifyDepartureEvidenceUseCase"
    }

    /**
     * @param exitTimestampMs Epoch-ms of the geofence exit event.
     * @param currentSpeedKmh Speed (km/h) sampled when handling the exit, or null if unavailable.
     * @return `true` when the exit is backed by vehicle evidence and may arm the coordinator
     *         as a confirmed departure.
     */
    operator fun invoke(exitTimestampMs: Long, currentSpeedKmh: Float?): Boolean {
        val enteredAt = departureEventBus.lastVehicleEnteredAt
        val enteredRecently = enteredAt != null &&
            abs(exitTimestampMs - enteredAt) <= config.vehicleEnterWindowMs
        val speedConfirms = currentSpeedKmh != null &&
            currentSpeedKmh >= config.minimumDepartureSpeedKmh
        val verified = enteredRecently || speedConfirms
        PaparcarLogger.d(
            TAG,
            "departure evidence: enteredRecently=$enteredRecently (enteredAt=$enteredAt) " +
                "speedConfirms=$speedConfirms (speedKmh=$currentSpeedKmh) → verified=$verified",
        )
        return verified
    }
}
