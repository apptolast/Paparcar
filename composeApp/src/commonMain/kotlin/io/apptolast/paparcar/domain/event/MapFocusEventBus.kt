package io.apptolast.paparcar.domain.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MapFocusEventBus {
    private val _events = MutableSharedFlow<Pair<Double, Double>>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun focusAt(lat: Double, lon: Double) {
        _events.tryEmit(Pair(lat, lon))
    }
}
