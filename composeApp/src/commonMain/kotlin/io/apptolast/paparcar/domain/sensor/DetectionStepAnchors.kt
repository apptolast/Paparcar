package io.apptolast.paparcar.domain.sensor

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

    /** Record the current cumulative step count as the zero point for [geofenceId]. No-op when the
     *  hardware counter is unavailable (the budget then reads null and the ladder stays silent). */
    suspend fun seal(geofenceId: String)

    /** Steps walked since [geofenceId] was sealed (current cumulative − the sealed baseline), or
     *  null when the counter is mute, no baseline was sealed, or a reboot reset the counter below
     *  the baseline (a negative delta is never a verdict). */
    suspend fun stepsSinceSeal(geofenceId: String): Long?
}
