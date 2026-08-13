package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The single [GeofenceEventBus] implementation, shared by every platform. [IOS-F0-04]
 *
 * Replaces the two identical platform impls that were backed by `Channel(UNLIMITED)` — channel
 * semantics were wrong for an observation surface on both counts: a channel delivers each event
 * to ONE consumer (two subscribers would steal events from each other), and with no consumer it
 * buffers forever (on Android nothing collected the bus, so every emit was an unbounded
 * dead-letter). A replay-less shared flow gives the contract exactly: broadcast to all current
 * subscribers, drop when there are none, never block the emitter.
 */
class SharedFlowGeofenceEventBus : GeofenceEventBus {
    private val _events = MutableSharedFlow<GeofenceEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<GeofenceEvent> = _events
    override fun emit(event: GeofenceEvent) {
        _events.tryEmit(event)
    }
}
