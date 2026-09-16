package com.rndeveloper.paparcar.domain.model

/**
 * Whether the person in front of the map may publish the spot under the pin.
 * [SPOT-A-REPORT-YOU-CANNOT-SEE-IS-NOT-A-REPORT-001]
 *
 * Three values because there are **two different sentences to say**, and saying one for the other is
 * a lie: "we don't know where you are" is not "you are far from there". A single boolean would have
 * forced the peek to guess which one it was showing.
 */
enum class SpotReportGate {
    /** The pin is close enough to the measured fix. */
    ALLOWED,

    /** No location fix, or no pin captured yet — nothing to measure against. */
    NO_FIX,

    /** There IS a fix, and the pin is beyond [SpotReportPolicy.MAX_REPORT_DISTANCE_METERS] of it. */
    TOO_FAR,
}

/**
 * How far from you the spot you are reporting may be.
 *
 * The rule already existed for the other half of the same claim — [SpotVotePolicy] gates "still
 * there / it's gone" at 50 m because *only someone standing there* may decide for everyone. Creating
 * a spot is the same statement and a more expensive one (it PLANTS the pin instead of retracting
 * it), and it had no rule at all: the report pin is the camera centre, so panning the map published
 * a spot in another city under your UID.
 */
object SpotReportPolicy {
    /**
     * How close the reporter must be to the spot, in metres.
     *
     * Deliberately NOT [SpotVotePolicy.MAX_VOTE_DISTANCE_METERS]: voting certifies what you have in
     * front of you, while reporting also covers the space you just saw while driving past and the
     * one you are walking away from. 50 m would kill the normal case; 500 m would only stop "another
     * city" and let through spots nobody actually saw. (User, 16-09-2026.)
     */
    const val MAX_REPORT_DISTANCE_METERS = 200.0

    /**
     * The gate for a pin [distanceMeters] away from the reporter. An unknown distance (no fix, or no
     * pin yet) is [SpotReportGate.NO_FIX] and never [SpotReportGate.ALLOWED] — same asymmetry as the
     * vote: the app does not guess about something the whole community then drives towards.
     */
    fun gateFor(distanceMeters: Double?): SpotReportGate = when {
        distanceMeters == null -> SpotReportGate.NO_FIX
        distanceMeters > MAX_REPORT_DISTANCE_METERS -> SpotReportGate.TOO_FAR
        else -> SpotReportGate.ALLOWED
    }
}
