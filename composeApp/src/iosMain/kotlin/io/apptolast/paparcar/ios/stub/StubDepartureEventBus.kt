package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.service.DepartureEventBus

class StubDepartureEventBus : DepartureEventBus {
    @kotlin.concurrent.Volatile
    override var lastVehicleEnteredAt: Long? = null
        private set

    override fun onVehicleEntered(timestampMs: Long) {
        lastVehicleEnteredAt = timestampMs
    }

    override fun reset() {
        lastVehicleEnteredAt = null
    }
}
