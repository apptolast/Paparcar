package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.service.DepartureEventBus

/**
 * In-memory [DepartureEventBus] backed by a @Volatile field.
 *
 * The timestamp is held in process memory. If the process dies between
 * IN_VEHICLE_ENTER and the subsequent geofence-exit, the bus resets to null
 * and [DetectParkingDepartureUseCase] will return false (conservative: prefer
 * a false negative over publishing the spot incorrectly).
 */
class DepartureEventBusImpl : DepartureEventBus {

    @Volatile
    private var _lastVehicleEnteredAt: Long? = null

    override val lastVehicleEnteredAt: Long?
        get() = _lastVehicleEnteredAt

    override fun onVehicleEntered(timestampMs: Long) {
        _lastVehicleEnteredAt = timestampMs
    }

    override fun reset() {
        _lastVehicleEnteredAt = null
    }
}
