@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/**
 * Returns a single GPS location reading, then cancels the underlying stream.
 *
 * Wraps [LocationDataSource.observeBalancedLocation] with a [TIMEOUT_MS] guard
 * so callers are never suspended indefinitely (e.g. when GPS is unavailable or cold).
 * Returns null on timeout or if no fix is obtained within the window.
 *
 * **Freshness gate** ([maxAgeMs]). The fused provider's first emission is typically the cached
 * last-known location. For UI that is fine; for a detection DECISION it is poison: in the field
 * (2026-07-07) the cache served the same coordinate with speed=0 for 4 minutes mid-drive and
 * reported "inside the fence" after the car had left — which corrupted the position anchor.
 * With [maxAgeMs] set, stale candidates are skipped and the call waits (within the timeout) for
 * a fix the provider produced NOW; returning null is preferred over returning the past.
 * [DET-RECONCILE-001]
 *
 * Used by [DepartureDetectionWorker] and the parked-state safety net to obtain the current
 * position/speed without starting a continuous location stream.
 */
class GetOneLocationUseCase(
    private val locationDataSource: LocationDataSource,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend operator fun invoke(maxAgeMs: Long? = null): GpsPoint? {
        val fix = withTimeoutOrNull(TIMEOUT_MS) {
            locationDataSource.observeBalancedLocation().firstOrNull { candidate ->
                val ageMs = nowMs() - candidate.timestamp
                val fresh = maxAgeMs == null || ageMs <= maxAgeMs
                if (!fresh) {
                    PaparcarLogger.d(DIAG, "⊘ stale fix rejected (age=${ageMs / 1000}s > ${maxAgeMs!! / 1000}s) lat=${candidate.latitude} lon=${candidate.longitude}")
                }
                fresh
            }
        }
        // Every one-shot fix is a field-forensics data point: WHERE it landed decides what the
        // safety net / departure worker concluded there. Timeouts matter just as much.
        if (fix != null) {
            PaparcarLogger.d(DIAG, "fix lat=${fix.latitude} lon=${fix.longitude} speed=${fix.speed}m/s acc=${fix.accuracy}m age=${(nowMs() - fix.timestamp) / 1000}s")
        } else {
            PaparcarLogger.d(DIAG, "fix TIMEOUT after ${TIMEOUT_MS}ms (maxAge=${maxAgeMs?.div(1000)?.let { "${it}s" } ?: "none"})")
        }
        return fix
    }

    companion object {
        private const val DIAG = "PARKDIAG/OneFix"
        private const val TIMEOUT_MS = 15_000L
    }
}
