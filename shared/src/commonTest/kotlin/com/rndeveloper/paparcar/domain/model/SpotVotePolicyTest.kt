package com.rndeveloper.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] The rule that turns a vote into a consequence.
 * One rejection withdraws a spot, so the proximity gate is the whole safety argument.
 */
class SpotVotePolicyTest {

    @Test
    fun should_allowVote_when_standingAtTheSpot() {
        assertTrue(SpotVotePolicy.canVote(0.0))
    }

    @Test
    fun should_allowVote_when_exactlyAtTheDistanceLimit() {
        assertTrue(SpotVotePolicy.canVote(SpotVotePolicy.MAX_VOTE_DISTANCE_METERS))
    }

    @Test
    fun should_refuseVote_when_justPastTheDistanceLimit() {
        assertFalse(SpotVotePolicy.canVote(SpotVotePolicy.MAX_VOTE_DISTANCE_METERS + 0.1))
    }

    @Test
    fun should_refuseVote_when_thereIsNoLocationFix() {
        // Without a fix we cannot know the voter is a witness, and a single vote withdraws the
        // spot for everyone. The app does not guess about something this destructive.
        assertFalse(SpotVotePolicy.canVote(null))
    }

    @Test
    fun should_retractSpot_when_witnessSaysItIsGone() {
        assertEquals(
            SpotVoteOutcome.RETRACT,
            SpotVotePolicy.outcomeOf(accepted = false, distanceMeters = 10.0),
        )
    }

    @Test
    fun should_refreshSpot_when_witnessSaysItIsStillThere() {
        assertEquals(
            SpotVoteOutcome.REFRESH,
            SpotVotePolicy.outcomeOf(accepted = true, distanceMeters = 10.0),
        )
    }

    @Test
    fun should_ignoreVote_when_castFromTooFarAway() {
        // Both directions are ignored, not just the destructive one: a remote "still there" would
        // rejuvenate a spot nobody has seen.
        assertEquals(
            SpotVoteOutcome.IGNORED_TOO_FAR,
            SpotVotePolicy.outcomeOf(accepted = false, distanceMeters = 900.0),
        )
        assertEquals(
            SpotVoteOutcome.IGNORED_TOO_FAR,
            SpotVotePolicy.outcomeOf(accepted = true, distanceMeters = 900.0),
        )
    }

    @Test
    fun should_ignoreVote_when_thereIsNoLocationFix() {
        assertEquals(
            SpotVoteOutcome.IGNORED_TOO_FAR,
            SpotVotePolicy.outcomeOf(accepted = false, distanceMeters = null),
        )
    }

    @Test
    fun should_stayTighterThanDetectionsNearCarRadius() {
        // Detection's NEAR_CAR_MAX_METERS is 100.0. A vote decides for everyone, so its gate is
        // deliberately stricter — and a separate constant, so neither can drift into the other.
        assertTrue(SpotVotePolicy.MAX_VOTE_DISTANCE_METERS < 100.0)
    }
}
