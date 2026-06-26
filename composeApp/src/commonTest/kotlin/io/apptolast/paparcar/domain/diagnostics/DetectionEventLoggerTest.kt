package io.apptolast.paparcar.domain.diagnostics

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.fakes.FakeDetectionEventLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectionEventLoggerTest {

    @Test
    fun should_swallow_every_event_when_logger_is_noop() = runTest {
        val logger = NoOpDetectionEventLogger()

        // No-op must accept any event type without throwing or mutating anything observable.
        logger.log(DetectionEvent.SessionStarted("s1", 1L, strategy = "COORDINATOR"))
        logger.log(DetectionEvent.Decision("s1", 2L, outcome = "CONFIRMED", pathLabel = "exit+steps"))

        // Reaching here without an exception is the assertion; assert true to keep the test explicit.
        assertTrue(true, "NoOp logger must never throw")
    }

    @Test
    fun should_record_events_in_order_when_using_recording_fake() = runTest {
        val logger = FakeDetectionEventLogger()
        val fix = GpsPoint(latitude = 50.08, longitude = 14.43, accuracy = 5f, timestamp = 10L, speed = 0f)

        logger.log(DetectionEvent.SessionStarted("praga", 1L, strategy = "COORDINATOR", vehicleType = "CAR"))
        logger.log(DetectionEvent.ActivityTransition("praga", 2L, activity = "IN_VEHICLE", transition = "EXIT"))
        logger.log(DetectionEvent.Step("praga", 3L, stepCount = 8, stopped = true, location = fix))
        logger.log(DetectionEvent.Decision("praga", 4L, outcome = "REJECTED", pathLabel = "exit+steps", confidence = null))
        // A second session interleaved — eventsFor must not leak across sessions.
        logger.log(DetectionEvent.SessionStarted("other", 5L, strategy = "BLUETOOTH"))

        assertEquals(5, logger.events.size, "every logged event is captured")

        val praga = logger.eventsFor("praga")
        assertEquals(4, praga.size, "eventsFor isolates a single session")
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            praga.map { it.timestampMs },
            "events are preserved in emission order",
        )
        assertEquals(fix, (praga[2] as DetectionEvent.Step).location, "GPS context survives the round-trip")
    }
}
