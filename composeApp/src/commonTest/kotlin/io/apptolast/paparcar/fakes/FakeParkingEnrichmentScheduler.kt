package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler

class FakeParkingEnrichmentScheduler : ParkingEnrichmentScheduler {

    var scheduleCallCount = 0
    var lastScheduledSessionId: String? = null

    override fun schedule(sessionId: String, lat: Double, lon: Double) {
        scheduleCallCount++
        lastScheduledSessionId = sessionId
    }
}
