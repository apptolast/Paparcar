package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** iOS implementation of [GeofenceEventBus] backed by an unlimited [Channel].
 *
 * Identical shape to the Android impl — the bus is platform-agnostic. It exists
 * separately on iOS only because Koin singletons live inside platform modules
 * and we keep stub/real iOS bindings together.
 */
class IosGeofenceEventBusImpl : GeofenceEventBus {
    private val channel = Channel<GeofenceEvent>(Channel.UNLIMITED)
    override val events: Flow<GeofenceEvent> = channel.receiveAsFlow()
    override fun emit(event: GeofenceEvent) {
        channel.trySend(event)
    }
}
