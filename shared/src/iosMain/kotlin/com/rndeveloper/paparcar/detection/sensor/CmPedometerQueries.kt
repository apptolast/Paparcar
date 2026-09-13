@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.detection.sensor

import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreMotion.CMPedometer
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * The one CMPedometer date-range read both consumers share: the step-seal budget
 * ([IosDetectionStepAnchors]) and the wake-and-query reconstruction (post-egress steps).
 * Null on unavailability, denied Motion permission or query error — mute, never a verdict.
 * [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001]
 */
internal suspend fun queryPedometerStepsBetween(fromMs: Long, toMs: Long): Long? {
    if (!CMPedometer.isStepCountingAvailable()) return null
    if (toMs <= fromMs) return null
    return suspendCancellableCoroutine { cont ->
        CMPedometer().queryPedometerDataFromDate(
            NSDate.dateWithTimeIntervalSince1970(fromMs / MILLIS_PER_SECOND),
            NSDate.dateWithTimeIntervalSince1970(toMs / MILLIS_PER_SECOND),
        ) { data, error ->
            if (!cont.isActive) return@queryPedometerDataFromDate
            if (error != null || data == null) {
                PaparcarLogger.w(TAG, "pedometer range query failed: ${error?.localizedDescription}")
                cont.resume(null)
            } else {
                cont.resume(data.numberOfSteps.longLongValue)
            }
        }
    }
}

private const val TAG = "CmPedometerQueries"
private const val MILLIS_PER_SECOND = 1_000.0
