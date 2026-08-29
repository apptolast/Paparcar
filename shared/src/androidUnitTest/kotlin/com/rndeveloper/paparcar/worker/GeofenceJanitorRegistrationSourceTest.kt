package com.rndeveloper.paparcar.worker

import com.rndeveloper.paparcar.detection.worker.GeofenceJanitorWorker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The registration event's `source` label contract: `janitor:<trigger>`, with absent input data
 * reading as the periodic — the installed KEEP periodic is never re-created, so absence is the
 * only honest value it can carry. Remote queries group the lane by the `janitor` prefix; breaking
 * either side silently splits the telemetry the policy cut depends on.
 * [DET-JANITOR-LANE-TELLS-ONCE-FROM-PERIODIC-001]
 */
class GeofenceJanitorRegistrationSourceTest {

    @Test
    fun should_labelAsPeriodic_when_noTriggerInInputData() {
        assertEquals("janitor:periodic", GeofenceJanitorWorker.registrationSource(null))
    }

    @Test
    fun should_labelEachOnceTrigger_when_provided() {
        assertEquals("janitor:boot", GeofenceJanitorWorker.registrationSource(GeofenceJanitorWorker.TRIGGER_BOOT))
        assertEquals("janitor:app-update", GeofenceJanitorWorker.registrationSource(GeofenceJanitorWorker.TRIGGER_APP_UPDATE))
        assertEquals("janitor:app-start", GeofenceJanitorWorker.registrationSource(GeofenceJanitorWorker.TRIGGER_APP_START))
        assertEquals("janitor:post-sync", GeofenceJanitorWorker.registrationSource(GeofenceJanitorWorker.TRIGGER_POST_SYNC))
    }

    @Test
    fun should_keepJanitorAsPrefix_forEveryTrigger() {
        // The prefix IS the lane grouping contract for remote queries.
        listOf(
            null,
            GeofenceJanitorWorker.TRIGGER_BOOT,
            GeofenceJanitorWorker.TRIGGER_APP_UPDATE,
            GeofenceJanitorWorker.TRIGGER_APP_START,
            GeofenceJanitorWorker.TRIGGER_POST_SYNC,
        ).forEach { trigger ->
            assertTrue(
                GeofenceJanitorWorker.registrationSource(trigger)
                    .startsWith(GeofenceJanitorWorker.REGISTRATION_SOURCE_JANITOR + ":"),
            )
        }
    }
}
