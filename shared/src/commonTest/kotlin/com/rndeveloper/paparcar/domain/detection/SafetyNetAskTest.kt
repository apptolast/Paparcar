package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-A-STILL-PARKED-ASK-MUST-EARN-ITS-INTERRUPTION-001] The per-fence interruption budget.
 *
 * It is a function, not an `if` inside a `when` arm, precisely because as an `if` it guarded only
 * ONE of the two doors: the arrival ask neither read the stamp nor wrote it, so the same unanswered
 * question could come back on every exact-alarm tick (~5 min) without ever spending the window.
 */
class SafetyNetAskTest {

    private val throttleMs = ParkingDetectionConfig().safetyNetAskThrottleMs
    private val nowMs = 1_700_000_000_000L

    @Test
    fun should_allowTheAsk_when_thisFenceWasNeverAsked() {
        assertTrue(parkingAskIsDue(lastAskAtMs = null, nowMs = nowMs, throttleMs = throttleMs))
    }

    @Test
    fun should_allowTheAsk_when_theStampIsTheSharedPreferencesDefault() {
        // `prefs.getLong(key, 0L)` on a fence that was never asked: 0 is "no record", not "asked at
        // the epoch" — and certainly not a budget spent 56 years ago.
        assertTrue(parkingAskIsDue(lastAskAtMs = 0L, nowMs = nowMs, throttleMs = throttleMs))
    }

    @Test
    fun should_suppressTheAsk_when_theBudgetHasNotRefilled() {
        assertFalse(
            parkingAskIsDue(lastAskAtMs = nowMs - throttleMs + 1, nowMs = nowMs, throttleMs = throttleMs),
        )
    }

    @Test
    fun should_allowTheAsk_when_theWindowHasExactlyElapsed() {
        assertTrue(parkingAskIsDue(lastAskAtMs = nowMs - throttleMs, nowMs = nowMs, throttleMs = throttleMs))
    }

    @Test
    fun should_allowTheAsk_when_theStampSitsInTheFuture() {
        // Clock moved back, or a restored backup brought someone else's timestamps. Reading that as
        // "asked recently" would mute this fence until the clock caught up — hours of silence bought
        // by corrupt state. An unusable record reads like no record.
        assertTrue(parkingAskIsDue(lastAskAtMs = nowMs + 60_000L, nowMs = nowMs, throttleMs = throttleMs))
    }

    @Test
    fun should_giveEachQuestionItsOwnVerdictLabel() {
        // The arrival ask used to leave NO verdict at all — only a log line — so it was invisible in
        // `diagnostics/{uid}/sessions` and nobody could count how often it fired.
        assertEquals("safety_net_prompt", ParkingAskKind.StillParked.verdictLabel)
        assertEquals("safety_net_arrival_ask", ParkingAskKind.WhereDidYouPark.verdictLabel)
        assertEquals(
            ParkingAskKind.entries.size,
            ParkingAskKind.entries.map { it.verdictLabel }.toSet().size,
            "two questions sharing one label is how the departure ask ended up answering for the arrival",
        )
    }
}
