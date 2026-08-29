package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger

/**
 * Recording fake for [DetectionEventLogger]. Captures every logged event in order so
 * instrumentation tests (DET-LOG-03) can assert what the detection pipeline emitted.
 */
class FakeDetectionEventLogger : DetectionEventLogger {

    val events: MutableList<DetectionEvent> = mutableListOf()

    override suspend fun log(event: DetectionEvent) {
        events.add(event)
    }

    /** Events recorded for a single session, in emission order. */
    fun eventsFor(sessionId: String): List<DetectionEvent> =
        events.filter { it.sessionId == sessionId }
}
