package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.service.GeofenceEvent
import com.rndeveloper.paparcar.domain.service.GeofenceEventBus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Android implementation of [GeofenceEventBus] backed by an unlimited [Channel]. */
class GeofenceEventBusImpl : GeofenceEventBus {
    private val _channel = Channel<GeofenceEvent>(Channel.UNLIMITED)
    override val events: Flow<GeofenceEvent> = _channel.receiveAsFlow()
    override fun emit(event: GeofenceEvent) {
        _channel.trySend(event)
    }
}
