package io.apptolast.paparcar.detection.sensor

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.sensor.DetectionStepAnchors
import io.apptolast.paparcar.domain.sensor.StepsSinceSeal
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

/**
 * [IOS-F0-06] iOS step-seal SKELETON per the portable contract (`DetectionStepAnchors`,
 * IOS-F0-05): the seal persists only WHERE and WHEN — no counter baseline, because iOS has no
 * cumulative register. [stepsSinceSeal] must derive the delta with a CMPedometer date-range
 * query over `[sealedAtMs, now]` (port F2); until then it answers null, which the contract
 * defines as "counter mute → the honest close stays silent" — a safe degradation, never a
 * wrong verdict.
 */
class IosDetectionStepAnchors(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : DetectionStepAnchors {

    override suspend fun seal(geofenceId: String, sealPoint: GpsPoint?) {
        defaults.setObject(nowMillis().toString(), forKey = KEY_NAMESPACE + SEAL_AT_PREFIX + geofenceId)
        if (sealPoint != null) {
            defaults.setObject(
                "${sealPoint.latitude},${sealPoint.longitude}",
                forKey = KEY_NAMESPACE + SEAL_POS_PREFIX + geofenceId,
            )
        } else {
            defaults.removeObjectForKey(KEY_NAMESPACE + SEAL_POS_PREFIX + geofenceId)
        }
    }

    override suspend fun stepsSinceSeal(geofenceId: String): StepsSinceSeal? {
        // TODO(F2): CMPedometer.queryPedometerData over [sealedAtMs, now] — the seal above
        // already persists everything that query needs.
        return null
    }

    private fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()

    private companion object {
        const val KEY_NAMESPACE = "parking_safety_net."
        const val SEAL_AT_PREFIX = "anchor_seal_at_"
        const val SEAL_POS_PREFIX = "anchor_seal_pos_"
    }
}
