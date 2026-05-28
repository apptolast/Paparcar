package io.apptolast.paparcar.domain.sensor

import kotlinx.coroutines.flow.Flow

/**
 * Hardware step-event source used by [io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator]
 * to distinguish a real parking from a queue/traffic stop.
 *
 * **Why this exists.** Activity Recognition fires `STILL ENTER` whenever the device sits still long
 * enough — semáforo, atasco, cola del parking, garaje esperando que abra la puerta. None of those are
 * actual parkings. The unambiguous signal that the user has parked is **the user got out and started
 * walking**. The hardware step detector reports one event per detected pedestrian step regardless of
 * GPS, so it works inside garages (no sky view) and is robust against the variable "walking distance"
 * a real parking can involve (4 m to the elevator vs 50 m across a public lot).
 *
 * **Permission.** `ACTIVITY_RECOGNITION` (Android 10+). Already requested for Activity Transitions —
 * no new runtime permission needed.
 *
 * **Lifecycle.** Implementations register the sensor listener on `collect` and unregister on
 * cancellation. Multiple collectors are not expected; if needed, wrap in `shareIn`.
 *
 * **Hardware availability.** A small number of pre-API-19 devices lack `Sensor.TYPE_STEP_DETECTOR`.
 * Implementations should emit an empty/no-op flow in that case so the coordinator falls back to its
 * timeout-based path instead of hanging.
 */
interface StepDetectorSource {
    /** Cold flow that emits [Unit] once per detected pedestrian step while collected. */
    fun steps(): Flow<Unit>
}
