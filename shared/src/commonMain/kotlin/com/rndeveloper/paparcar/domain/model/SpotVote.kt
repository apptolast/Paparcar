package com.rndeveloper.paparcar.domain.model

/**
 * What a community vote DOES to a spot. [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001]
 *
 * Before this existed, "Still there" and "It's gone" incremented two counters that fed
 * [Spot.confidence] — and once the freshness ramp stopped reading `confidence`
 * ([SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001]), those counters reached no pixel at all. A driver
 * could stand at an occupied space, say so, and the next driver would still be sent there.
 *
 * The consequence is deliberately NOT a tint. "This spot is occupied" is not "this spot is less
 * fresh"; it is a different statement, and the app already owns a way to say it —
 * [SpotStatus.RETRACTED], which keeps the document alive long enough to explain itself instead of
 * vanishing a marker in the face of someone already walking towards it.
 */
enum class SpotVoteOutcome {
    /** The spot is withdrawn for everyone. */
    RETRACT,

    /** The spot goes back to being newly freed: an eyewitness just observed exactly that. */
    REFRESH,

    /** The voter is not close enough for their word to count. Nothing is written. */
    IGNORED_TOO_FAR,
}

/**
 * The rule that turns a vote into an outcome.
 *
 * **One rejection is enough — because only someone standing there can cast it.** The three
 * decisions of this ticket are really one: a person at the space is the strongest evidence that
 * exists about it, stronger than any tally, and the proximity gate is what makes trusting a single
 * voice safe. It also matches the project's asymmetry doctrine — withdrawing a spot that was still
 * free costs a missed opportunity, while leaving a phantom alive costs a wasted drive, and the
 * wasted drive is what destroys trust in the app.
 *
 * `MIN_VOTES_FOR_SIGNAL = 3` (in the DTO mapper) is deliberately NOT reused here: three rejections
 * on a spot that lives minutes is a threshold that is almost never reached — the spot expires
 * first. That constant still governs `communityConfidence()`, which answers a different question.
 */
object SpotVotePolicy {
    /**
     * How close a voter must be for their vote to count, in metres.
     *
     * Tighter than detection's `NEAR_CAR_MAX_METERS = 100.0`, and intentionally a constant of its
     * own rather than a shared one: a vote here decides for EVERYONE, and urban GPS lands within
     * 10-20 m. Sharing detection's number would tie two unrelated domains together by accident.
     */
    const val MAX_VOTE_DISTANCE_METERS = 50.0

    /** Whether a voter [distanceMeters] away may vote at all. An unknown distance (no location
     *  fix) is not close enough — the app does not guess about something this destructive. */
    fun canVote(distanceMeters: Double?): Boolean =
        distanceMeters != null && distanceMeters <= MAX_VOTE_DISTANCE_METERS

    /** The outcome of a vote cast [distanceMeters] from the spot. [accepted] is "Still there". */
    fun outcomeOf(accepted: Boolean, distanceMeters: Double?): SpotVoteOutcome = when {
        !canVote(distanceMeters) -> SpotVoteOutcome.IGNORED_TOO_FAR
        accepted -> SpotVoteOutcome.REFRESH
        else -> SpotVoteOutcome.RETRACT
    }
}
