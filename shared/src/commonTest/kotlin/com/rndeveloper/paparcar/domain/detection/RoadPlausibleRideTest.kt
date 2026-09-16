package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-A-TRAIN-IS-NOT-A-CAR-001] The road-plausibility ceiling.
 *
 * The numbers in these scenarios are the ones the ticket was calibrated against, so the test doubles
 * as the record of WHY each bar sits where it does:
 *
 *  - 131,4 km/h — the fastest drive in the whole 18-trace field corpus (`Trace_MotorwayRedmi001`);
 *  - 160 km/h — above every legal motorway limit in the nine markets (Poland's 140 is the highest)
 *    and at the ~99th percentile of unrestricted German Autobahn traffic;
 *  - 253 km/h — what the 2026-09-15 session measured for most of 270 minutes.
 */
class RoadPlausibleRideTest {

    private val config = ParkingDetectionConfig()

    private fun judge(
        doubtfulBandMs: Long = 0L,
        impossibleBandMs: Long = 0L,
        journeyRateMps: Float = 0f,
    ) = judgeRoadPlausibility(
        roadDoubtfulBandMs = doubtfulBandMs,
        roadImpossibleBandMs = impossibleBandMs,
        journeyGroundRateMps = journeyRateMps,
        config = config,
    )

    @Test
    fun should_readAsPlausible_when_theSessionNeverLeftRoadSpeeds() {
        // The overwhelming majority of sessions, and the fastest real drive on file: 131,4 km/h
        // never touches either band, so nothing about this verdict can reach an ordinary trip.
        assertEquals(RoadPlausibility.PLAUSIBLE, judge())
    }

    @Test
    fun should_doubt_when_theSessionHeldASpeedNoLegalMotorwayAllows() {
        assertEquals(
            RoadPlausibility.DOUBTFUL,
            judge(doubtfulBandMs = config.sustainedDriveProofMs),
        )
    }

    @Test
    fun should_notDoubt_when_theHighSpeedWasNotSustained() {
        // A band is a measurement; a brush past it is not. One second short must not convict, or
        // the ceiling becomes the peak it was written to avoid being.
        assertEquals(
            RoadPlausibility.PLAUSIBLE,
            judge(doubtfulBandMs = config.sustainedDriveProofMs - 1),
        )
    }

    // ── The terminal band needs BOTH halves ─────────────────────────────────────────────

    @Test
    fun should_onlyDoubt_when_railSpeedWasHeldButTheJourneyWasRoadScale() {
        // The Autobahn case, and the reason the AND exists: five minutes above 200 km/h with no
        // journey to match is a fast car on an empty unrestricted stretch. It degrades to a
        // question — it must never end the session.
        assertEquals(
            RoadPlausibility.DOUBTFUL,
            judge(
                doubtfulBandMs = config.sustainedDriveProofMs,
                impossibleBandMs = config.roadImpossibleSustainedMs,
                journeyRateMps = config.roadImpossibleJourneyRateMps - 1f,
            ),
        )
    }

    @Test
    fun should_onlyDoubt_when_theJourneyWasVastButNoRailSpeedWasHeld() {
        // The mirror: a long motorway haul covers ground at a straight-line rate above the
        // corroborator's bar without ever holding rail speed. Also a question, never a close.
        assertEquals(
            RoadPlausibility.DOUBTFUL,
            judge(
                doubtfulBandMs = config.sustainedDriveProofMs,
                impossibleBandMs = config.roadImpossibleSustainedMs - 1,
                journeyRateMps = config.roadImpossibleJourneyRateMps + 10f,
            ),
        )
    }

    @Test
    fun should_convict_when_bothRailSpeedAndAJourneyBeyondAnyRoadWereMeasured() {
        // The 2026-09-15 session: ~2 hours above 200 km/h, and 476 km of straight-line ground in
        // 270 minutes (≈29,4 m/s). Nothing on a road does both.
        assertEquals(
            RoadPlausibility.IMPOSSIBLE,
            judge(
                doubtfulBandMs = 2 * 60 * 60 * 1_000L,
                impossibleBandMs = 2 * 60 * 60 * 1_000L,
                journeyRateMps = 29.4f,
            ),
        )
    }

    @Test
    fun should_reportImpossible_ratherThanDoubtful_when_bothBandsHold() {
        // Ordering, asserted rather than assumed: a convicting session clears the doubt band by
        // construction, and reporting it as DOUBTFUL would turn a decided session into a prompt at
        // a railway terminus — the one outcome this ticket exists to prevent.
        val verdict = judge(
            doubtfulBandMs = Long.MAX_VALUE / 2,
            impossibleBandMs = config.roadImpossibleSustainedMs,
            journeyRateMps = config.roadImpossibleJourneyRateMps,
        )
        assertEquals(RoadPlausibility.IMPOSSIBLE, verdict)
    }

    @Test
    fun should_convict_when_bothHalvesSitExactlyOnTheirBars() {
        // Boundary: the bars are inclusive on both halves. Spelled out because a `>` slipped in
        // later would still pass every other test here.
        assertEquals(
            RoadPlausibility.IMPOSSIBLE,
            judge(
                impossibleBandMs = config.roadImpossibleSustainedMs,
                journeyRateMps = config.roadImpossibleJourneyRateMps,
            ),
        )
    }
}
