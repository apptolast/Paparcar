package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.service.GeofenceEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [IOS-F0-04] Contract of the single geofence event bus: hot BROADCAST (every subscriber gets
 * every event — the channel impl it replaces delivered each event to only one), non-durable
 * (no subscriber → dropped, no replay), and a never-blocking emitter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedFlowGeofenceEventBusTest {

    @Test
    fun should_deliverEveryEventToEverySubscriber_when_multipleObserversAreActive() =
        runTest(UnconfinedTestDispatcher()) {
            val bus = SharedFlowGeofenceEventBus()
            val first = mutableListOf<GeofenceEvent>()
            val second = mutableListOf<GeofenceEvent>()
            val jobA = launch { bus.events.collect { first.add(it) } }
            val jobB = launch { bus.events.collect { second.add(it) } }

            bus.emit(GeofenceEvent.Exited(geofenceId = "session-1", timestamp = 1_000L))
            bus.emit(GeofenceEvent.Error(error = "code 1000", timestamp = 2_000L))

            jobA.cancelAndJoin()
            jobB.cancelAndJoin()
            assertEquals(2, first.size, "first subscriber must see both events")
            assertEquals(first, second, "broadcast: the second subscriber must not steal events")
        }

    @Test
    fun should_dropEventsWithoutSubscriberAndNeverReplay_when_observerArrivesLate() =
        runTest(UnconfinedTestDispatcher()) {
            // The doctrine the contract encodes [DET-B-02]: the bus is an observation surface,
            // not a durable queue — a consumer that must not miss transitions subscribes first
            // and reconciles from side-records, never from bus replay.
            val bus = SharedFlowGeofenceEventBus()
            bus.emit(GeofenceEvent.Exited(geofenceId = "before-anyone", timestamp = 1_000L))

            val seen = mutableListOf<GeofenceEvent>()
            val job = launch { bus.events.collect { seen.add(it) } }
            bus.emit(GeofenceEvent.Exited(geofenceId = "after-subscribe", timestamp = 2_000L))

            job.cancelAndJoin()
            assertEquals(1, seen.size, "the pre-subscription event must be dropped, not replayed")
            assertTrue((seen.single() as GeofenceEvent.Exited).geofenceId == "after-subscribe")
        }
}
