package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.GpsPoint

/**
 * Persistent breadcrumb trail of every fresh one-shot fix the detection stack samples.
 * [DET-BREADCRUMBS-001]
 *
 * The level-triggered net already wakes DURING untracked trips (significant-motion fires every
 * few seconds while moving — field 2026-07-07 22:41) and samples a fresh fix per wake-up… which
 * used to be discarded after deciding. Persisting them is a free trip trail: zero new OS
 * machinery, zero extra battery, survives process kills (disk). The reconcile evaluator reads it
 * to (a) place a backfilled parking at the point where the CAR stopped instead of where the
 * phone happens to be at wake-up (field 2026-07-08 04:41: session saved at the user's home with
 * the car 200 m away), and (b) prove a ride happened when the step counter is mute.
 *
 * NOT a continuous tracker: only the one-shot fixes the net samples anyway. Live coordinator
 * trips keep their own high-rate stream and `bestStopLocation`.
 */
interface TripTrail {

    /** Appends a fresh (freshness-gated by the caller) fix. Implementations bound the trail by
     *  size and age and must tolerate concurrent appends (parallel checks are the field norm). */
    fun append(point: GpsPoint)

    /** The current trail, oldest first. Stale points (beyond the retention window) are absent. */
    fun points(): List<GpsPoint>
}
