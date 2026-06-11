@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreMotion.CMPedometer
import platform.Foundation.NSDate

/**
 * iOS implementation backed by [CMPedometer].
 *
 * CMPedometer surfaces cumulative `numberOfSteps` snapshots (typically every 1-2 s while the
 * device is moving). To mirror Android's per-step `Sensor.TYPE_STEP_DETECTOR` semantics we
 * track the previous count and emit one `Unit` per step delta — the coordinator increments
 * `stepCount` once per emit, so 1 emit == 1 step accounting-wise.
 *
 * Permission (`NSMotionUsageDescription`) is requested by
 * [io.apptolast.paparcar.ios.permissions.IosPermissionRequester.requestStep1].
 * If the device cannot count steps, [steps] returns an empty flow and the coordinator's
 * timeout-based fallback owns confirmation.
 */
class IosStepDetectorSource : StepDetectorSource {

    override fun steps(): Flow<Unit> = callbackFlow {
        if (!CMPedometer.isStepCountingAvailable()) {
            PaparcarLogger.w(TAG, "Step counting unavailable on this device")
            close()
            return@callbackFlow
        }

        val pedometer = CMPedometer()
        var previousCount = 0L

        pedometer.startPedometerUpdatesFromDate(NSDate()) { data, error ->
            if (error != null) {
                PaparcarLogger.w(TAG, "CMPedometer error: ${error.localizedDescription}")
                return@startPedometerUpdatesFromDate
            }
            val current = data?.numberOfSteps?.longValue ?: return@startPedometerUpdatesFromDate
            val delta = current - previousCount
            previousCount = current
            repeat(delta.coerceAtMost(MAX_DELTA_PER_UPDATE).toInt()) {
                trySend(Unit)
            }
        }

        awaitClose { pedometer.stopPedometerUpdates() }
    }

    private companion object {
        const val TAG = "IosStepDetector"
        // Defensive ceiling: CMPedometer can backfill historical steps if the app was paused.
        // Cap the per-update emit count so a sudden 5000-step backfill doesn't flood the coordinator.
        const val MAX_DELTA_PER_UPDATE = 50L
    }
}
