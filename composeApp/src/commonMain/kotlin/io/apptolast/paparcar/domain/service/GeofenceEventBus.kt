package io.apptolast.paparcar.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer event bus for geofence transitions.
 *
 * Decouples the platform geofence delivery (Android: `CoordinatorDetectionService.handleGeofenceExit`)
 * from the [GeofenceManager] implementation, eliminating the static companion-object coupling that
 * caused a circular dependency.
 *
 * Implementations must be registered as singletons in the DI graph so that both the emitter (the
 * detection service) and the Manager share the same instance.
 */
interface GeofenceEventBus {
    /** Hot flow of geofence events emitted by the platform geofence-exit handler. */
    val events: Flow<GeofenceEvent>

    /** Emits [event] into the shared flow. Thread-safe (fire-and-forget). */
    fun emit(event: GeofenceEvent)
}
