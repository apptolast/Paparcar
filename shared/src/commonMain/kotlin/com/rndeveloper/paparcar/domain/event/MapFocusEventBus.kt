package com.rndeveloper.paparcar.domain.event

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MapFocusEventBus {
    private val _events = Channel<Pair<Double, Double>>(Channel.CONFLATED)
    val events = _events.receiveAsFlow()

    fun focusAt(lat: Double, lon: Double) {
        _events.trySend(Pair(lat, lon))
    }
}
