package io.apptolast.paparcar.detection.sensor

import android.content.Context
import androidx.core.content.edit
import io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.sensor.DetectionStepAnchors
import io.apptolast.paparcar.domain.sensor.StepCounterSource
import io.apptolast.paparcar.domain.sensor.StepsSinceSeal

/**
 * Android [DetectionStepAnchors] backed by `Sensor.TYPE_STEP_COUNTER` (via [StepCounterSource]) and
 * the SAME SharedPreferences slot the safety-net worker reads/writes — one storage contract, keyed
 * by geofence id. Sealing at confirm time makes the step budget available from the moment of
 * parking, which the 2-min-hop honest close needs (the worker's first tick can be 15 min out).
 * [DET-HONEST-CLOSE-001]
 *
 * [DET-STEP-BUDGET-ORIGIN-001] The seal also persists WHERE the body was when the counter was
 * read (`anchor_seal_pos_<id>` = "lat,lon"), so consumers can compare the step delta against a
 * displacement measured from the same origin. The position is written/removed atomically with the
 * steps baseline — a steps value without its position (legacy seal) reads back as
 * [StepsSinceSeal.sealPoint] = null and consumers refuse the walked-vs-rode verdict.
 */
class AndroidDetectionStepAnchors(
    private val stepCounterSource: StepCounterSource,
    private val context: Context,
) : DetectionStepAnchors {

    private fun prefs() =
        context.getSharedPreferences(ParkingSafetyNetWorker.PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(geofenceId: String) =
        ParkingSafetyNetWorker.ANCHOR_STEPS_KEY_PREFIX + geofenceId

    private fun posKey(geofenceId: String) =
        ParkingSafetyNetWorker.ANCHOR_SEAL_POS_KEY_PREFIX + geofenceId

    override suspend fun seal(geofenceId: String, sealPoint: GpsPoint?) {
        // Mute counter → no baseline to seal; the budget then reads null and the ladder stays
        // silent (asymmetric: better a late safety-net prompt than a wrong zone).
        val current = stepCounterSource.currentCumulativeSteps() ?: return
        prefs().edit {
            putLong(key(geofenceId), current)
            // Same edit as the steps so the pair can never diverge: either both from this seal,
            // or a null position that makes consumers refuse the verdict. [DET-STEP-BUDGET-ORIGIN-001]
            if (sealPoint != null) {
                putString(posKey(geofenceId), "${sealPoint.latitude},${sealPoint.longitude}")
            } else {
                remove(posKey(geofenceId))
            }
        }
    }

    override suspend fun stepsSinceSeal(geofenceId: String): StepsSinceSeal? {
        val baseline = prefs().getLong(key(geofenceId), -1L).takeIf { it >= 0L } ?: return null
        val current = stepCounterSource.currentCumulativeSteps() ?: return null
        // A reboot resets the hardware counter below the baseline → delta unknown, never a verdict.
        val delta = (current - baseline).takeIf { it >= 0L } ?: return null
        return StepsSinceSeal(steps = delta, sealPoint = readSealPoint(geofenceId))
    }

    private fun readSealPoint(geofenceId: String): GpsPoint? {
        val raw = prefs().getString(posKey(geofenceId), null) ?: return null
        val parts = raw.split(',')
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        return GpsPoint(latitude = lat, longitude = lon, accuracy = 0f, timestamp = 0L, speed = 0f)
    }
}
