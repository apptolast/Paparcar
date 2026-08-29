package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-ASSERTION-OUTRANKS-INFERENCE-001] An inference never deposes an assertion — and the rule
 * has to read identically at all four lanes that can relocate a pin.
 */
class AssertedPinAuthorityTest {

    private val config = ParkingDetectionConfig()
    private val nowMs = 1_700_000_000_000L

    /** Calle Fragua, El Puerto — the field coordinates of 2026-08-24. */
    private val assertedPin = GpsPoint(
        latitude = 36.613605,
        longitude = -6.2089333,
        accuracy = 1.25f,
        timestamp = nowMs - 2 * 60_000L - 53_000L, // 2 min 53 s ago, as in the field
        speed = 0f,
    )

    /** 14 m away: where the second prompt wanted to move the pin. */
    private val candidate14mAway = GpsPoint(
        latitude = 36.6136417,
        longitude = -6.2087783,
        accuracy = 2.08f,
        timestamp = nowMs,
        speed = 0.97f,
    )

    private fun blocks(
        pinReliability: Float? = config.reliabilityUserConfirmed,
        pin: GpsPoint = assertedPin,
        candidate: GpsPoint = candidate14mAway,
        sessionSawDriving: Boolean = false,
        freshWindowMs: Long? = config.reparkPlausibilityWindowMs,
        radiusMeters: Float? = config.reparkPlausibilityRadiusMeters,
    ) = assertionBlocksRelocation(
        pinReliability = pinReliability,
        pinLocation = pin,
        candidate = candidate,
        nowMs = nowMs,
        sessionSawDriving = sessionSawDriving,
        userConfirmedReliability = config.reliabilityUserConfirmed,
        freshWindowMs = freshWindowMs,
        radiusMeters = radiusMeters,
    )

    @Test
    fun should_block_when_theUserAssertedThisPinMinutesAgoAndNothingMeasuredADrive() {
        // The field case: pin a9709e31 at 20:48:43, second prompt at 20:51:22, 14 m apart.
        assertTrue(blocks())
    }

    @Test
    fun should_notBlock_when_theSessionMeasuredSustainedDriving() {
        // A real re-park: the car drove, so measured movement — the one thing that is not an
        // inference — is allowed to overrule the user's earlier word.
        assertFalse(blocks(sessionSawDriving = true))
    }

    @Test
    fun should_notBlock_when_thePinWasNotAssertedByTheUser() {
        // 0.9 = steps+egress, an automatic verdict. Config invariants keep every automatic path
        // strictly below reliabilityUserConfirmed, so only the user's word reaches the bar.
        assertFalse(blocks(pinReliability = config.reliabilityVehicleExit))
    }

    @Test
    fun should_notBlock_when_theReliabilityIsUnknown() {
        // Legacy rows carry null. Absence of a claim is not a claim.
        assertFalse(blocks(pinReliability = null))
    }

    @Test
    fun should_notBlock_when_theAssertionHasAgedOutOfTheWindow() {
        val old = assertedPin.copy(timestamp = nowMs - config.reparkPlausibilityWindowMs - 1)
        assertFalse(blocks(pin = old))
    }

    @Test
    fun should_notBlock_when_theCandidateIsBeyondWalkingReach() {
        // 2 km north: not a relocation of this pin, a genuinely different park.
        val faraway = candidate14mAway.copy(latitude = assertedPin.latitude + 0.018)
        assertFalse(blocks(candidate = faraway))
    }

    @Test
    fun should_blockRegardlessOfAgeAndDistance_when_theBoundsAreUnset() {
        // The honest close's reading: a session aborting without measured driving has produced
        // nothing capable of moving a car, so the assertion holds at any age and any distance.
        // This is the behaviour that lane had before the predicate was extracted, and the reason
        // the bounds are nullable rather than always-on.
        val ancient = assertedPin.copy(timestamp = nowMs - 30L * 24 * 3_600_000L)
        val faraway = candidate14mAway.copy(latitude = assertedPin.latitude + 0.05)
        assertTrue(
            blocks(pin = ancient, candidate = faraway, freshWindowMs = null, radiusMeters = null),
        )
    }

    @Test
    fun should_block_when_theClockSkewsTheAssertionIntoTheFuture() {
        // A negative age must not read as "expired": keeping the user's pin is the safe side.
        val future = assertedPin.copy(timestamp = nowMs + 60_000L)
        assertEquals(true, blocks(pin = future))
    }
}
