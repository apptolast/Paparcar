package io.apptolast.paparcar.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer event bus for geofence transitions.
 *
 * Decouples [GeofenceBroadcastReceiver] (Android platform) from
 * the [GeofenceService] implementation, eliminating the static
 * companion-object coupling that caused a circular dependency.
 *
 * Implementations must be registered as singletons in the DI graph
 * so that both the Receiver and the Manager share the same instance.
 */
interface GeofenceEventBus {
    /** Hot flow of geofence events emitted by the platform receiver. */
    val events: Flow<GeofenceEvent>

    /** Emits [event] into the shared flow. Thread-safe (fire-and-forget). */
    fun emit(event: GeofenceEvent)
}
