package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class StubGeofenceEventBus : GeofenceEventBus {
    private val channel = Channel<GeofenceEvent>(Channel.BUFFERED)
    override val events: Flow<GeofenceEvent> = channel.receiveAsFlow()
    override fun emit(event: GeofenceEvent) {
        channel.trySend(event)
    }
}
