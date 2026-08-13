package io.apptolast.paparcar.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer event bus for geofence transitions — the ONE in-process observation contract on
 * every platform. [IOS-F0-04, resolves audit G2]
 *
 * What it is: a **hot, broadcast, non-durable** stream. Every active subscriber receives every
 * event emitted while subscribed; an event emitted with no subscriber is DROPPED. This is an
 * observation surface, not a durable queue — durability lives in side-records by doctrine
 * [DET-B-02]: a consumer that must not miss a transition (the iOS wake orchestrator) subscribes
 * BEFORE the platform delegate can fire and reconciles from side-records + OS state on start,
 * never by replaying this bus.
 *
 * What it is NOT: the process-revival transport. On Android the OS starts the detection service
 * directly (PendingIntent, RC 9100/9101/9102) and the service acts on the intent FIRST, then
 * republishes here for observers; on iOS the CoreLocation delegate publishes here and the bus is
 * the primary consumption path.
 *
 * Implementations must be registered as singletons in the DI graph so that the platform emitter
 * and every observer share the same instance.
 */
interface GeofenceEventBus {
    /** Hot broadcast flow of geofence events. Subscribe before events matter; no replay. */
    val events: Flow<GeofenceEvent>

    /** Emits [event] to all current subscribers. Thread-safe, fire-and-forget, never blocks;
     *  with no subscriber the event is dropped (see the durability note above). */
    fun emit(event: GeofenceEvent)
}
