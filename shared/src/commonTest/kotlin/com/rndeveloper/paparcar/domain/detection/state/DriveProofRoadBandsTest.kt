package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.detection.physics.DriveProofBounds
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-A-TRAIN-IS-NOT-A-CAR-001] The two ceiling bands and the journey rate, measured over a stream
 * rather than asserted as values.
 *
 * The predicate's own test proves what the verdict does with these numbers; this proves the numbers
 * are what the session actually saw. Both are needed: the 2026-09-15 failure was not a verdict
 * reading its inputs wrongly, it was a verdict with no such input at all.
 */
class DriveProofRoadBandsTest {

    private val config = ParkingDetectionConfig()

    private val bounds = DriveProofBounds(
        windowMinMs = config.driveProofWindowMinMs,
        windowMaxMs = config.driveProofWindowMaxMs,
        hopMarginMeters = config.credibleDriveHopMarginMeters,
        minDistanceMeters = config.minimumTripDistanceMeters,
        maxRateMps = config.sustainedDepartureMaxRateMps,
        progressFraction = 0.25f,
        retentionSlackMs = 30_000L,
        maxRetainedFixes = 48,
    )

    /** Due north from the origin, so metres and latitude are interchangeable. */
    private fun fixAt(metersNorth: Double, tMs: Long, speed: Float, accuracy: Float = 20f) =
        GpsPoint(
            latitude = metersNorth / 111_320.0,
            longitude = 0.0,
            accuracy = accuracy,
            timestamp = tMs,
            speed = speed,
        )

    /**
     * Feeds a constant-speed leg and returns the resulting proof.
     *
     * @param armedAtMs how long the session had already been running when the leg starts — what
     *   `elapsedSinceArmMs` is measured from, and therefore what gates the journey rate.
     */
    private fun ride(
        speedMps: Float,
        durationMs: Long,
        stepMs: Long = 10_000L,
        accuracy: Float = 20f,
        startingAtMs: Long = 0L,
        startingMeters: Double = 0.0,
        from: DriveProof = DriveProof(),
    ): DriveProof {
        var proof = from
        var t = startingAtMs
        var metres = startingMeters
        while (t <= startingAtMs + durationMs) {
            val fix = fixAt(metres, t, speedMps, accuracy)
            proof = proof.onFix(
                fix = fix,
                nowMs = t,
                credibleSpeedFix = accuracy <= config.minGpsAccuracyForDriving,
                departureAnchor = null,
                departureFenceRadiusMeters = 0f,
                elapsedSinceArmMs = t,
                bounds = bounds,
                config = config,
                distanceFromOriginMeters = metres,
            )
            t += stepMs
            metres += speedMps * (stepMs / 1_000.0)
        }
        return proof
    }

    @Test
    fun should_creditNeitherBand_when_theSessionDroveTheFastestSpeedEverRecordedInTheField() {
        // 36,49 m/s = 131,4 km/h, the peak of `Trace_MotorwayRedmi001` and the fastest real drive
        // on file. Ten minutes of it must leave both ceilings untouched, or the ticket is a
        // regression on every motorway trip.
        val proof = ride(speedMps = 36.49f, durationMs = 10 * 60 * 1_000L)

        assertEquals(0L, proof.roadDoubtfulBandMs, "the field's fastest drive must not even be doubted")
        assertEquals(0L, proof.roadImpossibleBandMs)
    }

    @Test
    fun should_creditOnlyTheDoubtBand_when_theSpeedSitsBetweenTheTwoCeilings() {
        // 50 m/s = 180 km/h: above every legal limit we ship into, below the convicting band.
        val proof = ride(speedMps = 50f, durationMs = 10 * 60 * 1_000L)

        assertTrue(
            proof.roadDoubtfulBandMs >= config.sustainedDriveProofMs,
            "180 km/h held for ten minutes must fill the doubt band, was ${proof.roadDoubtfulBandMs}ms",
        )
        assertEquals(0L, proof.roadImpossibleBandMs, "…and must never reach the convicting one")
    }

    @Test
    fun should_creditBothBands_when_theSpeedIsRailScale() {
        // 70 m/s = 252 km/h, the 2026-09-15 measurement.
        val proof = ride(speedMps = 70f, durationMs = 10 * 60 * 1_000L)

        assertTrue(proof.roadImpossibleBandMs >= config.roadImpossibleSustainedMs)
        assertTrue(proof.roadDoubtfulBandMs >= proof.roadImpossibleBandMs, "the bands nest")
    }

    @Test
    fun should_creditNothing_when_theFixesAreNotCredible() {
        // The whole ceiling rests on believing a speed. A 200 m-accuracy fix claiming 252 km/h is
        // the same rumour the floors already refuse, and a ceiling that fired on one would invent
        // trains out of bad GPS.
        val proof = ride(speedMps = 70f, durationMs = 10 * 60 * 1_000L, accuracy = 200f)

        assertEquals(0L, proof.roadDoubtfulBandMs)
        assertEquals(0L, proof.roadImpossibleBandMs)
        assertEquals(0f, proof.journeyGroundRateMps)
    }

    // ── The journey corroborator ───────────────────────────────────────────────

    @Test
    fun should_measureNoJourneyRate_before_theBaselineHasElapsed() {
        // Fifty-nine minutes of rail speed still answers zero: below the baseline any rate is
        // cheap, and cheap is exactly what the corroborator must not be.
        val proof = ride(speedMps = 70f, durationMs = 59 * 60 * 1_000L)

        assertEquals(
            0f,
            proof.journeyGroundRateMps,
            "a journey shorter than the baseline must not produce a rate at all",
        )
    }

    @Test
    fun should_measureTheJourneyRate_once_theBaselineIsPassed() {
        val proof = ride(speedMps = 70f, durationMs = 70 * 60 * 1_000L)

        assertTrue(
            proof.journeyGroundRateMps >= config.roadImpossibleJourneyRateMps,
            "70 m/s held past the baseline must clear the corroborator, was ${proof.journeyGroundRateMps}",
        )
    }

    @Test
    fun should_notLetAFastLegCarryASlowJourney() {
        // The Autobahn shape in stream form, and the reason the rate is measured from the ORIGIN
        // rather than over a window: ten minutes at 252 km/h inside a journey that has otherwise
        // crawled for an hour leaves the straight-line rate far below the bar. The band will read
        // as held; the journey will refuse to corroborate it.
        val crawl = ride(speedMps = 6f, durationMs = 60 * 60 * 1_000L)
        val proof = ride(
            speedMps = 70f,
            durationMs = 10 * 60 * 1_000L,
            startingAtMs = 60 * 60 * 1_000L + 10_000L,
            startingMeters = 6.0 * 60 * 60,
            from = crawl,
        )

        assertTrue(proof.roadImpossibleBandMs >= config.roadImpossibleSustainedMs, "the leg IS rail speed")
        assertTrue(
            proof.journeyGroundRateMps < config.roadImpossibleJourneyRateMps,
            "…but the journey is road scale, was ${proof.journeyGroundRateMps}",
        )
    }
}
