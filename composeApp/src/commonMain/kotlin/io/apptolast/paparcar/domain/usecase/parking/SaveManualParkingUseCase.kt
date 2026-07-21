@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.detection.ManualParkingDetection
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import kotlin.time.Clock

/**
 * Persists a USER-CONFIRMED parking pin — the one business flow behind Home's
 * "Aparcar aquí" (create), "Mover ubicación" (edit) and the detection prompt's
 * "Sí, he aparcado" (detected). Extracted from HomeViewModel so the branching
 * and side-effects are testable without a VM harness. [HOME-ATOMIZE-001 F4]
 *
 * Side-effects on a successful CREATE (both entry points):
 *  - the "parking saved" notification ([CONFIRM-NO-NOTIF-CLEANUP] — notification
 *    responsibility lives with the caller-side use case, not ConfirmParking);
 *  - detection teardown: the user resolved the park by hand → the trip is over,
 *    so any in-progress coordinator session is cancelled before it can overwrite
 *    this pin. [DET-MANUAL-CANCEL-001]
 * A MOVE (editingParkingId != null) has neither: the session already exists —
 * [UpdateParkingLocationUseCase] re-registers its geofence in place.
 */
class SaveManualParkingUseCase(
    private val confirmParking: ConfirmParkingUseCase,
    private val updateParkingLocation: UpdateParkingLocationUseCase,
    private val notificationPort: AppNotificationManager,
    private val manualParkingDetection: ManualParkingDetection,
) {
    /**
     * Pin-mode confirm: builds the [GpsPoint] from the settled pin centre.
     *
     * @param editingParkingId non-null when MOVING an existing session; [targetVehicleId]
     *   is ignored in that case. [MULTI-PARKING-001]
     * @param targetVehicleId the vehicle a NEW session is created for; null → default vehicle.
     */
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        accuracy: Float,
        editingParkingId: String? = null,
        targetVehicleId: String? = null,
    ): Result<Unit> = save(
        gps = GpsPoint(
            latitude = lat,
            longitude = lon,
            accuracy = accuracy,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            speed = 0f,
        ),
        spotType = SpotType.MANUAL_REPORT,
        editingParkingId = editingParkingId,
        targetVehicleId = targetVehicleId,
    )

    /** Detection-prompt confirm: the detected fix IS the pin (always a create). */
    suspend fun confirmDetected(gps: GpsPoint): Result<Unit> =
        save(gps, SpotType.AUTO_DETECTED, editingParkingId = null, targetVehicleId = null)

    private suspend fun save(
        gps: GpsPoint,
        spotType: SpotType,
        editingParkingId: String?,
        targetVehicleId: String?,
    ): Result<Unit> = if (editingParkingId != null) {
        updateParkingLocation(editingParkingId, gps).map { }
    } else {
        confirmParking(
            gps,
            USER_CONFIRMED_RELIABILITY,
            spotType,
            vehicleId = targetVehicleId,
            // [DET-PIN-PROVENANCE-001] Hand-placed pin ("manual") vs the user tapping "Sí" on a
            // detection prompt ("user") — both are user ground truth, distinguished by spotType.
            detectionPath = if (spotType == SpotType.MANUAL_REPORT) PATH_MANUAL else PATH_USER,
        )
            .onSuccess { saved ->
                notificationPort.showParkingSaved(saved.location.latitude, saved.location.longitude)
                manualParkingDetection.stop()
            }
            .map { }
    }

    private companion object {
        // A pin the user placed/confirmed by hand is ground truth.
        const val USER_CONFIRMED_RELIABILITY = 1.0f
        // Pin provenance paths. [DET-PIN-PROVENANCE-001]
        const val PATH_MANUAL = "manual"
        const val PATH_USER = "user"
    }
}
