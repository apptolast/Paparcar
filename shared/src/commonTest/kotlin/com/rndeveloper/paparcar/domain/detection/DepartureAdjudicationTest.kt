package com.rndeveloper.paparcar.domain.detection

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-TWO-DISPATCHES-OF-ONE-DEPARTURE-READ-DIFFERENT-STATE-001 · fase 2] One fact, one
 * adjudication.
 *
 * The measured case is field 2026-08-30 21:27 (Oppo): the same fence dispatched twice, 596 ms
 * apart, where the second pass read a step budget the first had just spent. These pin the rule that
 * the second observation adds no evidence — and the ONE thing that still outranks an open
 * adjudication, which is not arrival order but strictly more proof.
 */
class DepartureAdjudicationTest {

    private val windowMs = 5 * 60 * 1_000L
    private val t0 = 1_788_000_000_000L

    private fun open(atMs: Long = t0, preconfirmed: Boolean = false) =
        OpenDepartureAdjudication(openedAtMs = atMs, preconfirmed = preconfirmed)

    @Test
    fun should_adjudicate_when_no_adjudication_is_open() {
        assertEquals(
            DepartureAdjudicationVerdict.Adjudicate,
            adjudicateDeparture(open = null, nowMs = t0, observationPreconfirmed = false, windowMs = windowMs),
        )
    }

    @Test
    fun should_adhere_when_a_second_observation_lands_596ms_after_the_first() {
        // The field gap verbatim: two safety-net wakes on one fence. The second must not
        // re-adjudicate — its reading of the witness slot is an artifact of the first pass.
        assertEquals(
            DepartureAdjudicationVerdict.Adhere,
            adjudicateDeparture(
                open = open(),
                nowMs = t0 + 596L,
                observationPreconfirmed = false,
                windowMs = windowMs,
            ),
        )
    }

    @Test
    fun should_adhere_when_a_plain_observation_follows_a_preconfirmed_adjudication() {
        // Less proof arriving later changes nothing: `preconfirmed` is a claim about the trip, and
        // dropping it would let a weaker observation reopen a settled fact by arriving second.
        assertEquals(
            DepartureAdjudicationVerdict.Adhere,
            adjudicateDeparture(
                open = open(preconfirmed = true),
                nowMs = t0 + 2_000L,
                observationPreconfirmed = false,
                windowMs = windowMs,
            ),
        )
    }

    @Test
    fun should_upgrade_when_the_observation_proves_the_trip_already_ended() {
        // The one legitimate re-open: `preconfirmed` says the trip is OVER, so the departure worker
        // must skip the speed re-check that would wrongly veto it. Strictly more evidence about the
        // same fact — not a matter of who arrived first.
        assertEquals(
            DepartureAdjudicationVerdict.Upgrade,
            adjudicateDeparture(
                open = open(preconfirmed = false),
                nowMs = t0 + 2_000L,
                observationPreconfirmed = true,
                windowMs = windowMs,
            ),
        )
    }

    @Test
    fun should_adhere_when_both_the_open_adjudication_and_the_observation_are_preconfirmed() {
        assertEquals(
            DepartureAdjudicationVerdict.Adhere,
            adjudicateDeparture(
                open = open(preconfirmed = true),
                nowMs = t0 + 2_000L,
                observationPreconfirmed = true,
                windowMs = windowMs,
            ),
        )
    }

    @Test
    fun should_adjudicate_again_when_the_window_has_elapsed() {
        // Past the window the same fence breaking is a NEW fact — park, leave, come back, leave
        // again. The window bounds what counts as one fact, never how often the net may act.
        assertEquals(
            DepartureAdjudicationVerdict.Adjudicate,
            adjudicateDeparture(
                open = open(),
                nowMs = t0 + windowMs + 1L,
                observationPreconfirmed = false,
                windowMs = windowMs,
            ),
        )
    }

    @Test
    fun should_keep_adhering_on_the_last_millisecond_of_the_window() {
        // The boundary belongs to the open adjudication: a retry still in flight at the very edge
        // must not be re-adjudicated underneath itself.
        assertEquals(
            DepartureAdjudicationVerdict.Adhere,
            adjudicateDeparture(
                open = open(),
                nowMs = t0 + windowMs,
                observationPreconfirmed = false,
                windowMs = windowMs,
            ),
        )
    }

    @Test
    fun should_adjudicate_when_the_clock_went_backwards() {
        // Reboot or NTP correction: a seal we cannot date is a seal we cannot trust to still be
        // open, and freezing a fence forever is the one failure this must never produce.
        assertEquals(
            DepartureAdjudicationVerdict.Adjudicate,
            adjudicateDeparture(
                open = open(atMs = t0),
                nowMs = t0 - 10_000L,
                observationPreconfirmed = false,
                windowMs = windowMs,
            ),
        )
    }
}
