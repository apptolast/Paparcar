package io.apptolast.paparcar.detection.sensor

import android.content.Context
import androidx.core.content.edit
import io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker
import io.apptolast.paparcar.domain.sensor.DetectionStepAnchors
import io.apptolast.paparcar.domain.sensor.StepCounterSource

/**
 * Android [DetectionStepAnchors] backed by `Sensor.TYPE_STEP_COUNTER` (via [StepCounterSource]) and
 * the SAME SharedPreferences slot the safety-net worker reads/writes — one storage contract, keyed
 * by geofence id. Sealing at confirm time makes the step budget available from the moment of
 * parking, which the 2-min-hop honest close needs (the worker's first tick can be 15 min out).
 * [DET-HONEST-CLOSE-001]
 */
class AndroidDetectionStepAnchors(
    private val stepCounterSource: StepCounterSource,
    private val context: Context,
) : DetectionStepAnchors {

    private fun prefs() =
        context.getSharedPreferences(ParkingSafetyNetWorker.PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(geofenceId: String) =
        ParkingSafetyNetWorker.ANCHOR_STEPS_KEY_PREFIX + geofenceId

    override suspend fun seal(geofenceId: String) {
        // Mute counter → no baseline to seal; the budget then reads null and the ladder stays
        // silent (asymmetric: better a late safety-net prompt than a wrong zone).
        val current = stepCounterSource.currentCumulativeSteps() ?: return
        prefs().edit { putLong(key(geofenceId), current) }
    }

    override suspend fun stepsSinceSeal(geofenceId: String): Long? {
        val baseline = prefs().getLong(key(geofenceId), -1L).takeIf { it >= 0L } ?: return null
        val current = stepCounterSource.currentCumulativeSteps() ?: return null
        // A reboot resets the hardware counter below the baseline → delta unknown, never a verdict.
        return (current - baseline).takeIf { it >= 0L }
    }
}
