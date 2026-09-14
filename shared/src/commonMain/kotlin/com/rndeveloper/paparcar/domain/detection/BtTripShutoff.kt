package com.rndeveloper.paparcar.domain.detection

/**
 * The null-trip verdict for BT-started trip tracking: the paired car's radio came on, sampling
 * started — but nobody is driving anywhere. Shuts the recorder off so a garage radio session never
 * bills a whole evening of GPS. [TRIP-THE-TRACKER-RIDES-THE-SENTINEL-001]
 *
 * Pure and clock-injected on purpose: the window runs on WALL TIME passed in by the caller, never
 * on fix timestamps — an underground garage that produces no fixes at all must still shut off
 * (no fixes is not an alibi, it is the absence of one).
 *
 * The two exculpating facts are deliberately independent:
 *  - a fix at driving speed — the safe direction to read the declared `speed` field: it lies LOW
 *    (it cannot prove rest), so a value ≥ [DRIVING_SPEED_MPS] claiming motion is trustworthy
 *    [project_det_the_speed_field_lies_low];
 *  - measured displacement beyond [NULL_TRIP_RADIUS_M] of the first fix — position evidence for a
 *    slow crawl whose speed field never clears the bar.
 * Either one keeps the recorder alive; a verdict needs the window elapsed AND neither.
 */
object BtTripShutoff {

    /** How long the recorder gets to observe a first sign of driving before shutting off. */
    const val NULL_TRIP_WINDOW_MS = 10 * 60_000L

    /** Wandering this far from the first fix is a trip, whatever the speed field says. Above the
     *  geofence-radius scale so a walk around the house with the car radio on stays a null trip. */
    const val NULL_TRIP_RADIUS_M = 150.0

    /** A declared speed at or above this (m/s ≈ 18 km/h) is driving — same "clearly driving" bound
     *  the adaptive sampler uses toward its cruise tier. */
    const val DRIVING_SPEED_MPS = 5f

    /**
     * True when the tracking session should shut off: the observation window elapsed with no
     * driving-speed fix and no displacement beyond the radius ([maxDisplacementMeters] null =
     * no fixes at all — still a null trip).
     */
    fun isNullTrip(
        startedAtMs: Long,
        nowMs: Long,
        sawDrivingSpeed: Boolean,
        maxDisplacementMeters: Double?,
    ): Boolean {
        if (nowMs - startedAtMs < NULL_TRIP_WINDOW_MS) return false
        if (sawDrivingSpeed) return false
        return (maxDisplacementMeters ?: 0.0) < NULL_TRIP_RADIUS_M
    }
}
