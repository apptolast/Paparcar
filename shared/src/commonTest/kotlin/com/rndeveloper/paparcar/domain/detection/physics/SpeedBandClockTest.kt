package com.rndeveloper.paparcar.domain.detection.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [DET-MOTOR-PROOF-001][DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] The sustained-band accumulator both
 *  the drive proof and the motor proof run on. */
class SpeedBandClockTest {

    private val windowMax = 60_000L

    @Test
    fun should_creditNothing_when_theFixIsOutOfBand() {
        assertEquals(
            5_000L,
            creditSpeedBand(5_000L, lastInBandFixMs = 1_000L, fixTimestampMs = 6_000L, fixInBand = false, windowMaxMs = windowMax),
        )
    }

    @Test
    fun should_creditNothing_when_theBandRunHasNoPreviousFix() {
        // A lone spike has no in-band peer, so it buys nothing — this is what keeps a Doppler
        // mirage out of the statistic a peak would have swallowed whole.
        assertEquals(
            0L,
            creditSpeedBand(0L, lastInBandFixMs = 0L, fixTimestampMs = 6_000L, fixInBand = true, windowMaxMs = windowMax),
        )
    }

    @Test
    fun should_creditTheGap_when_twoSuccessiveFixesAreInBand() {
        assertEquals(
            3_000L,
            creditSpeedBand(0L, lastInBandFixMs = 1_000L, fixTimestampMs = 4_000L, fixInBand = true, windowMaxMs = windowMax),
        )
    }

    @Test
    fun should_creditNothing_when_theGapOutgrowsTheTrustedWindow() {
        // Two isolated spikes minutes apart are not a run: a wider gap proves nothing.
        assertEquals(
            0L,
            creditSpeedBand(0L, lastInBandFixMs = 1_000L, fixTimestampMs = 1_000L + windowMax + 1, fixInBand = true, windowMaxMs = windowMax),
        )
    }

    @Test
    fun should_bridgeAnAccuracyHole_when_theGapStillFitsTheWindow() {
        // A real drive's band run is punched through by urban accuracy holes (field Enamorados)
        // and a skeletal stream's whole drive can be one 36-s hop (field Calle Gavia).
        assertEquals(
            36_000L,
            creditSpeedBand(0L, lastInBandFixMs = 1_000L, fixTimestampMs = 37_000L, fixInBand = true, windowMaxMs = windowMax),
        )
    }

    // ── sustainedDriveWitnessed ───────────────────────────────────────────────

    @Test
    fun should_witnessTheDrive_when_theProvenBandReachesTheBar() {
        assertTrue(sustainedDriveWitnessed(provenBandMs = 30_000L, proofMs = 30_000L))
        assertTrue(sustainedDriveWitnessed(provenBandMs = 30_001L, proofMs = 30_000L))
    }

    @Test
    fun should_notWitnessTheDrive_when_theBandFallsShort() {
        assertFalse(sustainedDriveWitnessed(provenBandMs = 29_999L, proofMs = 30_000L))
    }

    @Test
    fun should_notWitnessTheDrive_when_theTrackNeverCorroboratedIt() {
        // An uncorroborated run reaches this function as ZERO — the drive proof's promotion rule
        // is upstream (provenDrivingBandMs), and it is what stopped a lone 5,33 m/s sample from
        // reading as a trip on 2026-08-24. [DET-ASSERTION-OUTRANKS-INFERENCE-001]
        assertFalse(sustainedDriveWitnessed(provenBandMs = 0L, proofMs = 30_000L))
    }
}
