package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Returns a single GPS location reading, then cancels the underlying stream.
 *
 * Wraps [LocationDataSource.observeBalancedLocation] with a [TIMEOUT_MS] guard
 * so callers are never suspended indefinitely (e.g. when GPS is unavailable or cold).
 * Returns null on timeout or if no fix is obtained within the window.
 *
 * Used by [DepartureDetectionWorker] to obtain the current speed at geofence-exit
 * time without starting a continuous location stream.
 */
class GetOneLocationUseCase(
    private val locationDataSource: LocationDataSource,
) {
    suspend operator fun invoke(): GpsPoint? {
        val fix = withTimeoutOrNull(TIMEOUT_MS) {
            locationDataSource.observeBalancedLocation().first()
        }
        // Every one-shot fix is a field-forensics data point: WHERE it landed decides what the
        // safety net / departure worker concluded there. Timeouts matter just as much.
        if (fix != null) {
            PaparcarLogger.d(DIAG, "fix lat=${fix.latitude} lon=${fix.longitude} speed=${fix.speed}m/s acc=${fix.accuracy}m")
        } else {
            PaparcarLogger.d(DIAG, "fix TIMEOUT after ${TIMEOUT_MS}ms")
        }
        return fix
    }

    companion object {
        private const val DIAG = "PARKDIAG/OneFix"
        private const val TIMEOUT_MS = 15_000L
    }
}