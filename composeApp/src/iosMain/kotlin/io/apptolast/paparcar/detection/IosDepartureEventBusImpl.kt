package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.service.DepartureEventBus

/**
 * iOS [DepartureEventBus] backed by a `@Volatile` field — functional twin of Android's
 * [DepartureEventBusImpl]. The interface is just a thread-safe timestamp holder; both
 * platforms differ only in *who* writes the timestamp:
 *
 *  - Android: `ActivityTransitionReceiver` calls [onVehicleEntered] on IN_VEHICLE/ENTER.
 *  - iOS:     [IosActivityRecognitionManagerImpl] calls it when CMMotionActivity flips
 *             `automotive` from false → true.
 *
 * Same caveat as Android: state lives in process memory. If the process dies between
 * IN_VEHICLE_ENTER and the subsequent geofence-exit, the bus resets and
 * `DetectParkingDepartureUseCase` returns false (conservative: prefer a false negative
 * over a wrongly-published spot).
 */
class IosDepartureEventBusImpl : DepartureEventBus {

    @kotlin.concurrent.Volatile
    override var lastVehicleEnteredAt: Long? = null
        private set

    override fun onVehicleEntered(timestampMs: Long) {
        lastVehicleEnteredAt = timestampMs
    }

    override fun reset() {
        lastVehicleEnteredAt = null
    }
}
