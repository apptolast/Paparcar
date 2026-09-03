@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.DetectionPath
import com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
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
     * @param fromDetectionNudge true when a DETECTION nudge ("Marcar mi plaza" notification /
     *   sheet row) opened this pin mode: detection nominated the park, the user confirmed it —
     *   the session keeps detection provenance (`AUTO_DETECTED`, path `nudge`) so the freed spot
     *   later publishes as auto (2 h TTL) instead of a 15-min manual report.
     *   [DET-NUDGE-PIN-PROVENANCE-001]
     */
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        accuracy: Float,
        editingParkingId: String? = null,
        targetVehicleId: String? = null,
        fromDetectionNudge: Boolean = false,
    ): Result<Unit> = save(
        gps = GpsPoint(
            latitude = lat,
            longitude = lon,
            accuracy = accuracy,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            speed = 0f,
        ),
        spotType = if (fromDetectionNudge) SpotType.AUTO_DETECTED else SpotType.MANUAL_REPORT,
        detectionPath = if (fromDetectionNudge) PATH_NUDGE else PATH_MANUAL,
        editingParkingId = editingParkingId,
        targetVehicleId = targetVehicleId,
    )

    private suspend fun save(
        gps: GpsPoint,
        spotType: SpotType,
        detectionPath: String,
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
            // [DET-PIN-PROVENANCE-001] Hand-placed pin ("manual") vs "Sí" on the detection prompt
            // ("user") vs pin placed answering a detection nudge ("nudge") — all three are user
            // ground truth; spotType + path record who nominated the park.
            detectionPath = detectionPath,
            // A hand-placed pin is marked with the user standing at/near it; the pin doubles as
            // the body's position within map-drag noise. [DET-STEP-BUDGET-ORIGIN-001]
            sealPoint = gps,
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
        // Pin provenance paths, read off the TYPE so a path can never be spelled twice.
        // [DET-PIN-PROVENANCE-001][DET-NUDGE-PIN-PROVENANCE-001][PARK-A-PIN-MUST-SAY-WHO-PLACED-IT-001]
        //
        // `UserAnswered` is NOT here any more: it belonged to `confirmDetected`, the entry point of a
        // modal nobody could reach [UI-THE-PARKING-CONFIRMATION-MODAL-IS-UNREACHABLE-001]. Answering
        // the real question goes through the detection service (ACTION_PARKING_CONFIRMED), which
        // stamps its own path.
        val PATH_MANUAL = DetectionPath.ManualPin.label
        val PATH_NUDGE = DetectionPath.Nudge.label
        // The MOVE branch has no path constant here on purpose: it does not build a pin, it hands
        // the id to [UpdateParkingLocationUseCase], which owns the provenance of a drag
        // (`user_moved`). A constant here would be a second place to spell it — the exact defect
        // this ticket removes. [PARK-A-PIN-MUST-SAY-WHO-PLACED-IT-001]
    }
}
