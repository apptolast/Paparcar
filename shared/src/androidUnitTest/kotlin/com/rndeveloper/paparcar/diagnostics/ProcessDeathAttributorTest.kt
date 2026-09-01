package com.rndeveloper.paparcar.diagnostics

import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-MEMORY-LIMITER-IS-AN-ATTRIBUTABLE-KILL-001] The attribution table and the once-per-death
 * dedup are the two halves that make a parkdiag gap citable — and both are pure decisions that
 * would fail silently: a wrong mapping stamps the WRONG cause into every field diagnosis that
 * quotes it, and a broken watermark either re-reports the same death on every start (noise that
 * buries the census) or never reports at all (the gap stays anonymous, which is the bug the
 * ticket exists to end).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProcessDeathAttributorTest {

    private class RecordingLogger : DetectionEventLogger {
        val events = mutableListOf<DetectionEvent>()
        override suspend fun log(event: DetectionEvent) {
            events += event
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    // ── Attribution table ─────────────────────────────────────────────────────

    @Test
    fun should_mapEachPlatformReason_when_attributingAnExit() {
        assertEquals("force_stop", attributeExit(ApplicationExitInfo.REASON_USER_REQUESTED, null))
        assertEquals("force_stop", attributeExit(ApplicationExitInfo.REASON_USER_STOPPED, null))
        assertEquals("low_memory", attributeExit(ApplicationExitInfo.REASON_LOW_MEMORY, null))
        assertEquals("crash", attributeExit(ApplicationExitInfo.REASON_CRASH, null))
        assertEquals("crash", attributeExit(ApplicationExitInfo.REASON_CRASH_NATIVE, null))
        assertEquals("anr", attributeExit(ApplicationExitInfo.REASON_ANR, null))
        assertEquals(
            "excessive_resource",
            attributeExit(ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, null),
        )
        assertEquals("self_exit", attributeExit(ApplicationExitInfo.REASON_EXIT_SELF, null))
    }

    @Test
    fun should_attributeMemoryLimiter_when_reasonOtherCarriesTheAndroid17Description() {
        assertEquals(
            "memory_limiter",
            attributeExit(ApplicationExitInfo.REASON_OTHER, "MemoryLimiter:AnonSwap"),
        )
        // The description is a string, not a contract: anything else degrades to `other`.
        assertEquals("other", attributeExit(ApplicationExitInfo.REASON_OTHER, "SomethingElse"))
        assertEquals("other", attributeExit(ApplicationExitInfo.REASON_OTHER, null))
    }

    @Test
    fun should_degradeToOther_when_theReasonCodeIsUnknownToTheTable() {
        assertEquals("other", attributeExit(ApplicationExitInfo.REASON_SIGNALED, null))
        assertEquals("other", attributeExit(999, null))
    }

    // ── Watermark dedup ───────────────────────────────────────────────────────

    @Test
    fun should_reportOnlyStrictlyNewerExits_when_aWatermarkExists() {
        val records = listOf(
            ExitRecord(ApplicationExitInfo.REASON_EXIT_SELF, 0, null, timestampMs = 1_000L),
            ExitRecord(ApplicationExitInfo.REASON_USER_REQUESTED, 0, null, timestampMs = 2_000L),
            ExitRecord(ApplicationExitInfo.REASON_LOW_MEMORY, 0, null, timestampMs = 3_000L),
        )
        val fresh = newExitsSince(records, watermarkMs = 2_000L)
        assertEquals(listOf(3_000L), fresh.map { it.timestampMs })
    }

    @Test
    fun should_emitOldestFirst_when_thePlatformReturnsNewestFirst() {
        val records = listOf(
            ExitRecord(ApplicationExitInfo.REASON_CRASH, 0, null, timestampMs = 3_000L),
            ExitRecord(ApplicationExitInfo.REASON_ANR, 0, null, timestampMs = 1_000L),
        )
        assertEquals(listOf(1_000L, 3_000L), newExitsSince(records, 0L).map { it.timestampMs })
    }

    // ── End-to-end report(): emit + advance watermark ─────────────────────────

    @Test
    fun should_emitOneEventPerDeathAndNeverRepeatIt_when_reportedAcrossTwoStarts() = runTest {
        val logger = RecordingLogger()
        val attributor = ProcessDeathAttributor(context, logger)
        val death = ExitRecord(
            ApplicationExitInfo.REASON_USER_REQUESTED,
            status = 0,
            description = null,
            timestampMs = 5_000L,
        )

        attributor.report(listOf(death), nowMs = 10_000L)
        // Second start sees the same platform history: the watermark must silence it.
        attributor.report(listOf(death), nowMs = 20_000L)

        assertEquals(1, logger.events.size)
        val event = logger.events.single() as DetectionEvent.ProcessDeath
        assertEquals("force_stop", event.reason)
        assertEquals(5_000L, event.deathAgeMs)
        assertTrue(event.sessionId.startsWith("triggers_"), "files under the daily trigger ledger")
    }

    @Test
    fun should_emitNothing_when_thereAreNoNewDeaths() = runTest {
        val logger = RecordingLogger()
        ProcessDeathAttributor(context, logger).report(emptyList(), nowMs = 10_000L)
        assertEquals(0, logger.events.size)
    }
}
