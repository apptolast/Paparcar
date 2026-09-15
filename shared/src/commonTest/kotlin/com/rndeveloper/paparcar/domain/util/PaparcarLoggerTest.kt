package com.rndeveloper.paparcar.domain.util

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-A-CANCELLED-TICK-IS-NOT-A-FAILED-READ-001] `isReportableToCrashlytics` is the single place
 * that decides whether [PaparcarLogger.e] forwards a throwable to Crashlytics — a coroutine
 * cancellation must never count as a non-fatal, whichever of the 70+ call sites logs it.
 */
class PaparcarLoggerTest {

    private class FakeJobCancellationException : CancellationException("Job was cancelled")

    @Test
    fun `isReportableToCrashlytics is false for CancellationException`() {
        assertFalse(isReportableToCrashlytics(CancellationException("cancelled")))
    }

    @Test
    fun `isReportableToCrashlytics is false for CancellationException subtypes`() {
        assertFalse(isReportableToCrashlytics(FakeJobCancellationException()))
    }

    @Test
    fun `isReportableToCrashlytics is true for a real failure`() {
        assertTrue(isReportableToCrashlytics(IllegalStateException("boom")))
    }
}
