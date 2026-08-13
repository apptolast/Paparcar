package io.apptolast.paparcar.domain

import io.apptolast.paparcar.domain.coordinator.ingestion.DetectionTraceIngestion
import io.apptolast.paparcar.domain.coordinator.ingestion.TraceEvent
import io.apptolast.paparcar.fakes.FakeActivityRecognitionManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [IOS-F0-05] The pull lane of [ActivityRecognitionManager]: recorded transitions are queryable
 * by range, default to empty on push platforms, and bridge into the wake-and-query trace.
 */
class ActivityRecognitionQueryTest {

    private val t0 = 1_783_185_093_721L

    @Test
    fun should_return_empty_when_the_platform_is_push_only() = runTest {
        // The interface default IS the Android answer — a push platform never records history.
        val pushOnly = object : ActivityRecognitionManager {
            override fun registerTransitions() = Unit
            override fun unregisterTransitions() = Unit
        }
        assertTrue(pushOnly.queryTransitions(0L, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun should_return_only_transitions_inside_the_range_in_timestamp_order() = runTest {
        val fake = FakeActivityRecognitionManager()
        fake.recordedTransitions = listOf(
            ActivityTransitionEvent(t0 + 900_000L, TraceEvent.Activity.VEHICLE_EXIT),
            ActivityTransitionEvent(t0 - 60_000L, TraceEvent.Activity.VEHICLE_EXIT), // before range
            ActivityTransitionEvent(t0 + 10_000L, TraceEvent.Activity.VEHICLE_ENTER),
        )

        val result = fake.queryTransitions(t0, t0 + 1_000_000L)

        assertEquals(2, result.size, "the pre-range transition must be excluded")
        assertEquals(TraceEvent.Activity.VEHICLE_ENTER, result[0].activity)
        assertEquals(TraceEvent.Activity.VEHICLE_EXIT, result[1].activity)
        assertTrue(result[0].tMs < result[1].tMs, "ascending order is part of the contract")
    }

    @Test
    fun should_deliver_queried_transitions_through_the_trace_in_timestamp_order() = runTest {
        // The wake-and-query composition the iOS orchestrator will run: query → toTraceEvent →
        // ingest alongside fixes, everything interleaved by timestamp.
        val fake = FakeActivityRecognitionManager()
        fake.recordedTransitions = listOf(
            ActivityTransitionEvent(t0 + 20_000L, TraceEvent.Activity.VEHICLE_EXIT),
        )
        val trace = (
            fake.queryTransitions(t0, t0 + 60_000L).map { it.toTraceEvent() } +
                listOf(
                    TraceEvent(t0 + 10_000L, TraceEvent.Kind.FIX, 36.6, -6.25, 10f, 8f),
                    TraceEvent(t0 + 30_000L, TraceEvent.Kind.FIX, 36.6, -6.25, 10f, 0f),
                    TraceEvent(t0 + 40_000L, TraceEvent.Kind.STEP),
                )
            )

        val delivered = mutableListOf<String>()
        DetectionTraceIngestion(trace).replay(
            emitFix = { delivered.add("FIX@${it.timestamp - t0}") },
            emitStep = { delivered.add("STEP@40000") },
            emitActivity = { activity, trueTimeMs -> delivered.add("${activity.name}@${trueTimeMs - t0}") },
        )

        assertEquals(
            listOf("FIX@10000", "VEHICLE_EXIT@20000", "FIX@30000", "STEP@40000"),
            delivered,
            "the queried transition must interleave at its recorded time, not at query time",
        )
    }
}
