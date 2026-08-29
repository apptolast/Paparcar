package com.rndeveloper.paparcar.domain.model

/**
 * How likely a published community spot is to still be free, derived from ONE input: its AGE.
 * [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001]
 *
 * This is the single freshness ramp in the app — green / amber / red on the marker, on the list
 * puck and on the age chip. Before this existed there were two ramps with different inputs, and
 * they contradicted each other on the same row: the marker read `confidence` (which decayed
 * proportionally to the TTL) while the chip read absolute minutes REMAINING, so a 2 h spot went red
 * on the map at 54 minutes while its own chip was still green at 110.
 *
 * Two things that are NOT freshness, and never enter this ramp again:
 *  - **Provenance** (auto-detected vs. a human report) is told by a glyph — the person badge — per
 *    [UI-COLOR-DOCTRINE-001 F5].
 *  - **Doubt about the departure** ([SpotStatus.PROVISIONAL]) is told by its badge.
 *
 * And a third that this ramp deliberately does not encode: [SpotTtlPolicy] expiry. `expiresAt` is
 * when the sweep DELETES the document — a garbage-collection deadline, not a statement about the
 * parking space. Nothing in the app measures whether a spot has been taken, so a countdown to
 * expiry was a promise we could not keep.
 */
enum class SpotFreshness {
    /** Freed within the last [SpotFreshnessPolicy.FRESH_MAX_AGE_MS] — green. */
    FRESH,

    /** Old enough to be doubtful, young enough to be worth a detour — amber. */
    RECENT,

    /** Probably gone. Still shown, because an honest stale spot beats an empty map — red. */
    STALE,
}

/**
 * The age thresholds behind [SpotFreshness].
 *
 * They are absolute minutes rather than a fraction of [SpotTtlPolicy.AUTO_SPOT_TTL_MS] on purpose.
 * Every spot the user can see now carries the same lifetime, so "proportional" and "absolute"
 * collapse into the same function — and absolute is the one that does not misbehave on the short
 * [SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS] window, where a proportional ramp would paint a possible
 * phantom green for its first nine minutes.
 */
object SpotFreshnessPolicy {
    /** Up to 10 minutes old: the space was almost certainly still empty a moment ago. */
    const val FRESH_MAX_AGE_MS: Long = 10 * 60 * 1_000L

    /** Up to 30 minutes old: worth offering, not worth promising. */
    const val RECENT_MAX_AGE_MS: Long = 30 * 60 * 1_000L

    /** The freshness of a spot that is [ageMs] old. A negative age (clock skew between the
     *  reporter's device and this one) is treated as brand new rather than as stale. */
    fun ofAge(ageMs: Long): SpotFreshness = when {
        ageMs <= FRESH_MAX_AGE_MS -> SpotFreshness.FRESH
        ageMs <= RECENT_MAX_AGE_MS -> SpotFreshness.RECENT
        else -> SpotFreshness.STALE
    }

    /** The freshness of a spot published at [reportedAtMs], evaluated at [nowMs]. A spot with no
     *  timestamp (`0`) cannot be aged, so it is shown at face value rather than condemned. */
    fun of(reportedAtMs: Long, nowMs: Long): SpotFreshness =
        if (reportedAtMs <= 0L) SpotFreshness.FRESH else ofAge(nowMs - reportedAtMs)
}
