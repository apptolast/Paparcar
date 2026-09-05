@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.detection.sensor

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.sensor.DetectionStepAnchors
import com.rndeveloper.paparcar.domain.sensor.StepsSinceSeal
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

/**
 * [IOS-F0-06 → IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] iOS step-seal per the portable contract
 * (`DetectionStepAnchors`, IOS-F0-05): the seal persists only WHERE and WHEN, and the delta is
 * derived at READ time with a CMPedometer date-range query over `[sealedAtMs, now]`.
 *
 * This is strictly better than Android's cumulative-counter baseline in two ways the contract
 * predicted: the OS keeps recording steps with the app DEAD (a wake hours later still gets the
 * true budget), and there is no counter to freeze or reboot below the baseline — the
 * frozen-counter pathology (DET-FROZEN-COUNTER) has no iOS analogue. When the pedometer is
 * unavailable or the query errors, the answer is null — "counter mute → the honest close stays
 * silent", a safe degradation, never a wrong verdict.
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
        val sealedAtMs = (defaults.objectForKey(KEY_NAMESPACE + SEAL_AT_PREFIX + geofenceId) as? String)
            ?.toLongOrNull() ?: return null
        val nowMs = nowMillis()
        if (nowMs < sealedAtMs) return null // a clock jump backwards is never a verdict

        val steps = queryPedometerStepsBetween(sealedAtMs, nowMs) ?: return null
        return StepsSinceSeal(
            steps = steps,
            sealPoint = readSealPoint(geofenceId, sealedAtMs),
            sealedAtMs = sealedAtMs,
        )
    }

    private fun readSealPoint(geofenceId: String, sealedAtMs: Long): GpsPoint? {
        val raw = defaults.objectForKey(KEY_NAMESPACE + SEAL_POS_PREFIX + geofenceId) as? String
            ?: return null
        val parts = raw.split(",")
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        // The seal stores no accuracy/speed — the point exists to anchor the displacement origin
        // [DET-STEP-BUDGET-ORIGIN-001], so neutral values are honest here.
        return GpsPoint(lat, lon, accuracy = 0f, timestamp = sealedAtMs, speed = 0f)
    }

    private fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

    private companion object {
        const val KEY_NAMESPACE = "parking_safety_net."
        const val SEAL_AT_PREFIX = "anchor_seal_at_"
        const val SEAL_POS_PREFIX = "anchor_seal_pos_"
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
