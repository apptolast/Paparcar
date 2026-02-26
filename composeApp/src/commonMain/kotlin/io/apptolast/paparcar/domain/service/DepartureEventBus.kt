package io.apptolast.paparcar.domain.service

/**
 * Domain-layer event bus that records the timestamp of the last IN_VEHICLE_ENTER
 * activity-recognition transition.
 *
 * Used by [DetectParkingDepartureUseCase] to verify that the user recently entered
 * a vehicle before the geofence-exit fired — the key signal that distinguishes
 * "drove away in own car" from "went for a walk".
 *
 * Implementations must be registered as **singletons** so that
 * [ActivityTransitionReceiver] (writer) and [CheckDepartureWorker] (reader)
 * always share the same in-memory instance.
 */
interface DepartureEventBus {

    /** Epoch-ms timestamp of the last IN_VEHICLE_ENTER, or null if not yet recorded. */
    val lastVehicleEnteredAt: Long?

    /** Called by [ActivityTransitionReceiver] when an IN_VEHICLE_ENTER transition fires. */
    fun onVehicleEntered(timestampMs: Long)

    /**
     * Clears the stored timestamp once a confirmed departure has been handled.
     * Called by [CheckDepartureWorker] after successfully enqueuing [ReportSpotWorker].
     */
    fun reset()
}
