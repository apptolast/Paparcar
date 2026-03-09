package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler

class StubParkingEnrichmentScheduler : ParkingEnrichmentScheduler {
    override fun schedule(sessionId: String, lat: Double, lon: Double) {}
}
