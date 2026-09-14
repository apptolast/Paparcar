package com.rndeveloper.paparcar.domain.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001] The funnel every closing path passes a
 * candidate departure instant through before it may date a departure with it.
 */
class MeasuredDepartureInstantTest {

    private val sessionStart = 1_000_000_000_000L
    private val now = sessionStart + 60 * 60_000L // an hour of parking

    @Test
    fun should_returnTheInstant_when_itWasMeasuredDuringThisSession() {
        val crossedAt = sessionStart + 45 * 60_000L

        assertEquals(crossedAt, measuredDepartureAtMs(crossedAt, sessionStart, now))
    }

    @Test
    fun should_returnNull_when_nothingMeasuredTheDeparture() {
        // The whole point of the ticket: the refusal is null, and null is what the freshness gate
        // reads as "an unknown hour is not a recent hour". No caller may substitute `now` for it.
        assertNull(measuredDepartureAtMs(null, sessionStart, now))
    }

    @Test
    fun should_returnNull_when_theInstantPredatesTheSession() {
        // Evidence older than the parking describes the trip that CREATED it, or an earlier life
        // entirely — it cannot date a departure FROM it. [DET-SESSION-BIRTH-001]
        val beforeTheCarArrived = sessionStart - 1L

        assertNull(measuredDepartureAtMs(beforeTheCarArrived, sessionStart, now))
    }

    @Test
    fun should_acceptTheInstant_when_itCoincidesWithTheSessionBirth() {
        // The admissibility boundary is inclusive, same as every other reader of
        // `isAdmissibleEvidence`: a departure at the very instant of the park is degenerate but
        // not inadmissible, and the freshness gate is the one that judges how old it is.
        assertEquals(sessionStart, measuredDepartureAtMs(sessionStart, sessionStart, now))
    }

    @Test
    fun should_returnNull_when_theInstantIsInTheFuture() {
        // A provider clock skewed forward would make a long-gone plaza look younger than it is —
        // this ticket's own failure arriving through the back door. A departure cannot have
        // happened after now.
        assertNull(measuredDepartureAtMs(now + 1L, sessionStart, now))
    }

    @Test
    fun should_returnTheInstant_when_thereIsNoSessionToContradictIt() {
        // No session in hand is not a reason to discard a measured instant — the project's rule on
        // SIGNALS is that a stale one loses authority, never that it is thrown away. Mirrors
        // `isAdmissibleEvidence(_, null) == true`.
        val crossedAt = now - 5 * 60_000L

        assertEquals(crossedAt, measuredDepartureAtMs(crossedAt, sessionStartMs = null, nowMs = now))
    }
}
