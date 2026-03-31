package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.service.DepartureEventBus

class FakeDepartureEventBus(initialTimestamp: Long? = null) : DepartureEventBus {

    override var lastVehicleEnteredAt: Long? = initialTimestamp
    var resetCount = 0

    override fun onVehicleEntered(timestampMs: Long) {
        lastVehicleEnteredAt = timestampMs
    }

    override fun reset() {
        lastVehicleEnteredAt = null
        resetCount++
    }
}
