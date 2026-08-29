package com.rndeveloper.paparcar.domain.sensor

import com.rndeveloper.paparcar.domain.model.GpsPoint

/**
 * Per-parking hardware step-counter baseline: seals the cumulative step count at the moment a park
 * is confirmed, and later reports how many steps have been walked since. [DET-HONEST-CLOSE-001]
 *
 * The honest-close ladder and the parked-session safety net share ONE question — "did the car
 * drive away from its last pin, or did the person walk?" — answered by comparing the displacement
 * to the steps walked since the pin was sealed. The safety net used to seal this baseline only on
 * its first wake-up INSIDE the fence (up to 15 min after parking); a 2-minute hop between two
 * parks beat that tick, so the baseline was absent exactly when the honest close needs it (field
 * 2026-07-14, Camelias). Sealing at CONFIRM time closes that gap for both consumers: the budget is
 * available from the moment of parking.
 *
 * Backed by the same store the safety-net worker reads (keyed by geofence id), so a seal here and
 * a re-seal at a later cure are the same slot. Platform-specific (Android `Sensor.TYPE_STEP_COUNTER`
 * + persistent prefs); absent platforms bind nothing and the honest close stays silent.
 */
interface DetectionStepAnchors {

    /** Record the current cumulative step count as the zero point for [geofenceId], together with
     *  WHERE the body was when the counter was read ([sealPoint], null when the caller has no
     *  honest position for it). No-op when the hardware counter is unavailable (the budget then
     *  reads null and the ladder stays silent).
     *
     *  [DET-STEP-BUDGET-ORIGIN-001] The seal point is what makes the budget comparable: a step
     *  delta only answers "walked or rode?" against a displacement measured FROM THE SAME POINT
     *  the counter was zeroed at. Sealing the steps at confirm time while measuring the distance
     *  from the PIN mixed two origins — the egress walk (pin → seal point) was excluded from the
     *  count but included in the distance, so a plain walk home read as a ride and the honest
     *  close planted a pin inside the user's home (field 2026-07-22 01:47, Redmi/Glorieta). */
    suspend fun seal(geofenceId: String, sealPoint: GpsPoint?)

    /** Steps walked since [geofenceId] was sealed plus the seal's own position, or null when the
     *  counter is mute, no baseline was sealed, or a reboot reset the counter below the baseline
     *  (a negative delta is never a verdict). [StepsSinceSeal.sealPoint] is null for baselines
     *  sealed without a position (legacy seals, callers with no honest fix). */
    suspend fun stepsSinceSeal(geofenceId: String): StepsSinceSeal?
}

/** A step-budget answer: [steps] walked since the seal, measured from [sealPoint] (null when the
 *  seal recorded no position — consumers must then refuse a walked-vs-rode verdict rather than
 *  compare against a displacement with a different origin). [DET-STEP-BUDGET-ORIGIN-001]
 *  [sealedAtMs] is WHEN the baseline was sealed (epoch ms; null for legacy seals without a
 *  timestamp) — the budget expires with age, so consumers must refuse a verdict whose delta
 *  spans hours of sleep / process deaths / counter batching. [DET-TRIP-WITNESS-001] */
data class StepsSinceSeal(
    val steps: Long,
    val sealPoint: GpsPoint?,
    val sealedAtMs: Long? = null,
)
